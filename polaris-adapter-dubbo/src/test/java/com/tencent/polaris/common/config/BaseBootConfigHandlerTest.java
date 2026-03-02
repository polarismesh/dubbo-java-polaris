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
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.plugins.stat.prometheus.handler.PrometheusHandlerConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BaseBootConfigHandler 单元测试类
 *
 * @author Yuwei Fu
 */
public class BaseBootConfigHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseBootConfigHandlerTest.class);

    private BaseBootConfigHandler handler;
    private ConfigurationImpl configuration;

    @Before
    public void before() {
        handler = new BaseBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
    }

    /**
     * 创建默认的 PolarisConfig 用于测试
     */
    private PolarisConfig createDefaultPolarisConfig(Map<String, String> parameters) {
        return new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, parameters);
    }

    /**
     * 创建带 token 的 PolarisConfig 用于测试
     */
    private PolarisConfig createPolarisConfigWithToken(String token, Map<String, String> parameters) {
        parameters.put(Consts.KEY_TOKEN, token);
        return new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, parameters);
    }

    // ==================== timeout 参数测试 ====================

    /**
     * 测试：设置有效的 timeout 参数
     */
    @Test
    public void testHandle_withValidTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "5000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(5000L, configuration.getGlobal().getAPI().getTimeout());
        LOG.info("[Test] 有效 timeout 测试通过");
    }

    /**
     * 测试：timeout 为 0 时不设置 API timeout
     */
    @Test
    public void testHandle_withZeroTimeout() {
        // Arrange
        long originalTimeout = configuration.getGlobal().getAPI().getTimeout();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "0");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - timeout 为 0 时不修改配置
        Assert.assertEquals(originalTimeout, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：timeout 为无效值时不设置 API timeout
     */
    @Test
    public void testHandle_withInvalidTimeout() {
        // Arrange
        long originalTimeout = configuration.getGlobal().getAPI().getTimeout();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "not_a_number");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 无效值时不修改配置
        Assert.assertEquals(originalTimeout, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：未设置 timeout 参数时不修改配置
     */
    @Test
    public void testHandle_withoutTimeout() {
        // Arrange
        long originalTimeout = configuration.getGlobal().getAPI().getTimeout();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalTimeout, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：polaris_timeout 覆盖 timeout
     */
    @Test
    public void testHandle_polarisTimeoutOverridesTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "3000");
        parameters.put(Consts.KEY_POLARIS_TIMEOUT, "8000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_timeout 应覆盖 timeout
        Assert.assertEquals(8000L, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：仅设置 polaris_timeout（无 timeout）
     */
    @Test
    public void testHandle_onlyPolarisTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_TIMEOUT, "6000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(6000L, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：polaris_timeout 为无效值时使用 timeout 的值
     */
    @Test
    public void testHandle_invalidPolarisTimeout_usesTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "3000");
        parameters.put(Consts.KEY_POLARIS_TIMEOUT, "invalid");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_timeout 无效，应使用 timeout 的值
        Assert.assertEquals(3000L, configuration.getGlobal().getAPI().getTimeout());
    }

    /**
     * 测试：polaris_timeout 为空字符串时不覆盖 timeout
     */
    @Test
    public void testHandle_emptyPolarisTimeout_usesTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "4000");
        parameters.put(Consts.KEY_POLARIS_TIMEOUT, "");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 空的 polaris_timeout 不覆盖
        Assert.assertEquals(4000L, configuration.getGlobal().getAPI().getTimeout());
    }

    // ==================== persist_enable 参数测试 ====================

    /**
     * 测试：设置 persist_enable 为 true
     */
    @Test
    public void testHandle_withPersistEnableTrue() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PERSIST_ENABLE, "true");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertTrue(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：设置 persist_enable 为 false
     */
    @Test
    public void testHandle_withPersistEnableFalse() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PERSIST_ENABLE, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertFalse(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：未设置 persist_enable 时不修改配置
     */
    @Test
    public void testHandle_withoutPersistEnable() {
        // Arrange
        boolean originalValue = configuration.getConsumer().getLocalCache().isPersistEnable();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalValue, configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：polaris_persist_enable 覆盖 persist_enable
     */
    @Test
    public void testHandle_polarisPersistEnableOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PERSIST_ENABLE, "true");
        parameters.put(Consts.KEY_POLARIS_PERSIST_ENABLE, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_persist_enable 应覆盖 persist_enable
        Assert.assertFalse(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：polaris_persist_enable 覆盖 persist_enable（反向验证）
     */
    @Test
    public void testHandle_polarisPersistEnableOverrides_reverseCase() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PERSIST_ENABLE, "false");
        parameters.put(Consts.KEY_POLARIS_PERSIST_ENABLE, "true");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_persist_enable 应覆盖 persist_enable
        Assert.assertTrue(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：仅设置 polaris_persist_enable（无 persist_enable）
     */
    @Test
    public void testHandle_onlyPolarisPersistEnable() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_PERSIST_ENABLE, "false");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertFalse(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    /**
     * 测试：polaris_persist_enable 为空字符串时不覆盖 persist_enable
     */
    @Test
    public void testHandle_emptyPolarisPersistEnable_usesPersistEnable() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PERSIST_ENABLE, "true");
        parameters.put(Consts.KEY_POLARIS_PERSIST_ENABLE, "");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 空的 polaris_persist_enable 不覆盖
        Assert.assertTrue(configuration.getConsumer().getLocalCache().isPersistEnable());
    }

    // ==================== ServerConnector 配置测试 ====================

    /**
     * 测试：设置 token 后 ServerConnector 配置生效
     */
    @Test
    public void testHandle_serverConnectorToken() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createPolarisConfigWithToken("test-token-123", parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证服务治理 ServerConnector 的 token
        Assert.assertEquals("test-token-123",
                configuration.getGlobal().getServerConnector().getToken());
        // 验证配置中心 ServerConnector 的 token
        Assert.assertEquals("test-token-123",
                configuration.getConfigFile().getServerConnector().getToken());
        LOG.info("[Test] ServerConnector token 设置测试通过");
    }

    /**
     * 测试：无 token 时 ServerConnector 不设置 token
     */
    @Test
    public void testHandle_serverConnectorWithoutToken() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);
        String originalToken = configuration.getGlobal().getServerConnector().getToken();

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 无 token 时不修改 ServerConnector token
        Assert.assertEquals(originalToken, configuration.getGlobal().getServerConnector().getToken());
    }

    /**
     * 测试：ServerConnector 服务治理连接地址设置
     */
    @Test
    public void testHandle_serverConnectorDiscoverAddresses() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证服务治理连接地址
        List<String> addresses = configuration.getGlobal().getServerConnector().getAddresses();
        Assert.assertNotNull(addresses);
        Assert.assertTrue("服务治理地址应包含 127.0.0.1:8091", addresses.contains("127.0.0.1:8091"));
    }

    /**
     * 测试：ServerConnector 配置中心连接地址设置
     */
    @Test
    public void testHandle_serverConnectorConfigAddresses() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证配置中心连接地址
        List<String> configAddresses = configuration.getConfigFile().getServerConnector().getAddresses();
        Assert.assertNotNull(configAddresses);
        Assert.assertTrue("配置中心地址应包含 127.0.0.1:8093", configAddresses.contains("127.0.0.1:8093"));
    }

    // ==================== SDK Context 配置测试 ====================

    /**
     * 测试：detect_when 参数设置主动探测策略
     */
    @Test
    public void testHandle_detectWhen() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "never");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(OutlierDetectionConfig.When.never,
                configuration.getConsumer().getOutlierDetection().getWhen());
    }

    /**
     * 测试：polaris_detect_when 覆盖 detect_when
     */
    @Test
    public void testHandle_polarisDetectWhenOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "never");
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "always");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_detect_when 应覆盖 detect_when
        Assert.assertEquals(OutlierDetectionConfig.When.always,
                configuration.getConsumer().getOutlierDetection().getWhen());
    }

    /**
     * 测试：无效的 detect_when 值不影响配置
     */
    @Test
    public void testHandle_invalidDetectWhen() {
        // Arrange
        OutlierDetectionConfig.When originalWhen = configuration.getConsumer().getOutlierDetection().getWhen();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "invalid_value");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 无效值不修改配置
        Assert.assertEquals(originalWhen, configuration.getConsumer().getOutlierDetection().getWhen());
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
    }

    /**
     * 测试：bindIP 设置为本机地址
     */
    @Test
    public void testHandle_bindIPSetToLocalHost() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - bindIP 应被设置（非 null）
        Assert.assertNotNull(configuration.getGlobal().getAPI().getBindIP());
    }

    // ==================== Prometheus 监控配置测试 ====================

    /**
     * 测试：stat_type 为 push 时的 Prometheus 配置
     */
    @Test
    public void testHandle_prometheusStatPush() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "5000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("push", prometheusConfig.getType());
        Assert.assertTrue("push 地址应包含 10.0.0.1:9091",
                prometheusConfig.getAddress().contains("10.0.0.1:9091"));
        Assert.assertEquals(5000L, (long) prometheusConfig.getPushInterval());
        LOG.info("[Test] Prometheus push 模式测试通过");
    }

    /**
     * 测试：polaris_stat_type 覆盖 stat_type
     */
    @Test
    public void testHandle_polarisStatTypeOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_POLARIS_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_stat_type 应覆盖 stat_type
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("push", prometheusConfig.getType());
    }

    /**
     * 测试：push 模式未指定 addr 时使用 discover 地址默认端口 9091
     */
    @Test
    public void testHandle_prometheusStatPush_defaultAddr() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 未指定 push addr 时默认从 discover 地址推导
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("push", prometheusConfig.getType());
        Assert.assertNotNull(prometheusConfig.getAddress());
        Assert.assertFalse("push 地址不应为空", prometheusConfig.getAddress().isEmpty());
        // 默认地址应包含 127.0.0.1:9091（从 discover 地址 127.0.0.1:8091 推导）
        Assert.assertTrue("默认 push 地址应包含 127.0.0.1:9091",
                prometheusConfig.getAddress().contains("127.0.0.1:9091"));
    }

    /**
     * 测试：push 模式默认 interval 为 10s
     */
    @Test
    public void testHandle_prometheusStatPush_defaultInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals(10000L, (long) prometheusConfig.getPushInterval());
    }

    /**
     * 测试：polaris_stat_push_addr 覆盖 stat_push_addr
     */
    @Test
    public void testHandle_polarisPushAddrOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_ADDR, "10.0.0.2:9091");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertTrue("push 地址应包含 polaris 覆盖地址 10.0.0.2:9091",
                prometheusConfig.getAddress().contains("10.0.0.2:9091"));
    }

    /**
     * 测试：polaris_stat_push_interval 覆盖 stat_push_interval
     */
    @Test
    public void testHandle_polarisPushIntervalOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "5000");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL, "15000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals(15000L, (long) prometheusConfig.getPushInterval());
    }

    /**
     * 测试：stat_type 为 pull 时的 Prometheus 配置
     */
    @Test
    public void testHandle_prometheusStatPull() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "9092");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("pull", prometheusConfig.getType());
        Assert.assertEquals(9092, configuration.getGlobal().getAdmin().getPort());
        LOG.info("[Test] Prometheus pull 模式测试通过");
    }

    /**
     * 测试：pull 模式默认端口为 9091
     */
    @Test
    public void testHandle_prometheusStatPull_defaultPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(9091, configuration.getGlobal().getAdmin().getPort());
    }

    /**
     * 测试：polaris_stat_pull_port 覆盖 stat_pull_port
     */
    @Test
    public void testHandle_polarisPullPortOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "9092");
        parameters.put(Consts.KEY_POLARIS_METRIC_PULL_PORT, "9093");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(9093, configuration.getGlobal().getAdmin().getPort());
    }

    /**
     * 测试：未设置 stat_type 时禁用 stat reporter
     */
    @Test
    public void testHandle_noStatType_disablesStatReporter() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 未设置 stat_type 时应禁用
        Assert.assertFalse(configuration.getGlobal().getStatReporter().isEnable());
    }

    // ==================== PushGateway EventReporter 配置测试 ====================

    /**
     * 测试：启用 PushGateway 事件上报
     */
    @Test
    public void testHandle_pushGatewayEventEnabled() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PGW_EVENT_ENABLED, "true");
        parameters.put(Consts.KEY_PGW_EVENT_ADDR, "10.0.0.1:9091");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 验证 event reporter 被添加
        Assert.assertTrue("event reporters 应包含 pushgateway",
                configuration.getGlobal().getEventReporter().getReporters().contains("pushgateway"));
        LOG.info("[Test] PushGateway 事件上报启用测试通过");
    }

    /**
     * 测试：未启用 PushGateway 事件上报时不设置地址
     */
    @Test
    public void testHandle_pushGatewayEventNotEnabled() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - event reporters 列表中仍会包含 pushGateway（默认添加），但未启用
        Assert.assertTrue("event reporters 应包含 pushgateway 类型",
                configuration.getGlobal().getEventReporter().getReporters().contains("pushgateway"));
    }

    // ==================== 综合场景测试 ====================

    /**
     * 测试：所有参数同时配置，验证 polaris_ 前缀参数的覆盖优先级
     */
    @Test
    public void testHandle_withAllParameters_polarisOverrides() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "3000");
        parameters.put(Consts.KEY_POLARIS_TIMEOUT, "9000");
        parameters.put(Consts.KEY_PERSIST_ENABLE, "true");
        parameters.put(Consts.KEY_POLARIS_PERSIST_ENABLE, "false");
        parameters.put(Consts.KEY_DETECT_WHEN, "never");
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "always");
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_POLARIS_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_ADDR, "10.0.0.2:9091");
        PolarisConfig polarisConfig = createPolarisConfigWithToken("override-token", parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - polaris_ 前缀参数应全部覆盖原始参数
        Assert.assertEquals(9000L, configuration.getGlobal().getAPI().getTimeout());
        Assert.assertFalse(configuration.getConsumer().getLocalCache().isPersistEnable());
        Assert.assertEquals(OutlierDetectionConfig.When.always,
                configuration.getConsumer().getOutlierDetection().getWhen());
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("push", prometheusConfig.getType());
        Assert.assertTrue(prometheusConfig.getAddress().contains("10.0.0.2:9091"));

        LOG.info("[Test] 综合覆盖优先级测试通过");
    }

    /**
     * 测试：空参数 map
     */
    @Test
    public void testHandle_withEmptyParameters() {
        // Arrange
        long originalTimeout = configuration.getGlobal().getAPI().getTimeout();
        boolean originalPersistEnable = configuration.getConsumer().getLocalCache().isPersistEnable();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 空参数不修改 timeout 和 persistEnable
        Assert.assertEquals(originalTimeout, configuration.getGlobal().getAPI().getTimeout());
        Assert.assertEquals(originalPersistEnable, configuration.getConsumer().getLocalCache().isPersistEnable());
        // 但仍然会设置 ServerConnector 地址和 SDK Context 配置
        Assert.assertFalse(configuration.getGlobal().getStatReporter().isEnable());
    }

    /**
     * 测试：push 模式多地址场景（地址用分隔符分隔）
     */
    @Test
    public void testHandle_prometheusStatPush_multipleAddresses() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091/10.0.0.2:9091");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals(2, prometheusConfig.getAddress().size());
        Assert.assertTrue(prometheusConfig.getAddress().contains("10.0.0.1:9091"));
        Assert.assertTrue(prometheusConfig.getAddress().contains("10.0.0.2:9091"));
    }

    /**
     * 测试：push 模式 interval 为无效值时使用默认值
     */
    @Test
    public void testHandle_prometheusStatPush_invalidInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "not_a_number");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 无效 interval 值时使用默认值 10000
        PrometheusHandlerConfig prometheusConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals(10000L, (long) prometheusConfig.getPushInterval());
    }

    /**
     * 测试：pull 模式端口为无效值时使用默认端口
     */
    @Test
    public void testHandle_prometheusStatPull_invalidPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "not_a_number");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert - 无效端口值时使用默认端口 9091
        Assert.assertEquals(9091, configuration.getGlobal().getAdmin().getPort());
    }
}
