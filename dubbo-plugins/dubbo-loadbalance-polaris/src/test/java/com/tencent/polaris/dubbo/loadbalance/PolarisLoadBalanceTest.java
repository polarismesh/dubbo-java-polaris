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

package com.tencent.polaris.dubbo.loadbalance;

import com.tencent.polaris.api.config.consumer.LoadBalanceConfig;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.common.router.InstanceInvoker;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PolarisLoadBalanceTest {

    private MockedStatic<PolarisOperators> polarisOperatorsMock;
    private PolarisOperator mockOperator;
    private PolarisConfig mockConfig;

    @Before
    public void setUp() {
        mockOperator = mock(PolarisOperator.class);
        mockConfig = mock(PolarisConfig.class);
        when(mockConfig.getNamespace()).thenReturn("default");
        when(mockOperator.getPolarisConfig()).thenReturn(mockConfig);

        polarisOperatorsMock = Mockito.mockStatic(PolarisOperators.class);
        polarisOperatorsMock.when(PolarisOperators::getGovernancePolarisOperator).thenReturn(mockOperator);
    }

    @After
    public void tearDown() {
        polarisOperatorsMock.close();
    }

    @SuppressWarnings("unchecked")
    private <T> List<Invoker<T>> createMockInvokers(int count, Instance returnInstance) {
        List<Invoker<T>> invokers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Invoker<T> invoker = mock(Invoker.class);
            URL url = URL.valueOf("dubbo://192.168.1." + i + ":20880/com.example.FooService");
            when(invoker.getUrl()).thenReturn(url);
            invokers.add(invoker);
        }

        when(mockOperator.loadBalance(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(returnInstance);

        return invokers;
    }

    @Test
    public void testWeightedRandomLbPolicy() {
        PolarisWeightedRandomLoadBalance lb = new PolarisWeightedRandomLoadBalance();
        assertThat(lb.getLbPolicy()).isEqualTo(LoadBalanceConfig.LOAD_BALANCE_WEIGHTED_RANDOM);
    }

    @Test
    public void testWeightedRoundRobinLbPolicy() {
        PolarisWeightedRoundRobinLoadBalance lb = new PolarisWeightedRoundRobinLoadBalance();
        assertThat(lb.getLbPolicy()).isEqualTo(LoadBalanceConfig.LOAD_BALANCE_WEIGHTED_ROUND_ROBIN);
    }

    @Test
    public void testRingHashLbPolicy() {
        PolarisRingHashLoadBalance lb = new PolarisRingHashLoadBalance();
        assertThat(lb.getLbPolicy()).isEqualTo(LoadBalanceConfig.LOAD_BALANCE_RING_HASH);
    }

    @Test
    public void testShortestResponseTimeLbPolicy() {
        PolarisShortestResponseTimeLoadBalance lb = new PolarisShortestResponseTimeLoadBalance();
        assertThat(lb.getLbPolicy()).isEqualTo(LoadBalanceConfig.LOAD_BALANCE_SHORTEST_RESPONSE_TIME);
    }

    @Test
    public void testLeastConnectionLbPolicy() {
        PolarisLeastConnectionLoadBalance lb = new PolarisLeastConnectionLoadBalance();
        assertThat(lb.getLbPolicy()).isEqualTo(LoadBalanceConfig.LOAD_BALANCE_LEAST_CONNECTION);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDoSelectCallsOperatorWithCorrectLbPolicy() {
        InstanceInvoker<Object> expectedInstance = mock(InstanceInvoker.class);
        List<Invoker<Object>> invokers = createMockInvokers(3, expectedInstance);

        URL url = URL.valueOf("dubbo://192.168.1.1:20880/com.example.FooService");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn("hello");

        PolarisWeightedRandomLoadBalance lb = new PolarisWeightedRandomLoadBalance();
        Invoker<Object> result = lb.doSelect(invokers, url, invocation);

        verify(mockOperator).loadBalance(
                eq("com.example.FooService"),
                eq(LoadBalanceConfig.LOAD_BALANCE_WEIGHTED_RANDOM),
                eq(""),
                anyList()
        );
        assertThat(result).isSameAs(expectedInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRingHashBuildHashKey() {
        PolarisRingHashLoadBalance lb = new PolarisRingHashLoadBalance();

        URL url = URL.valueOf("dubbo://192.168.1.1:20880/com.example.FooService");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn("hello");
        when(invocation.getArguments()).thenReturn(new Object[]{"arg0Value", "arg1Value"});

        String hashKey = lb.buildHashKey(url, invocation);
        // Default hash.arguments is "0", so should include method name + first argument
        assertThat(hashKey).isEqualTo("helloarg0Value");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRingHashBuildHashKeyWithMultipleArguments() {
        PolarisRingHashLoadBalance lb = new PolarisRingHashLoadBalance();

        URL url = URL.valueOf("dubbo://192.168.1.1:20880/com.example.FooService?hello.hash.arguments=0,1");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn("hello");
        when(invocation.getArguments()).thenReturn(new Object[]{"arg0Value", "arg1Value"});

        String hashKey = lb.buildHashKey(url, invocation);
        assertThat(hashKey).isEqualTo("helloarg0Valuearg1Value");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFallbackWhenOperatorIsNull() {
        polarisOperatorsMock.when(PolarisOperators::getGovernancePolarisOperator).thenReturn(null);

        List<Invoker<Object>> invokers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Invoker<Object> invoker = mock(Invoker.class);
            URL invokerUrl = URL.valueOf("dubbo://192.168.1." + i + ":20880/com.example.FooService");
            when(invoker.getUrl()).thenReturn(invokerUrl);
            invokers.add(invoker);
        }

        URL url = URL.valueOf("dubbo://192.168.1.1:20880/com.example.FooService");
        Invocation invocation = mock(Invocation.class);

        PolarisWeightedRandomLoadBalance lb = new PolarisWeightedRandomLoadBalance();
        Invoker<Object> result = lb.doSelect(invokers, url, invocation);

        assertThat(result).isIn(invokers);
        verify(mockOperator, never()).loadBalance(anyString(), anyString(), anyString(), anyList());
    }
}
