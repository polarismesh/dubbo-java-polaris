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

import com.tencent.polaris.api.plugin.compose.Extensions;
import com.tencent.polaris.api.plugin.server.ServerConnector;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.plugins.connector.common.DestroyableServerConnector;
import com.tencent.polaris.plugins.connector.composite.CompositeConnector;
import com.tencent.polaris.plugins.connector.nacos.NacosConnector;
import com.tencent.polaris.plugins.connector.nacos.NacosContext;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * 单测 PolarisRegistry 的 Nacos 订阅映射注入逻辑:
 *   - resolveNacosContext (Task 3)
 *   - buildNacosDubboServiceName + doSubscribe 写入行为 (Task 4)
 */
public class PolarisRegistryNacosSubscribeMappingTest {

    /**
     * 非多 connector 模式(GrpcConnector 直连):resolveNacosContext 返回 null
     */
    @Test
    public void testResolveNacosContext_notCompositeConnector_returnsNull() {
        SDKContext sdk = Mockito.mock(SDKContext.class);
        Extensions extensions = Mockito.mock(Extensions.class);
        ServerConnector grpcOnly = Mockito.mock(ServerConnector.class);
        Mockito.when(sdk.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getServerConnector()).thenReturn(grpcOnly);

        NacosContext ctx = PolarisRegistry.resolveNacosContext(sdk);
        Assert.assertNull(ctx);
    }

    /**
     * 多 connector 模式但下属无 NacosConnector:返回 null
     */
    @Test
    public void testResolveNacosContext_compositeWithoutNacos_returnsNull() {
        SDKContext sdk = Mockito.mock(SDKContext.class);
        Extensions extensions = Mockito.mock(Extensions.class);
        CompositeConnector composite = Mockito.mock(CompositeConnector.class);
        DestroyableServerConnector grpc = Mockito.mock(DestroyableServerConnector.class);
        Mockito.when(sdk.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getServerConnector()).thenReturn(composite);
        Mockito.when(composite.getServerConnectors()).thenReturn(Collections.singletonList(grpc));

        NacosContext ctx = PolarisRegistry.resolveNacosContext(sdk);
        Assert.assertNull(ctx);
    }

    /**
     * 有 NacosConnector 但 dubboAdapt=false:返回 null
     */
    @Test
    public void testResolveNacosContext_nacosPresentButDubboAdaptFalse_returnsNull() {
        SDKContext sdk = Mockito.mock(SDKContext.class);
        Extensions extensions = Mockito.mock(Extensions.class);
        CompositeConnector composite = Mockito.mock(CompositeConnector.class);
        NacosConnector nacos = Mockito.mock(NacosConnector.class);
        NacosContext nacosCtx = new NacosContext();
        nacosCtx.setDubboAdapt(false);
        Mockito.when(sdk.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getServerConnector()).thenReturn(composite);
        Mockito.when(composite.getServerConnectors()).thenReturn(Collections.singletonList(nacos));
        Mockito.when(nacos.getNacosContext()).thenReturn(nacosCtx);

        NacosContext ctx = PolarisRegistry.resolveNacosContext(sdk);
        Assert.assertNull(ctx);
    }

    /**
     * 有 NacosConnector 且 dubboAdapt=true:返回该 NacosContext
     */
    @Test
    public void testResolveNacosContext_nacosPresentAndDubboAdaptTrue_returnsContext() {
        SDKContext sdk = Mockito.mock(SDKContext.class);
        Extensions extensions = Mockito.mock(Extensions.class);
        CompositeConnector composite = Mockito.mock(CompositeConnector.class);
        DestroyableServerConnector grpc = Mockito.mock(DestroyableServerConnector.class);
        NacosConnector nacos = Mockito.mock(NacosConnector.class);
        NacosContext nacosCtx = new NacosContext();
        nacosCtx.setDubboAdapt(true);
        Mockito.when(sdk.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getServerConnector()).thenReturn(composite);
        Mockito.when(composite.getServerConnectors()).thenReturn(Arrays.asList(grpc, nacos));
        Mockito.when(nacos.getNacosContext()).thenReturn(nacosCtx);

        NacosContext ctx = PolarisRegistry.resolveNacosContext(sdk);
        Assert.assertSame(nacosCtx, ctx);
    }

    /**
     * 拼接规则: providers:{interface}:{version}:{group}
     */
    @Test
    public void testBuildNacosDubboServiceName_withVersionAndGroup() {
        org.apache.dubbo.common.URL url = org.apache.dubbo.common.URL.valueOf(
                "dubbo://10.0.0.1:20880/com.example.IFoo?version=1.0.0&group=groupA");
        String name = PolarisRegistry.buildNacosDubboServiceName(url);
        Assert.assertEquals("providers:com.example.IFoo:1.0.0:groupA", name);
    }

    @Test
    public void testBuildNacosDubboServiceName_withoutVersionAndGroup() {
        org.apache.dubbo.common.URL url = org.apache.dubbo.common.URL.valueOf(
                "dubbo://10.0.0.1:20880/com.example.IBar");
        String name = PolarisRegistry.buildNacosDubboServiceName(url);
        Assert.assertEquals("providers:com.example.IBar::", name);
    }

    /**
     * nacosContext != null 时,doSubscribe 会在 watchService 前
     * 调用 nacosContext.putServiceNameMapping(polarisName, buildNacosDubboServiceName(url))。
     *
     * 这里直接验证 NacosContext 侧的状态变化,避免深度 mock PolarisRegistry 构造链路。
     */
    @Test
    public void testPutServiceNameMapping_writesExpectedEntry() {
        NacosContext nacosCtx = new NacosContext();
        nacosCtx.setDubboAdapt(true);
        org.apache.dubbo.common.URL url = org.apache.dubbo.common.URL.valueOf(
                "dubbo://10.0.0.1:20880/com.example.IFoo?version=1.0.0&group=groupA");

        String polarisName = url.getServiceInterface();
        String nacosName = PolarisRegistry.buildNacosDubboServiceName(url);
        nacosCtx.putServiceNameMapping(polarisName, nacosName);

        Assert.assertEquals(
                "providers:com.example.IFoo:1.0.0:groupA",
                nacosCtx.getServiceNameMappings().get("com.example.IFoo"));
    }
}
