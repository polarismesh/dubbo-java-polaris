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

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.config.provider.LosslessConfig;
import com.tencent.polaris.api.config.provider.ProviderConfig;
import com.tencent.polaris.api.core.LosslessAPI;
import com.tencent.polaris.api.plugin.compose.Extensions;
import com.tencent.polaris.api.plugin.lossless.LosslessActionProvider;
import com.tencent.polaris.api.pojo.BaseInstance;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.plugin.lossless.common.LosslessUtils;
import org.apache.dubbo.common.URL;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class PolarisRegistryLosslessTest {

    private MockedStatic<PolarisOperators> mockedStaticOperators;
    private MockedStatic<LosslessUtils> mockedStaticLosslessUtils;
    private PolarisOperator mockOperator;
    private PolarisConfig mockConfig;
    private LosslessAPI mockLosslessAPI;

    @Before
    public void setUp() {
        mockOperator = Mockito.mock(PolarisOperator.class);
        mockConfig = Mockito.mock(PolarisConfig.class);
        mockLosslessAPI = Mockito.mock(LosslessAPI.class);

        SDKContext mockSdkContext = Mockito.mock(SDKContext.class);
        Extensions mockExtensions = Mockito.mock(Extensions.class);
        Configuration mockConfiguration = Mockito.mock(Configuration.class);
        ProviderConfig mockProviderConfig = Mockito.mock(ProviderConfig.class);
        LosslessConfig mockLosslessConfig = Mockito.mock(LosslessConfig.class);

        Mockito.when(mockOperator.getSdkContext()).thenReturn(mockSdkContext);
        Mockito.when(mockSdkContext.getExtensions()).thenReturn(mockExtensions);
        Mockito.when(mockExtensions.getConfiguration()).thenReturn(mockConfiguration);
        Mockito.when(mockConfiguration.getProvider()).thenReturn(mockProviderConfig);
        Mockito.when(mockProviderConfig.getLossless()).thenReturn(mockLosslessConfig);

        Mockito.when(mockOperator.getPolarisConfig()).thenReturn(mockConfig);
        Mockito.when(mockOperator.getLosslessAPI()).thenReturn(mockLosslessAPI);
        Mockito.when(mockConfig.getNamespace()).thenReturn("default");

        mockedStaticLosslessUtils = Mockito.mockStatic(LosslessUtils.class);
        mockedStaticLosslessUtils.when(() -> LosslessUtils.getMatchLosslessRule(
                Mockito.any(Extensions.class), Mockito.any(BaseInstance.class)))
                .thenReturn(null);

        mockedStaticOperators = Mockito.mockStatic(PolarisOperators.class);
        mockedStaticOperators.when(() -> PolarisOperators.loadOrStoreForGovernance(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyMap()))
                .thenReturn(mockOperator);
    }

    @After
    public void tearDown() {
        mockedStaticLosslessUtils.close();
        mockedStaticOperators.close();
    }

    @Test
    public void doRegister_losslessEnabled_callsLosslessAPI() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(true);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL serviceUrl = URL.valueOf(
                "dubbo://192.168.1.1:20880/com.example.FooService?version=1.0&side=provider");
        registry.doRegister(serviceUrl);

        ArgumentCaptor<BaseInstance> instanceCaptor = ArgumentCaptor.forClass(BaseInstance.class);
        Mockito.verify(mockLosslessAPI).setLosslessActionProvider(
                instanceCaptor.capture(), Mockito.any(LosslessActionProvider.class));
        Mockito.verify(mockLosslessAPI).losslessRegister(Mockito.any(BaseInstance.class));

        BaseInstance captured = instanceCaptor.getValue();
        Assert.assertEquals("default", captured.getNamespace());
        Assert.assertEquals("com.example.FooService", captured.getService());
        Assert.assertEquals("192.168.1.1", captured.getHost());
        Assert.assertEquals(20880, captured.getPort());

        Mockito.verify(mockOperator, Mockito.never()).register(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyMap());
    }

    @Test
    public void doRegister_losslessDisabled_callsDirectRegister() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(false);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL serviceUrl = URL.valueOf(
                "dubbo://192.168.1.1:20880/com.example.FooService?version=1.0&side=provider");
        registry.doRegister(serviceUrl);

        Mockito.verify(mockOperator).register(
                Mockito.eq("com.example.FooService"),
                Mockito.eq("192.168.1.1"),
                Mockito.eq(20880),
                Mockito.eq("dubbo"),
                Mockito.eq("1.0"),
                Mockito.anyInt(),
                Mockito.anyMap());

        Mockito.verify(mockLosslessAPI, Mockito.never()).setLosslessActionProvider(Mockito.any(), Mockito.any());
        Mockito.verify(mockLosslessAPI, Mockito.never()).losslessRegister(Mockito.any());
    }

    @Test
    public void doUnregister_losslessEnabled_callsDirectDeregister() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(true);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL serviceUrl = URL.valueOf(
                "dubbo://192.168.1.1:20880/com.example.FooService?version=1.0&side=provider");
        registry.doRegister(serviceUrl);
        registry.doUnregister(serviceUrl);

        Mockito.verify(mockOperator).deregister(
                Mockito.eq("com.example.FooService"),
                Mockito.eq("192.168.1.1"),
                Mockito.eq(20880));
        Mockito.verify(mockLosslessAPI, Mockito.never()).losslessDeRegister(Mockito.any());
    }

    @Test
    public void losslessActionProvider_registerCallback_invokesOperatorRegister() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(true);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL serviceUrl = URL.valueOf(
                "dubbo://192.168.1.1:20880/com.example.FooService?version=1.0&side=provider");
        registry.doRegister(serviceUrl);

        ArgumentCaptor<LosslessActionProvider> providerCaptor = ArgumentCaptor.forClass(LosslessActionProvider.class);
        Mockito.verify(mockLosslessAPI).setLosslessActionProvider(Mockito.any(), providerCaptor.capture());
        LosslessActionProvider provider = providerCaptor.getValue();
        Assert.assertEquals("http", provider.getName());

        provider.doRegister(null);
        Mockito.verify(mockOperator).register(
                Mockito.eq("com.example.FooService"),
                Mockito.eq("192.168.1.1"),
                Mockito.eq(20880),
                Mockito.eq("dubbo"),
                Mockito.eq("1.0"),
                Mockito.anyInt(),
                Mockito.anyMap());
    }

    @Test
    public void losslessActionProvider_deregisterCallback_invokesOperatorDeregister() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(true);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL serviceUrl = URL.valueOf(
                "dubbo://192.168.1.1:20880/com.example.FooService?version=1.0&side=provider");
        registry.doRegister(serviceUrl);

        ArgumentCaptor<LosslessActionProvider> providerCaptor = ArgumentCaptor.forClass(LosslessActionProvider.class);
        Mockito.verify(mockLosslessAPI).setLosslessActionProvider(Mockito.any(), providerCaptor.capture());
        LosslessActionProvider provider = providerCaptor.getValue();

        provider.doDeregister();
        Mockito.verify(mockOperator).deregister("com.example.FooService", "192.168.1.1", 20880);
    }

    @Test
    public void doRegister_consumerSide_skipped() {
        Mockito.when(mockConfig.isLosslessEnabled()).thenReturn(true);

        URL registryUrl = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisRegistry registry = new PolarisRegistry(registryUrl);

        URL consumerUrl = URL.valueOf(
                "consumer://192.168.1.1/com.example.FooService?side=consumer");
        registry.doRegister(consumerUrl);

        Mockito.verify(mockLosslessAPI, Mockito.never()).setLosslessActionProvider(Mockito.any(), Mockito.any());
        Mockito.verify(mockLosslessAPI, Mockito.never()).losslessRegister(Mockito.any());
        Mockito.verify(mockOperator, Mockito.never()).register(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyMap());
    }
}
