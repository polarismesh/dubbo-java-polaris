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
 */

package com.tencent.polaris.dubbo.registry;

import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.provider.LosslessConfigImpl;
import com.tencent.polaris.specification.api.v1.traffic.manage.LosslessProto;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class LosslessBootConfigHandlerTest {

    private LosslessBootConfigHandler handler;
    private ConfigurationImpl configuration;
    private PolarisConfig polarisConfig;

    @Before
    public void setUp() {
        handler = new LosslessBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
        configuration.setDefault();
        polarisConfig = Mockito.mock(PolarisConfig.class);
    }

    @Test
    public void enabledFlagApplied() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "true");
        handler.handle(polarisConfig, params, configuration);
        LosslessConfigImpl lossless = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertTrue(lossless.isEnable());
    }

    @Test
    public void delayRegisterIntervalSetsDelayByTimeStrategy() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL, "30000");
        handler.handle(polarisConfig, params, configuration);
        LosslessConfigImpl lossless = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals(30000L, lossless.getDelayRegisterInterval());
        Assert.assertEquals(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME, lossless.getStrategy());
    }

    @Test
    public void healthCheckPathSetsDelayByHealthCheckStrategy() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH, "/health");
        params.put(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL, "5000");
        handler.handle(polarisConfig, params, configuration);
        LosslessConfigImpl lossless = (LosslessConfigImpl) configuration.getProvider().getLossless();
        Assert.assertEquals("/health", lossless.getHealthCheckPath());
        Assert.assertEquals(5000L, lossless.getHealthCheckInterval());
        Assert.assertEquals(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK, lossless.getStrategy());
    }

    @Test
    public void adminPortAppliedToGlobalAdmin() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_ADMIN_PORT, "28080");
        handler.handle(polarisConfig, params, configuration);
        Assert.assertEquals(28080, configuration.getGlobal().getAdmin().getPort());
    }

    @Test
    public void unparsableNumericValuesAreIgnored() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_ADMIN_PORT, "not-a-number");
        params.put(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL, "abc");
        // should not throw
        handler.handle(polarisConfig, params, configuration);
    }
}
