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

package com.tencent.polaris.dubbo.registry;

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.config.BootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.consumer.DiscoveryConfigImpl;
import com.tencent.polaris.factory.config.global.ServerConnectorConfigImpl;
import com.tencent.polaris.factory.config.provider.RegisterConfigImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理 nacos connector 配置，将 polaris_nacos_* 参数转换为
 * Polaris SDK 的多 connector 配置，实现双注册/双发现。
 */
public class NacosBootConfigHandler implements BootConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NacosBootConfigHandler.class);

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        // 检查总开关
        String enabledStr = parameters.get(Consts.KEY_POLARIS_NACOS_ENABLED);
        if (!"true".equalsIgnoreCase(enabledStr)) {
            return;
        }

        // 检查必填参数
        String serverAddr = parameters.get(Consts.KEY_POLARIS_NACOS_SERVER_ADDR);
        if (StringUtils.isBlank(serverAddr)) {
            LOGGER.warn("[Nacos] polaris_nacos_enabled is true but polaris_nacos_server_addr is missing, skip nacos connector setup");
            return;
        }

        LOGGER.info("[Nacos] setting up nacos connector with server_addr: {}", serverAddr);

        // 构建 nacos ServerConnectorConfig
        ServerConnectorConfigImpl nacosConnector = buildNacosConnectorConfig(parameters, serverAddr);

        // 从单 connector 切换到多 connector 模式
        switchToMultiConnectorMode(configuration, nacosConnector);

        // 配置 provider.registers
        configureRegister(configuration, parameters);

        // 配置 consumer.discoveries
        configureDiscovery(configuration, parameters);

        LOGGER.info("[Nacos] nacos connector setup completed");
    }

    private ServerConnectorConfigImpl buildNacosConnectorConfig(Map<String, String> parameters, String serverAddr) {
        ServerConnectorConfigImpl nacosConnector = new ServerConnectorConfigImpl();
        nacosConnector.setId(Consts.NACOS_CONNECTOR_ID);
        nacosConnector.setProtocol(Consts.NACOS_CONNECTOR_PROTOCOL);

        // 解析地址（支持逗号分隔多地址）
        List<String> addresses = new ArrayList<>();
        for (String addr : serverAddr.split(",")) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                addresses.add(trimmed);
            }
        }
        nacosConnector.setAddresses(addresses);

        // 填充 metadata
        Map<String, String> metadata = nacosConnector.getMetadata();

        // 认证
        String username = parameters.get(Consts.KEY_POLARIS_NACOS_USERNAME);
        if (StringUtils.isNotBlank(username)) {
            metadata.put(Consts.NACOS_METADATA_KEY_USERNAME, username);
        }
        String password = parameters.get(Consts.KEY_POLARIS_NACOS_PASSWORD);
        if (StringUtils.isNotBlank(password)) {
            metadata.put(Consts.NACOS_METADATA_KEY_PASSWORD, password);
        }

        // 命名空间（默认 public）
        String namespace = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_NAMESPACE, Consts.NACOS_DEFAULT_NAMESPACE);
        metadata.put(Consts.NACOS_METADATA_KEY_NAMESPACE, namespace);

        // 分组（默认 DEFAULT_GROUP）
        String group = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_GROUP, Consts.NACOS_DEFAULT_GROUP);
        metadata.put(Consts.NACOS_METADATA_KEY_GROUP, group);

        // 集群（默认 DEFAULT）
        String cluster = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_CLUSTER, Consts.NACOS_DEFAULT_CLUSTER);
        metadata.put(Consts.NACOS_METADATA_KEY_CLUSTER, cluster);

        // 临时实例（默认 true）
        String ephemeral = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_EPHEMERAL, "true");
        metadata.put(Consts.NACOS_METADATA_KEY_EPHEMERAL, ephemeral);

        // 权重（默认 1）
        String weight = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_WEIGHT, "1");
        metadata.put(Consts.NACOS_METADATA_KEY_WEIGHT, weight);

        // 上下文路径
        String contextPath = parameters.get(Consts.KEY_POLARIS_NACOS_CONTEXT_PATH);
        if (StringUtils.isNotBlank(contextPath)) {
            metadata.put(Consts.NACOS_METADATA_KEY_CONTEXT_PATH, contextPath);
        }

        // Dubbo 适配（默认 true）：开启后 Nacos 服务名使用 category:interface:version:group 格式
        String dubboAdapt = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_DUBBO_ADAPT, "true");
        metadata.put(Consts.NACOS_METADATA_KEY_DUBBO_ADAPT, dubboAdapt);

        return nacosConnector;
    }

    private void switchToMultiConnectorMode(ConfigurationImpl configuration, ServerConnectorConfigImpl nacosConnector) {
        // 取出当前的 Polaris GRPC connector
        ServerConnectorConfigImpl polarisConnector = configuration.getGlobal().getServerConnector();

        // 组装多 connector 列表
        List<ServerConnectorConfigImpl> connectors = new ArrayList<>();
        if (polarisConnector != null) {
            connectors.add(polarisConnector);
        }
        connectors.add(nacosConnector);

        // 切换到多 connector 模式
        configuration.getGlobal().setServerConnectors(connectors);
        configuration.getGlobal().setServerConnector(null);
    }

    private void configureRegister(ConfigurationImpl configuration, Map<String, String> parameters) {
        String registerEnabledStr = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_REGISTER_ENABLED, "true");
        boolean registerEnabled = Boolean.parseBoolean(registerEnabledStr);

        RegisterConfigImpl nacosRegister = new RegisterConfigImpl();
        nacosRegister.setServerConnectorId(Consts.NACOS_CONNECTOR_ID);
        nacosRegister.setEnable(registerEnabled);

        configuration.getProvider().getRegisters().add(nacosRegister);
    }

    private void configureDiscovery(ConfigurationImpl configuration, Map<String, String> parameters) {
        String discoveryEnabledStr = parameters.getOrDefault(Consts.KEY_POLARIS_NACOS_DISCOVERY_ENABLED, "true");
        boolean discoveryEnabled = Boolean.parseBoolean(discoveryEnabledStr);

        DiscoveryConfigImpl nacosDiscovery = new DiscoveryConfigImpl();
        nacosDiscovery.setServerConnectorId(Consts.NACOS_CONNECTOR_ID);
        nacosDiscovery.setEnable(discoveryEnabled);

        configuration.getConsumer().getDiscoveries().add(nacosDiscovery);
    }
}
