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

package com.tencent.polaris.common.config;

import com.tencent.polaris.api.config.consumer.OutlierDetectionConfig;
import com.tencent.polaris.api.config.global.StatReporterConfig;
import com.tencent.polaris.api.config.plugin.DefaultPlugins;
import com.tencent.polaris.api.utils.CollectionUtils;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.global.AdminConfigImpl;
import com.tencent.polaris.factory.config.global.ServerConnectorConfigImpl;
import com.tencent.polaris.plugins.event.pushgateway.PushGatewayEventReporterConfig;
import com.tencent.polaris.plugins.stat.prometheus.handler.PrometheusHandlerConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.dubbo.common.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseBootConfigHandler implements BootConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseBootConfigHandler.class);

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        int timeout = 0;
        String timeoutStr = parameters.get(Consts.KEY_TIMEOUT);
        if (null != timeoutStr && timeoutStr.length() > 0) {
            try {
                timeout = Integer.parseInt(timeoutStr);
            } catch (Exception e) {
                LOGGER.info("[Common] fail to convert ttlStr {}", timeoutStr, e);
            }
        }
        timeoutStr = parameters.get(Consts.KEY_POLARIS_TIMEOUT);
        if (null != timeoutStr && timeoutStr.length() > 0) {
            try {
                timeout = Integer.parseInt(timeoutStr);
            } catch (Exception e) {
                LOGGER.info("[Common] fail to convert ttlStr {}", timeoutStr, e);
            }
        }

        if (timeout > 0) {
            configuration.getGlobal().getAPI().setTimeout(timeout);
        }
        Boolean persistEnable = null;
        String persistEnableStr = parameters.get(Consts.KEY_PERSIST_ENABLE);
        if (null != persistEnableStr && persistEnableStr.length() > 0) {
            persistEnable = Boolean.parseBoolean(persistEnableStr);
        }
        persistEnableStr = parameters.get(Consts.KEY_POLARIS_PERSIST_ENABLE);
        if (null != persistEnableStr && persistEnableStr.length() > 0) {
            persistEnable = Boolean.parseBoolean(persistEnableStr);
        }
        if (null != persistEnable) {
            configuration.getConsumer().getLocalCache().setPersistEnable(persistEnable);
        }
        initServerConnectorConfig(polarisConfig, configuration);
        initSDKContextConfig(configuration, parameters, polarisConfig);
        initPrometheusHandlerConfig(configuration, parameters, polarisConfig);
        initPushGatewayEventReporterConfig(configuration, parameters, polarisConfig);
    }

    private void initServerConnectorConfig(PolarisConfig polarisConfig, ConfigurationImpl configuration) {
        if (StringUtils.isNotBlank(polarisConfig.getToken())) {
            // 设置服务治理 ServerConnector 的配置
            ServerConnectorConfigImpl connector = configuration.getGlobal().getServerConnector();
            if (Objects.nonNull(connector)) {
                connector.setToken(polarisConfig.getToken());
            }
            List<ServerConnectorConfigImpl> connectors = configuration.getGlobal().getServerConnectors();
            if (CollectionUtils.isNotEmpty(connectors)) {
                connectors.forEach(connectorConfig -> connectorConfig.setToken(polarisConfig.getToken()));
            }

            // 设置配置中心 ServerConnector 的配置
            ServerConnectorConfigImpl configConnector = configuration.getConfigFile().getServerConnector();
            if (Objects.nonNull(connector)) {
                configConnector.setToken(polarisConfig.getToken());
            }
        }
        // 设置服务治理连接地址
        configuration.getGlobal().getServerConnector()
                .setAddresses(polarisConfig.getDiscoverAddresses());


    }


    private void initSDKContextConfig(ConfigurationImpl configuration, Map<String, String> parameters,
            PolarisConfig polarisConfig) {

        // 设置主动探测
        // polaris_detect_when 优先级高于 detect_when
        if (parameters.containsKey(Consts.KEY_DETECT_WHEN) || parameters.containsKey(Consts.KEY_POLARIS_DETECT_WHEN)) {
            String detectWhen = parameters.get(Consts.KEY_DETECT_WHEN);
            if (StringUtils.isNotBlank(parameters.get(Consts.KEY_POLARIS_DETECT_WHEN))) {
                detectWhen = parameters.get(Consts.KEY_POLARIS_DETECT_WHEN);
            }
            try {
                configuration.getConsumer().getOutlierDetection()
                        .setWhen(OutlierDetectionConfig.When.valueOf(detectWhen));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid detectWhen value: {}, valid values are: {}",
                        detectWhen, Arrays.toString(OutlierDetectionConfig.When.values()));
            }
        }

        // 禁用服务推空保护
        configuration.getConsumer().getServiceRouter().getPlugin()
                .put("recoverRouter", Collections.singletonMap("excludeCircuitBreakInstances", "false"));
        configuration.getConsumer().getServiceRouter().getPlugin()
                .put("recoverRouter", Collections.singletonMap("allRecoverEnable", "false"));
        configuration.getGlobal().getAPI().setBindIP(NetUtils.getLocalHost());
    }

    private void initPushGatewayEventReporterConfig(ConfigurationImpl configuration, Map<String, String> parameters,
            PolarisConfig polarisConfig) {
        // event reporter
        PushGatewayEventReporterConfig pushGatewayEventReporterConfig = configuration.getGlobal().getEventReporter()
                .getPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE, PushGatewayEventReporterConfig.class);
        configuration.getGlobal().getEventReporter().getReporters()
                .add(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE);
        if (parameters.containsKey(Consts.KEY_PGW_EVENT_ENABLED)) {
            boolean enabled = Boolean.parseBoolean(parameters.get(Consts.KEY_PGW_EVENT_ENABLED));
            pushGatewayEventReporterConfig.setEnable(enabled);
            if (parameters.containsKey(Consts.KEY_PGW_EVENT_ADDR)) {
                List<String> addresses = Arrays.asList(
                        parameters.get(Consts.KEY_PGW_EVENT_ADDR).split(Consts.ADDRESSES_SEPARATOR));
                pushGatewayEventReporterConfig.setAddress(addresses);
            }
            pushGatewayEventReporterConfig.setEventQueueSize(1000);
            pushGatewayEventReporterConfig.setMaxBatchSize(100);
            pushGatewayEventReporterConfig.setNamespace("polaris");
            pushGatewayEventReporterConfig.setService("polaris.pushgateway");
        }

        configuration.getGlobal().getEventReporter()
                .setPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE, pushGatewayEventReporterConfig);
    }

    private void initPrometheusHandlerConfig(ConfigurationImpl configuration, Map<String, String> parameters,
            PolarisConfig polarisConfig) {

        PrometheusHandlerConfig prometheusHandlerConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);

        AdminConfigImpl adminConfig = configuration.getGlobal().getAdmin();
        // stat reporter
        // polaris_stat_type 优先级高于 stat_type
        String statType = parameters.get(Consts.KEY_METRIC_TYPE);
        if (StringUtils.isNotBlank(parameters.get(Consts.KEY_POLARIS_METRIC_TYPE))) {
            statType = parameters.get(Consts.KEY_POLARIS_METRIC_TYPE);
        }
        if (StringUtils.isNotBlank(statType)) {
            switch (statType) {
                case "push":
                    // polaris_stat_push_addr 优先级高于 stat_push_addr
                    String pushAddr = parameters.get(Consts.KEY_METRIC_PUSH_ADDR);
                    if (StringUtils.isNotBlank(parameters.get(Consts.KEY_POLARIS_METRIC_PUSH_ADDR))) {
                        pushAddr = parameters.get(Consts.KEY_POLARIS_METRIC_PUSH_ADDR);
                    }
                    if (StringUtils.isBlank(pushAddr)) {
                        pushAddr = polarisConfig.getDiscoverAddresses().stream()
                                .map(addr -> addr.split(":")[0] + ":9091")
                                .collect(Collectors.joining(Consts.ADDRESSES_SEPARATOR));
                    }
                    List<String> addresses = Arrays.asList(pushAddr.split(Consts.ADDRESSES_SEPARATOR));
                    configuration.getGlobal().getStatReporter().setEnable(true);
                    prometheusHandlerConfig.setType("push");
                    prometheusHandlerConfig.setAddress(addresses);

                    // 默认为 10s
                    // polaris_stat_push_interval 优先级高于 stat_push_interval
                    long interval = 10 * 1000L;
                    if (parameters.containsKey(Consts.KEY_METRIC_PUSH_INTERVAL)) {
                        try {
                            interval = Integer.parseInt(parameters.get(Consts.KEY_METRIC_PUSH_INTERVAL));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    if (parameters.containsKey(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL)) {
                        try {
                            interval = Integer.parseInt(parameters.get(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    prometheusHandlerConfig.setPushInterval(interval);
                    break;
                case "pull":
                    // polaris_stat_pull_port 优先级高于 stat_pull_port
                    int port = 9091;
                    if (parameters.containsKey(Consts.KEY_METRIC_PULL_PORT)) {
                        try {
                            port = Integer.parseInt(parameters.get(Consts.KEY_METRIC_PULL_PORT));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    if (parameters.containsKey(Consts.KEY_POLARIS_METRIC_PULL_PORT)) {
                        try {
                            port = Integer.parseInt(parameters.get(Consts.KEY_POLARIS_METRIC_PULL_PORT));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    configuration.getGlobal().getStatReporter().setEnable(true);
                    prometheusHandlerConfig.setType("pull");
                    adminConfig.setPort(port);
                    break;
            }
        } else {
            configuration.getGlobal().getStatReporter().setEnable(false);
        }
        configuration.getGlobal().getStatReporter().setPluginConfig("prometheus", prometheusHandlerConfig);

    }
}
