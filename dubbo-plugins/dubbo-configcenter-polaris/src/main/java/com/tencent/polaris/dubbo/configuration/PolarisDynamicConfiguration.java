/*
 * Tencent is pleased to support the open source community by making dubbo-polaris-java available.
 *
 * Copyright (C) 2021 Tencent. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.polaris.dubbo.configuration;

import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.exception.ServerCodes;
import com.tencent.polaris.api.plugin.configuration.ConfigFileResponse;
import com.tencent.polaris.api.plugin.event.ConfigEvent;
import com.tencent.polaris.api.plugin.event.EventConstants;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.flow.BaseFlow;
import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.configuration.api.core.ChangeType;
import com.tencent.polaris.configuration.api.core.ConfigFile;
import com.tencent.polaris.configuration.api.core.ConfigFileChangeEvent;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.configuration.api.rpc.ConfigPublishRequest;
import java.time.LocalDateTime;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class PolarisDynamicConfiguration implements DynamicConfiguration {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PolarisOperator operator;

    private final PolarisConfig polarisConfig;

    private final ConfigFileService fileQuerier;

    private final ConfigFilePublishService filePublisher;

    private final Map<String, Set<ConfigurationListener>> listeners = new ConcurrentHashMap<>();

    PolarisDynamicConfiguration(URL url) {
        this.operator = PolarisOperators.loadOrStoreForConfig(url.getHost(), url.getPort(), url.getParameters());
        this.polarisConfig = operator.getPolarisConfig();
        this.fileQuerier = operator.getConfigFileAPI();
        this.filePublisher = operator.getConfigFilePublishAPI();
    }

    @Override
    public void addListener(String key, String group, ConfigurationListener listener) {
        String fileKey = group + "@@" + key;
        listeners.computeIfAbsent(fileKey, s -> {
            ConfigFile configFile = fileQuerier.getConfigFile(polarisConfig.getNamespace(), group, key);
            configFile.addChangeListener(event -> {
                Set<ConfigurationListener> watchers = listeners.getOrDefault(fileKey, Collections.emptySet());
                watchers.forEach(configurationListener -> {
                    ConfigChangedEvent dubboEvent = new ConfigChangedEvent(
                            key, group, event.getNewValue(), getChangeType(event.getChangeType()));
                    configurationListener.process(dubboEvent);
                });
                reportEvent(key, group, event);
            });
            logger.info(String.format("add polaris config listener, key=%s, group=%s", key, group));
            return new CopyOnWriteArraySet<>();
        });

        listeners.get(fileKey).add(listener);
    }

    @Override
    public void removeListener(String key, String group, ConfigurationListener listener) {
        String fileKey = group + "@@" + key;
        listeners.getOrDefault(fileKey, Collections.emptySet()).remove(listener);
    }

    @Override
    public String getConfig(String key, String group, long timeout) throws IllegalStateException {
        try {
            ConfigFile configFile = fileQuerier.getConfigFile(polarisConfig.getNamespace(), group, key);
            return configFile.getContent();
        } catch (PolarisException e) {
            logger.error(formatCode(
                            e.getCode()) +
                            e.getMessage() +
                            String.format("key=%s, group=%s", key, group) +
                            "get config from polaris fail",
                    e);
        }
        return null;
    }

    @Override
    public Object getInternalProperty(String key) {
        try {
            ConfigFile configFile = fileQuerier.getConfigFile(polarisConfig.getNamespace(), DEFAULT_GROUP, key);
            return configFile.getContent();
        } catch (PolarisException e) {
            logger.error(formatCode(
                            e.getCode()) +
                            e.getMessage() +
                            String.format("key=%s, group=%s", key, DEFAULT_GROUP) +
                            "get config from polaris fail",
                    e);
        }
        return null;
    }

    @Override
    public boolean publishConfig(String key, String group, String content) throws UnsupportedOperationException {
        try {
            ConfigPublishRequest request = new ConfigPublishRequest();
            request.setNamespace(polarisConfig.getNamespace());
            request.setGroup(group);
            request.setFilename(key);
            request.setContent(content);
            ConfigFileResponse response = filePublisher.upsertAndPublish(request);
            if (response.getCode() != ServerCodes.EXECUTE_SUCCESS) {
                logger.error(
                        formatCode(response.getCode()) +
                                response.getMessage() +
                                String.format("key(%s) group(%s)", key, group) +
                                "release config fail"
                );
                return false;
            }
            return true;
        } catch (PolarisException e) {
            logger.error(
                    formatCode(e.getCode()) +
                            e.getMessage() +
                            String.format("key(%s) group(%s)", key, group) +
                            "publish config fail",
                    e);
            return false;
        }
    }

    private String formatCode(Object val) {
        return "POLARIS:" + val;
    }

    private ConfigChangeType getChangeType(ChangeType polarisChangeType) {
        ConfigChangeType dubboChangeType;
        switch (polarisChangeType) {
            case ADDED:
                dubboChangeType = ConfigChangeType.ADDED;
                break;
            case DELETED:
                dubboChangeType = ConfigChangeType.DELETED;
                break;
            default:
                dubboChangeType = ConfigChangeType.MODIFIED;
                break;
        }
        return dubboChangeType;
    }

    private void reportEvent(String key, String group, ConfigFileChangeEvent event){
        SDKContext context = operator.getSdkContext();
        ConfigEvent.Builder builder = new ConfigEvent.Builder()
                .withTimestamp(LocalDateTime.now())
                .withEventType(EventConstants.EventType.CONFIG)
                .withEventName(EventConstants.EventName.ConfigUpdated)
                .withClientId(context.getExtensions().getValueContext().getClientId())
                .withClientIp(context.getExtensions().getValueContext().getHost())
                .withNamespace(polarisConfig.getNamespace())
                .withConfigGroup(group)
                .withConfigFileName(key);
        BaseFlow.reportConfigEvent(context.getExtensions(), builder.build());
    }
}
