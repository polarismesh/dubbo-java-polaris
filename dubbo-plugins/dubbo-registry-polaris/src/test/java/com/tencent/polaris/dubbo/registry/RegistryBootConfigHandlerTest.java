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
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RegistryBootConfigHandler 单元测试
 *
 * @author Yuwei Fu
 * @date 2026-03-02
 */
public class RegistryBootConfigHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryBootConfigHandlerTest.class);

    private RegistryBootConfigHandler handler;
    private ConfigurationImpl configuration;

    @Before
    public void before() {
        handler = new RegistryBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
    }

    /**
     * 创建默认的 PolarisConfig 用于测试
     */
    private PolarisConfig createDefaultPolarisConfig(Map<String, String> parameters) {
        return new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, parameters);
    }

    // ==================== polaris_lb_policy 参数测试 ====================

    /**
     * 测试：设置有效的 lb_policy 参数
     */
    @Test
    public void testHandle_withValidLbPolicy() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_LB_POLICY, "round_robin");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals("round_robin",
                configuration.getGlobal().getServerConnector().getLbPolicy());
        LOG.info("[Test] 有效 lb_policy 设置测试通过");
    }

    /**
     * 测试：未设置 lb_policy 时不修改 ServerConnector 配置
     */
    @Test
    public void testHandle_withoutLbPolicy() {
        // Arrange
        String originalLbPolicy = configuration.getGlobal().getServerConnector().getLbPolicy();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalLbPolicy,
                configuration.getGlobal().getServerConnector().getLbPolicy());
        LOG.info("[Test] 未设置 lb_policy 时不修改配置测试通过");
    }

    /**
     * 测试：lb_policy 设置为不同策略值
     */
    @Test
    public void testHandle_withDifferentLbPolicies() {
        // Arrange
        String[] policies = {"round_robin", "random", "ring_hash", "maglev"};
        for (String policy : policies) {
            configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
            Map<String, String> parameters = new HashMap<>();
            parameters.put(Consts.KEY_LB_POLICY, policy);
            PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

            // Act
            handler.handle(polarisConfig, parameters, configuration);

            // Assert
            Assert.assertEquals(policy,
                    configuration.getGlobal().getServerConnector().getLbPolicy());
        }
        LOG.info("[Test] 不同 lb_policy 策略值设置测试通过");
    }

    // ==================== polaris_server_switch_interval 参数测试 ====================

    /**
     * 测试：设置有效的 server_switch_interval 参数
     */
    @Test
    public void testHandle_withValidServerSwitchInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "30000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(30000L,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
        LOG.info("[Test] 有效 server_switch_interval 设置测试通过");
    }

    /**
     * 测试：未设置 server_switch_interval 时不修改 ServerConnector 配置
     */
    @Test
    public void testHandle_withoutServerSwitchInterval() {
        // Arrange
        long originalInterval = configuration.getGlobal().getServerConnector().getServerSwitchInterval();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalInterval,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
        LOG.info("[Test] 未设置 server_switch_interval 时不修改配置测试通过");
    }

    /**
     * 测试：server_switch_interval 设置为最小值 1
     */
    @Test
    public void testHandle_withMinServerSwitchInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "1");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(1L,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
    }

    /**
     * 测试：server_switch_interval 设置为较大值
     */
    @Test
    public void testHandle_withLargeServerSwitchInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "3600000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(3600000L,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
    }

    /**
     * 测试：server_switch_interval 为无效值时抛出 NumberFormatException
     */
    @Test
    public void testHandle_withInvalidServerSwitchInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "not_a_number");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act & Assert
        Assert.assertThrows(NumberFormatException.class, () -> {
            handler.handle(polarisConfig, parameters, configuration);
        });
        LOG.info("[Test] 无效 server_switch_interval 抛出异常测试通过");
    }

    // ==================== 综合场景测试 ====================

    /**
     * 测试：同时设置 lb_policy 和 server_switch_interval
     */
    @Test
    public void testHandle_withBothParameters() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_LB_POLICY, "ring_hash");
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "60000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals("ring_hash",
                configuration.getGlobal().getServerConnector().getLbPolicy());
        Assert.assertEquals(60000L,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
        LOG.info("[Test] 同时设置两个参数测试通过");
    }

    /**
     * 测试：空参数 map 时不修改任何 ServerConnector 配置
     */
    @Test
    public void testHandle_withEmptyParameters() {
        // Arrange
        String originalLbPolicy = configuration.getGlobal().getServerConnector().getLbPolicy();
        long originalInterval = configuration.getGlobal().getServerConnector().getServerSwitchInterval();
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalLbPolicy,
                configuration.getGlobal().getServerConnector().getLbPolicy());
        Assert.assertEquals(originalInterval,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
        LOG.info("[Test] 空参数不修改配置测试通过");
    }

    /**
     * 测试：仅设置 lb_policy，server_switch_interval 保持默认值
     */
    @Test
    public void testHandle_onlyLbPolicy_intervalUnchanged() {
        // Arrange
        long originalInterval = configuration.getGlobal().getServerConnector().getServerSwitchInterval();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_LB_POLICY, "random");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals("random",
                configuration.getGlobal().getServerConnector().getLbPolicy());
        Assert.assertEquals(originalInterval,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
    }

    /**
     * 测试：仅设置 server_switch_interval，lb_policy 保持默认值
     */
    @Test
    public void testHandle_onlyServerSwitchInterval_lbPolicyUnchanged() {
        // Arrange
        String originalLbPolicy = configuration.getGlobal().getServerConnector().getLbPolicy();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "45000");
        PolarisConfig polarisConfig = createDefaultPolarisConfig(parameters);

        // Act
        handler.handle(polarisConfig, parameters, configuration);

        // Assert
        Assert.assertEquals(originalLbPolicy,
                configuration.getGlobal().getServerConnector().getLbPolicy());
        Assert.assertEquals(45000L,
                configuration.getGlobal().getServerConnector().getServerSwitchInterval());
    }
}
