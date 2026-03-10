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

import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.provider.LosslessConfigImpl;
import com.tencent.polaris.specification.api.v1.traffic.manage.LosslessProto;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LosslessBootConfigHandlerTest {

    private LosslessBootConfigHandler handler;
    private ConfigurationImpl configuration;

    @Before
    public void setUp() {
        handler = new LosslessBootConfigHandler();
        configuration = new ConfigurationImpl();
        configuration.setDefault();
    }

    @Test
    public void testHandle_losslessEnabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "true");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertTrue("无损功能应启用", losslessConfig.isEnable());
    }

    @Test
    public void testHandle_losslessDisabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "false");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertFalse("无损功能应禁用", losslessConfig.isEnable());
    }

    @Test
    public void testHandle_delayTime() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL, "30000");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("延迟时间应为 30000ms", 30000L, losslessConfig.getDelayRegisterInterval());
        Assert.assertEquals("策略应为 DELAY_BY_TIME",
                LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME, losslessConfig.getStrategy());
    }

    @Test
    public void testHandle_healthCheckPath() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH, "/health");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("健康检查路径应为 /health", "/health", losslessConfig.getHealthCheckPath());
        Assert.assertEquals("策略应为 DELAY_BY_HEALTH_CHECK",
                LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK, losslessConfig.getStrategy());
    }

    @Test
    public void testHandle_adminPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ADMIN_PORT, "18080");

        handler.handle(null, parameters, configuration);

        Assert.assertEquals("管理端口应为 18080", 18080, (int) configuration.getGlobal().getAdmin().getPort());
    }

    @Test
    public void testHandle_invalidDelayTime_ignored() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL, "not_a_number");

        handler.handle(null, parameters, configuration);
    }

    @Test
    public void testHandle_invalidAdminPort_ignored() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ADMIN_PORT, "not_a_number");

        handler.handle(null, parameters, configuration);
    }

    @Test
    public void testHandle_emptyParameters() {
        Map<String, String> parameters = new HashMap<>();

        handler.handle(null, parameters, configuration);
    }

    @Test
    public void testHandle_healthCheckInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH, "/health");
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL, "5000");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("健康检查间隔应为 5000ms", 5000L, losslessConfig.getHealthCheckInterval());
        Assert.assertEquals("策略应为 DELAY_BY_HEALTH_CHECK",
                LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK, losslessConfig.getStrategy());
    }

    @Test
    public void testHandle_healthCheckIntervalWithoutPath_ignored() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL, "5000");

        LosslessConfigImpl before = (LosslessConfigImpl) configuration.getProvider().getLossless();
        long defaultInterval = before.getHealthCheckInterval();

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("无 path 时 interval 不应变更", defaultInterval, losslessConfig.getHealthCheckInterval());
    }

    @Test
    public void testHandle_invalidHealthCheckInterval_ignored() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH, "/health");
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL, "not_a_number");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("健康检查路径应设置", "/health", losslessConfig.getHealthCheckPath());
    }

    @Test
    public void testHandle_healthCheckPathOverridesDelayTime() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL, "30000");
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH, "/health");

        handler.handle(null, parameters, configuration);

        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("策略应为 DELAY_BY_HEALTH_CHECK",
                LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK, losslessConfig.getStrategy());
        Assert.assertEquals("延迟时间仍应设置", 30000L, losslessConfig.getDelayRegisterInterval());
        Assert.assertEquals("健康检查路径应设置", "/health", losslessConfig.getHealthCheckPath());
    }
}
