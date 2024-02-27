/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
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
import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.configuration.api.core.ConfigFile;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class PolarisDynamicConfiguration implements DynamicConfiguration {

    private final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final PolarisOperator operator;

    private final PolarisConfig polarisConfig;

    private final ConfigFileService fileQuerier;

    private final ConfigFilePublishService filePublisher;

    private Map<String, Set<ConfigurationListener>> listeners = new ConcurrentHashMap<>();

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
                    ConfigChangedEvent dubboEvent = new ConfigChangedEvent(key, group, event.getNewValue());
                    configurationListener.process(dubboEvent);
                });
            });
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
        ConfigFile configFile = fileQuerier.getConfigFile(polarisConfig.getNamespace(), group, key);
        return configFile.getContent();
    }

    @Override
    public Object getInternalProperty(String key) {
        ConfigFile configFile = fileQuerier.getConfigFile(polarisConfig.getNamespace(), DEFAULT_GROUP, key);
        return configFile.getContent();
    }

    @Override
    public boolean publishConfig(String key, String group, String content) throws UnsupportedOperationException {
        try {
            ConfigFileResponse response = filePublisher.createConfigFile(polarisConfig.getNamespace(), group, key, content);
            if (response.getCode() == ServerCodes.EXISTED_RESOURCE) {
                response = filePublisher.updateConfigFile(polarisConfig.getNamespace(), group, key, content);
            }
            if (response.getCode() != ServerCodes.EXECUTE_SUCCESS) {
                logger.error(
                        formatCode(response.getCode()),
                        response.getMessage(),
                        String.format("key(%s) group(%s)", key, group),
                        "upsert config fail"
                );
                return false;
            }
            response = filePublisher.releaseConfigFile(polarisConfig.getNamespace(), group, key);
            if (response.getCode() != ServerCodes.EXECUTE_SUCCESS) {
                logger.error(
                        formatCode(response.getCode()),
                         response.getMessage(),
                        String.format("key(%s) group(%s)", key, group),
                        "release config fail"
                        );
                return false;
            }
            return true;
        } catch (PolarisException e) {
            logger.error(
                    formatCode(e.getCode()),
                    e.getMessage(),
                    String.format("key(%s) group(%s)", key, group),
                    "publish config fail");
            return false;
        }
    }

    private String formatCode(Object val) {
        return "POLARIS:" + val;
    }
}
