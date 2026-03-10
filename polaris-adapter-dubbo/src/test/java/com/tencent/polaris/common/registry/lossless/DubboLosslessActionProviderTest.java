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

package com.tencent.polaris.common.registry.lossless;

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.config.provider.LosslessConfig;
import com.tencent.polaris.api.config.provider.ProviderConfig;
import com.tencent.polaris.api.plugin.compose.Extensions;
import com.tencent.polaris.api.plugin.lossless.InstanceProperties;
import com.tencent.polaris.api.pojo.BaseInstance;
import com.tencent.polaris.specification.api.v1.traffic.manage.LosslessProto;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class DubboLosslessActionProviderTest {

    @Test
    public void testDoRegister_invokesOriginalAction() {
        AtomicBoolean registerCalled = new AtomicBoolean(false);
        AtomicBoolean deregisterCalled = new AtomicBoolean(false);

        Extensions extensions = mockExtensions(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME);

        DubboLosslessActionProvider provider = new DubboLosslessActionProvider(
                () -> registerCalled.set(true),
                () -> deregisterCalled.set(true),
                20880,
                mockBaseInstance(),
                extensions
        );

        provider.doRegister(new InstanceProperties());

        Assert.assertTrue("原始注册回调应被调用", registerCalled.get());
        Assert.assertFalse("反注册回调不应被调用", deregisterCalled.get());
    }

    @Test
    public void testDoDeregister_invokesOriginalAction() {
        AtomicBoolean registerCalled = new AtomicBoolean(false);
        AtomicBoolean deregisterCalled = new AtomicBoolean(false);

        Extensions extensions = mockExtensions(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME);

        DubboLosslessActionProvider provider = new DubboLosslessActionProvider(
                () -> registerCalled.set(true),
                () -> deregisterCalled.set(true),
                20880,
                mockBaseInstance(),
                extensions
        );

        provider.doDeregister();

        Assert.assertFalse("注册回调不应被调用", registerCalled.get());
        Assert.assertTrue("原始反注册回调应被调用", deregisterCalled.get());
    }

    @Test
    public void testGetName() {
        Extensions extensions = mockExtensions(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME);

        DubboLosslessActionProvider provider = new DubboLosslessActionProvider(
                () -> {}, () -> {}, 20880, mockBaseInstance(), extensions);

        Assert.assertEquals("dubbo", provider.getName());
    }

    @Test
    public void testIsEnableHealthCheck_withHealthCheckStrategy() {
        Extensions extensions = mockExtensions(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK);

        DubboLosslessActionProvider provider = new DubboLosslessActionProvider(
                () -> {}, () -> {}, 20880, mockBaseInstance(), extensions);

        Assert.assertTrue("基于健康检查策略时应启用健康检查", provider.isEnableHealthCheck());
    }

    @Test
    public void testIsEnableHealthCheck_withTimeStrategy() {
        Extensions extensions = mockExtensions(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME);

        DubboLosslessActionProvider provider = new DubboLosslessActionProvider(
                () -> {}, () -> {}, 20880, mockBaseInstance(), extensions);

        Assert.assertFalse("基于时间策略时不应启用健康检查", provider.isEnableHealthCheck());
    }

    private Extensions mockExtensions(LosslessProto.DelayRegister.DelayStrategy strategy) {
        Extensions extensions = Mockito.mock(Extensions.class);
        Configuration configuration = Mockito.mock(Configuration.class);
        ProviderConfig providerConfig = Mockito.mock(ProviderConfig.class);
        LosslessConfig losslessConfig = Mockito.mock(LosslessConfig.class);

        Mockito.when(extensions.getConfiguration()).thenReturn(configuration);
        Mockito.when(configuration.getProvider()).thenReturn(providerConfig);
        Mockito.when(providerConfig.getLossless()).thenReturn(losslessConfig);
        Mockito.when(losslessConfig.getStrategy()).thenReturn(strategy);
        Mockito.when(losslessConfig.getHealthCheckPath()).thenReturn("/health");

        return extensions;
    }

    private BaseInstance mockBaseInstance() {
        BaseInstance instance = Mockito.mock(BaseInstance.class);
        Mockito.when(instance.getNamespace()).thenReturn("default");
        Mockito.when(instance.getService()).thenReturn("com.example.FooService");
        Mockito.when(instance.getHost()).thenReturn("10.0.0.1");
        Mockito.when(instance.getPort()).thenReturn(20880);
        return instance;
    }
}
