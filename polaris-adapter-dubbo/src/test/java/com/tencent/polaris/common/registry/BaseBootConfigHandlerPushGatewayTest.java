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

import com.tencent.polaris.api.config.plugin.DefaultPlugins;
import com.tencent.polaris.common.config.BaseBootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.plugins.event.pushgateway.PushGatewayEventReporterConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class BaseBootConfigHandlerPushGatewayTest {

    private BaseBootConfigHandler handler;
    private ConfigurationImpl configuration;
    private PolarisConfig polarisConfig;

    @Before
    public void setUp() {
        handler = new BaseBootConfigHandler();
        configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
        configuration.setDefault();
        polarisConfig = Mockito.mock(PolarisConfig.class);
        Mockito.when(polarisConfig.getDiscoverAddress()).thenReturn("127.0.0.1:8091");
    }

    @Test
    public void reporterAlwaysRegisteredEvenWithoutParams() {
        handler.handle(polarisConfig, new HashMap<>(), configuration);
        Assert.assertTrue(configuration.getGlobal().getEventReporter().getReporters()
                .contains(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE));
    }

    @Test
    public void pgwEnabledFlagAndAddressApplied() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_PGW_EVENT_ENABLED, "true");
        params.put(Consts.KEY_PGW_EVENT_ADDR, "10.0.0.1:9091/10.0.0.2:9091");

        handler.handle(polarisConfig, params, configuration);

        PushGatewayEventReporterConfig cfg = configuration.getGlobal().getEventReporter()
                .getPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE,
                        PushGatewayEventReporterConfig.class);
        Assert.assertTrue(cfg.isEnable());
        Assert.assertEquals(2, cfg.getAddress().size());
        Assert.assertEquals("10.0.0.1:9091", cfg.getAddress().get(0));
        Assert.assertEquals("10.0.0.2:9091", cfg.getAddress().get(1));
        Assert.assertEquals(Integer.valueOf(1000), Integer.valueOf(cfg.getEventQueueSize()));
        Assert.assertEquals(Integer.valueOf(100), Integer.valueOf(cfg.getMaxBatchSize()));
        Assert.assertEquals("polaris", cfg.getNamespace());
        Assert.assertEquals("polaris.pushgateway", cfg.getService());
    }

    @Test
    public void pgwDisabled_doesNotSetEnable() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_PGW_EVENT_ENABLED, "false");

        handler.handle(polarisConfig, params, configuration);

        PushGatewayEventReporterConfig cfg = configuration.getGlobal().getEventReporter()
                .getPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE,
                        PushGatewayEventReporterConfig.class);
        Assert.assertFalse(cfg.isEnable());
    }
}
