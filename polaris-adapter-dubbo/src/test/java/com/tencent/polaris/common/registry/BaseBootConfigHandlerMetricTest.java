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

import com.tencent.polaris.api.config.global.StatReporterConfig;
import com.tencent.polaris.common.config.BaseBootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.plugins.stat.prometheus.handler.PrometheusHandlerConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class BaseBootConfigHandlerMetricTest {

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
    public void statTypePush_setsPushAddressAndInterval() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "push");
        params.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        params.put(Consts.KEY_METRIC_PUSH_INTERVAL, "5000");

        handler.handle(polarisConfig, params, configuration);

        PrometheusHandlerConfig cfg = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        Assert.assertEquals("push", cfg.getType());
        Assert.assertEquals(1, cfg.getAddress().size());
        Assert.assertEquals("10.0.0.1:9091", cfg.getAddress().get(0));
        Assert.assertEquals(Long.valueOf(5000L), Long.valueOf(cfg.getPushInterval()));
    }

    @Test
    public void statTypePush_fallbackToDiscoverAddressHost() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "push");

        handler.handle(polarisConfig, params, configuration);

        PrometheusHandlerConfig cfg = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals(1, cfg.getAddress().size());
        Assert.assertEquals("127.0.0.1:9091", cfg.getAddress().get(0));
    }

    @Test
    public void statTypePull_setsAdminPort() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "pull");
        params.put(Consts.KEY_METRIC_PULL_PORT, "19191");

        handler.handle(polarisConfig, params, configuration);

        PrometheusHandlerConfig cfg = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertTrue(configuration.getGlobal().getStatReporter().isEnable());
        Assert.assertEquals("pull", cfg.getType());
        Assert.assertEquals(19191, configuration.getGlobal().getAdmin().getPort());
    }

    @Test
    public void polarisPrefixedKeyBeatsShortKey_pushAddr() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "push");
        params.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        params.put(Consts.KEY_POLARIS_METRIC_PUSH_ADDR, "10.0.0.2:9091");

        handler.handle(polarisConfig, params, configuration);

        PrometheusHandlerConfig cfg = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("10.0.0.2:9091", cfg.getAddress().get(0));
    }

    @Test
    public void polarisPrefixedKeyBeatsShortKey_statType() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "pull");
        params.put(Consts.KEY_POLARIS_METRIC_TYPE, "push");
        params.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");

        handler.handle(polarisConfig, params, configuration);

        PrometheusHandlerConfig cfg = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);
        Assert.assertEquals("push", cfg.getType());
    }

    @Test
    public void noStatType_disablesStatReporter() {
        handler.handle(polarisConfig, new HashMap<>(), configuration);
        Assert.assertFalse(configuration.getGlobal().getStatReporter().isEnable());
    }

    @Test
    public void unparsableNumericValuesAreIgnored() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_METRIC_TYPE, "pull");
        params.put(Consts.KEY_METRIC_PULL_PORT, "not-a-number");
        // should not throw
        handler.handle(polarisConfig, params, configuration);
        Assert.assertEquals(9091, configuration.getGlobal().getAdmin().getPort());
    }
}
