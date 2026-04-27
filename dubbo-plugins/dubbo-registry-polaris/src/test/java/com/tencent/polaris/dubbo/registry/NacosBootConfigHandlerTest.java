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

import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.consumer.DiscoveryConfigImpl;
import com.tencent.polaris.factory.config.global.ServerConnectorConfigImpl;
import com.tencent.polaris.factory.config.provider.RegisterConfigImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * NacosBootConfigHandler 单元测试
 */
public class NacosBootConfigHandlerTest {

    private NacosBootConfigHandler handler;
    private ConfigurationImpl configuration;

    @Before
    public void before() {
        handler = new NacosBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
        configuration.setDefault();
    }

    private PolarisConfig createDefaultPolarisConfig(Map<String, String> parameters) {
        return new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, parameters);
    }

    private ServerConnectorConfigImpl findNacosConnector(ConfigurationImpl config) {
        List<ServerConnectorConfigImpl> connectors = config.getGlobal().getServerConnectors();
        if (connectors == null) {
            return null;
        }
        for (ServerConnectorConfigImpl c : connectors) {
            if (Consts.NACOS_CONNECTOR_ID.equals(c.getId())) {
                return c;
            }
        }
        return null;
    }

    private RegisterConfigImpl findNacosRegister(ConfigurationImpl config) {
        List<RegisterConfigImpl> registers = config.getProvider().getRegisters();
        if (registers == null) {
            return null;
        }
        for (RegisterConfigImpl r : registers) {
            if (Consts.NACOS_CONNECTOR_ID.equals(r.getServerConnectorId())) {
                return r;
            }
        }
        return null;
    }

    private DiscoveryConfigImpl findNacosDiscovery(ConfigurationImpl config) {
        List<DiscoveryConfigImpl> discoveries = config.getConsumer().getDiscoveries();
        if (discoveries == null) {
            return null;
        }
        for (DiscoveryConfigImpl d : discoveries) {
            if (Consts.NACOS_CONNECTOR_ID.equals(d.getServerConnectorId())) {
                return d;
            }
        }
        return null;
    }

    // ==================== 禁用场景 ====================

    @Test
    public void testHandle_nacosNotEnabled_noChange() {
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);
        ServerConnectorConfigImpl originalConnector = configuration.getGlobal().getServerConnector();

        handler.handle(polarisConfig, parameters, configuration);

        Assert.assertNotNull(configuration.getGlobal().getServerConnector());
        Assert.assertSame(originalConnector, configuration.getGlobal().getServerConnector());
    }

    @Test
    public void testHandle_nacosExplicitlyDisabled_noChange() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        Assert.assertNotNull(configuration.getGlobal().getServerConnector());
    }

    // ==================== 启用场景 ====================

    @Test
    public void testHandle_nacosEnabled_switchesToMultiConnectorMode() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        List<ServerConnectorConfigImpl> connectors = configuration.getGlobal().getServerConnectors();
        Assert.assertNotNull(connectors);
        Assert.assertEquals(2, connectors.size());
        Assert.assertNull(configuration.getGlobal().getServerConnector());
    }

    @Test
    public void testHandle_nacosEnabled_correctNacosConnectorConfig() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        parameters.put(Consts.KEY_POLARIS_NACOS_USERNAME, "nacos");
        parameters.put(Consts.KEY_POLARIS_NACOS_PASSWORD, "nacos123");
        parameters.put(Consts.KEY_POLARIS_NACOS_NAMESPACE, "test-ns");
        parameters.put(Consts.KEY_POLARIS_NACOS_GROUP, "TEST_GROUP");
        parameters.put(Consts.KEY_POLARIS_NACOS_CLUSTER, "cluster-a");
        parameters.put(Consts.KEY_POLARIS_NACOS_EPHEMERAL, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_WEIGHT, "2");
        parameters.put(Consts.KEY_POLARIS_NACOS_CONTEXT_PATH, "/nacos");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        ServerConnectorConfigImpl nacosConnector = findNacosConnector(configuration);
        Assert.assertNotNull("应存在 nacos connector", nacosConnector);
        Assert.assertEquals(Consts.NACOS_CONNECTOR_PROTOCOL, nacosConnector.getProtocol());
        Assert.assertEquals(1, nacosConnector.getAddresses().size());
        Assert.assertTrue(nacosConnector.getAddresses().contains("127.0.0.1:8848"));

        Map<String, String> metadata = nacosConnector.getMetadata();
        Assert.assertEquals("nacos", metadata.get(Consts.NACOS_METADATA_KEY_USERNAME));
        Assert.assertEquals("nacos123", metadata.get(Consts.NACOS_METADATA_KEY_PASSWORD));
        Assert.assertEquals("test-ns", metadata.get(Consts.NACOS_METADATA_KEY_NAMESPACE));
        Assert.assertEquals("TEST_GROUP", metadata.get(Consts.NACOS_METADATA_KEY_GROUP));
        Assert.assertEquals("cluster-a", metadata.get(Consts.NACOS_METADATA_KEY_CLUSTER));
        Assert.assertEquals("true", metadata.get(Consts.NACOS_METADATA_KEY_EPHEMERAL));
        Assert.assertEquals("2", metadata.get(Consts.NACOS_METADATA_KEY_WEIGHT));
        Assert.assertEquals("/nacos", metadata.get(Consts.NACOS_METADATA_KEY_CONTEXT_PATH));
    }

    @Test
    public void testHandle_nacosEnabled_polarisConnectorPreserved() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        List<ServerConnectorConfigImpl> connectors = configuration.getGlobal().getServerConnectors();
        boolean foundPolaris = false;
        for (ServerConnectorConfigImpl c : connectors) {
            if ("grpc".equals(c.getProtocol())) {
                foundPolaris = true;
                break;
            }
        }
        Assert.assertTrue("应保留 Polaris GRPC connector", foundPolaris);
    }

    @Test
    public void testHandle_nacosEnabled_defaultValues() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        ServerConnectorConfigImpl nacosConnector = findNacosConnector(configuration);
        Assert.assertNotNull(nacosConnector);
        Map<String, String> metadata = nacosConnector.getMetadata();
        Assert.assertEquals(Consts.NACOS_DEFAULT_NAMESPACE, metadata.get(Consts.NACOS_METADATA_KEY_NAMESPACE));
        Assert.assertEquals(Consts.NACOS_DEFAULT_GROUP, metadata.get(Consts.NACOS_METADATA_KEY_GROUP));
        Assert.assertEquals(Consts.NACOS_DEFAULT_CLUSTER, metadata.get(Consts.NACOS_METADATA_KEY_CLUSTER));
        Assert.assertEquals("true", metadata.get(Consts.NACOS_METADATA_KEY_EPHEMERAL));
        Assert.assertEquals("1", metadata.get(Consts.NACOS_METADATA_KEY_WEIGHT));
    }

    // ==================== 注册/发现开关 ====================

    @Test
    public void testHandle_nacosEnabled_registerAndDiscoveryEnabledByDefault() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        RegisterConfigImpl nacosRegister = findNacosRegister(configuration);
        Assert.assertNotNull("应包含 nacos register 配置", nacosRegister);
        Assert.assertTrue("nacos 注册应默认启用", nacosRegister.isEnable());

        DiscoveryConfigImpl nacosDiscovery = findNacosDiscovery(configuration);
        Assert.assertNotNull("应包含 nacos discovery 配置", nacosDiscovery);
        Assert.assertTrue("nacos 发现应默认启用", nacosDiscovery.isEnable());
    }

    @Test
    public void testHandle_nacosRegisterDisabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        parameters.put(Consts.KEY_POLARIS_NACOS_REGISTER_ENABLED, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        RegisterConfigImpl nacosRegister = findNacosRegister(configuration);
        Assert.assertNotNull(nacosRegister);
        Assert.assertFalse("nacos 注册应被禁用", nacosRegister.isEnable());
    }

    @Test
    public void testHandle_nacosDiscoveryDisabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "127.0.0.1:8848");
        parameters.put(Consts.KEY_POLARIS_NACOS_DISCOVERY_ENABLED, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        DiscoveryConfigImpl nacosDiscovery = findNacosDiscovery(configuration);
        Assert.assertNotNull(nacosDiscovery);
        Assert.assertFalse("nacos 发现应被禁用", nacosDiscovery.isEnable());
    }

    // ==================== 多地址 ====================

    @Test
    public void testHandle_nacosEnabled_multipleAddresses() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        parameters.put(Consts.KEY_POLARIS_NACOS_SERVER_ADDR, "10.0.0.1:8848,10.0.0.2:8848,10.0.0.3:8848");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        ServerConnectorConfigImpl nacosConnector = findNacosConnector(configuration);
        Assert.assertNotNull(nacosConnector);
        Assert.assertEquals(3, nacosConnector.getAddresses().size());
        Assert.assertTrue(nacosConnector.getAddresses().contains("10.0.0.1:8848"));
        Assert.assertTrue(nacosConnector.getAddresses().contains("10.0.0.2:8848"));
        Assert.assertTrue(nacosConnector.getAddresses().contains("10.0.0.3:8848"));
    }

    // ==================== 错误处理 ====================

    @Test
    public void testHandle_nacosEnabled_missingServerAddr_noChange() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NACOS_ENABLED, "true");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        handler.handle(polarisConfig, parameters, configuration);

        Assert.assertNotNull(configuration.getGlobal().getServerConnector());
    }
}
