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

package com.tencent.polaris.common.registry;

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

    // ==================== timeout 参数测试 ====================

    /**
     * 测试：设置有效的 timeout 参数
     */
    @Test
    public void testHandle_withValidTimeout() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TIMEOUT, "5000");

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

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

        // Act
        handler.handle(parameters, configuration);

        // Assert - 空的 polaris_persist_enable 不覆盖
        Assert.assertTrue(configuration.getConsumer().getLocalCache().isPersistEnable());
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

        // Act
        handler.handle(parameters, configuration);

        // Assert - polaris_ 前缀参数应全部覆盖原始参数
        Assert.assertEquals(9000L, configuration.getGlobal().getAPI().getTimeout());
        Assert.assertFalse(configuration.getConsumer().getLocalCache().isPersistEnable());

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

        // Act
        handler.handle(parameters, configuration);

        // Assert - 空参数不修改任何配置
        Assert.assertEquals(originalTimeout, configuration.getGlobal().getAPI().getTimeout());
        Assert.assertEquals(originalPersistEnable, configuration.getConsumer().getLocalCache().isPersistEnable());
    }
}
