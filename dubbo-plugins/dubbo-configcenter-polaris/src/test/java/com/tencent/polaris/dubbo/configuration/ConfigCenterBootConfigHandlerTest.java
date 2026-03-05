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

import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConfigCenterBootConfigHandler 单元测试类
 *
 * @author Yuwei Fu
 */
public class ConfigCenterBootConfigHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigCenterBootConfigHandlerTest.class);

    private ConfigCenterBootConfigHandler handler;
    private ConfigurationImpl configuration;

    @Before
    public void before() {
        handler = new ConfigCenterBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
    }

    /**
     * 创建默认的 PolarisConfig 用于测试（CONFIG 类型，端口 8093 为配置中心端口）
     */
    private PolarisConfig createDefaultPolarisConfig(Map<String, String> parameters) {
        return new PolarisConfig(PolarisOperators.OperatorType.CONFIG, "127.0.0.1", 8093, parameters);
    }

    // ==================== 配置中心连接地址测试 ====================

    /**
     * 测试：配置中心连接地址正确设置
     */
    @Test
    public void testHandle_configAddressesSet() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证配置中心连接地址
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertFalse("配置中心地址不应为空", configAddresses.isEmpty());
        Assert.assertTrue("配置中心地址应包含 127.0.0.1:8093", configAddresses.contains("127.0.0.1:8093"));
        LOG.info("[Test] 配置中心连接地址设置测试通过");
    }

    /**
     * 测试：使用不同 host 和 port 的配置中心地址
     */
    @Test
    public void testHandle_configAddressesWithDifferentHostPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = new PolarisConfig(PolarisOperators.OperatorType.CONFIG, "192.168.1.100", 9093,
                parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertTrue("配置中心地址应包含 192.168.1.100:9093", configAddresses.contains("192.168.1.100:9093"));
    }

    // ==================== 推空保护测试 ====================

    /**
     * 测试：配置推空保护被禁用
     */
    @Test
    public void testHandle_emptyProtectionDisabled() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 配置推空保护应被禁用
        Assert.assertFalse(configuration.getConfigFile().getServerConnector().isEmptyProtectionEnable());
        LOG.info("[Test] 配置推空保护禁用测试通过");
    }

    // ==================== 配置加密开关测试 ====================

    /**
     * 测试：配置加密过滤默认启用（未传参数时默认为 true）
     */
    @Test
    public void testHandle_configEncryptEnabledByDefault() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 配置加密默认启用
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().isEnable());
        LOG.info("[Test] 配置加密默认启用测试通过");
    }

    /**
     * 测试：显式设置配置加密启用
     */
    @Test
    public void testHandle_configEncryptExplicitlyEnabled() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "true");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().isEnable());
    }

    /**
     * 测试：显式禁用配置加密
     */
    @Test
    public void testHandle_configEncryptDisabled() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 配置加密应被禁用
        Assert.assertFalse(configuration.getConfigFile().getConfigFilterConfig().isEnable());
        LOG.info("[Test] 配置加密禁用测试通过");
    }

    /**
     * 测试：配置加密参数为非法值时解析为 false
     */
    @Test
    public void testHandle_configEncryptInvalidValue() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "invalid_value");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - Boolean.parseBoolean 对非 "true" 值返回 false
        Assert.assertFalse(configuration.getConfigFile().getConfigFilterConfig().isEnable());
    }

    // ==================== 过滤链配置测试 ====================

    /**
     * 测试：过滤链包含 crypto
     */
    @Test
    public void testHandle_filterChainContainsCrypto() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 过滤链应包含 crypto
        List<String> chain = configuration.getConfigFile().getConfigFilterConfig().getChain();
        Assert.assertNotNull(chain);
        Assert.assertTrue("过滤链应包含 crypto", chain.contains("crypto"));
        LOG.info("[Test] 过滤链包含 crypto 测试通过");
    }

    // ==================== crypto 插件配置测试 ====================

    /**
     * 测试：crypto 插件配置正确
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandle_cryptoPluginConfig() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - crypto 插件配置应包含 type=AES
        Map<String, Object> pluginConfig = (Map<String, Object>) configuration.getConfigFile()
                .getConfigFilterConfig().getPlugin().get("crypto");
        Assert.assertNotNull("crypto 插件配置不应为 null", pluginConfig);
        Assert.assertEquals("AES", pluginConfig.get("type"));
        LOG.info("[Test] crypto 插件配置测试通过");
    }

    /**
     * 测试：crypto 插件仅包含 type 字段
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandle_cryptoPluginOnlyContainsType() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Map<String, Object> pluginConfig = (Map<String, Object>) configuration.getConfigFile()
                .getConfigFilterConfig().getPlugin().get("crypto");
        Assert.assertNotNull(pluginConfig);
        Assert.assertEquals("crypto 插件应只有 type 一个字段", 1, pluginConfig.size());
    }

    // ==================== 综合场景测试 ====================

    /**
     * 测试：所有配置项同时正确设置
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandle_allConfigurationsSetCorrectly() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "true");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证所有配置项
        // 1. 配置中心地址
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertTrue(configAddresses.contains("127.0.0.1:8093"));

        // 2. 推空保护禁用
        Assert.assertFalse(configuration.getConfigFile().getServerConnector().isEmptyProtectionEnable());

        // 3. 加密过滤启用
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().isEnable());

        // 4. 过滤链包含 crypto
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().getChain().contains("crypto"));

        // 5. crypto 插件配置
        Map<String, Object> pluginConfig = (Map<String, Object>) configuration.getConfigFile()
                .getConfigFilterConfig().getPlugin().get("crypto");
        Assert.assertNotNull(pluginConfig);
        Assert.assertEquals("AES", pluginConfig.get("type"));

        LOG.info("[Test] 综合配置测试通过");
    }

    /**
     * 测试：禁用加密时其他配置仍正常设置
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandle_encryptDisabled_otherConfigsStillSet() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        // 加密禁用
        Assert.assertFalse(configuration.getConfigFile().getConfigFilterConfig().isEnable());

        // 但地址仍然设置
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertTrue(configAddresses.contains("127.0.0.1:8093"));

        // 推空保护仍然禁用
        Assert.assertFalse(configuration.getConfigFile().getServerConnector().isEmptyProtectionEnable());

        // 过滤链仍然包含 crypto
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().getChain().contains("crypto"));

        // 插件配置仍然设置
        Map<String, Object> pluginConfig = (Map<String, Object>) configuration.getConfigFile()
                .getConfigFilterConfig().getPlugin().get("crypto");
        Assert.assertNotNull(pluginConfig);
        Assert.assertEquals("AES", pluginConfig.get("type"));
    }

    /**
     * 测试：空参数 map 时使用默认值
     */
    @Test
    public void testHandle_withEmptyParameters() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 空参数时加密默认启用（getOrDefault 返回 "true"）
        Assert.assertTrue(configuration.getConfigFile().getConfigFilterConfig().isEnable());
        Assert.assertFalse(configuration.getConfigFile().getServerConnector().isEmptyProtectionEnable());
    }

    /**
     * 测试：多次调用 handle 不会导致 crypto 重复添加到过滤链
     * 注意：当前实现每次调用都会 add "crypto"，此测试验证这一行为
     */
    @Test
    public void testHandle_multipleInvocations_cryptoAddedMultipleTimes() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act - 调用两次
        handler.handle(polarisConfig, parameters, configuration);
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 每次调用都会添加一个 crypto，验证当前行为
        List<String> chain = configuration.getConfigFile().getConfigFilterConfig().getChain();
        long cryptoCount = chain.stream().filter("crypto"::equals).count();
        Assert.assertTrue("多次调用会多次添加 crypto 到过滤链", cryptoCount >= 2);
    }

    /**
     * 测试：使用 GOVERNANCE 类型的 PolarisConfig（验证配置地址推导）
     */
    @Test
    public void testHandle_withGovernanceOperatorType() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091,
                parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - GOVERNANCE 类型时配置地址默认端口为 8093
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertTrue("GOVERNANCE 类型时配置中心地址应包含默认端口 8093",
                configAddresses.contains("127.0.0.1:8093"));
    }
}
