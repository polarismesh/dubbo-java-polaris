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

package com.tencent.polaris.dubbo.router;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.client.pojo.ServiceRuleByProto;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PolarisRouterTest {

    private PolarisRouter polarisRouter;
    private PolarisOperator mockOperator;
    private PolarisConfig mockConfig;

    @Before
    public void before() throws Exception {
        mockOperator = Mockito.mock(PolarisOperator.class);
        mockConfig = Mockito.mock(PolarisConfig.class);
        Mockito.when(mockConfig.getNamespace()).thenReturn("default");
        Mockito.when(mockConfig.isNearbyEnabled()).thenReturn(false);
        Mockito.when(mockOperator.getPolarisConfig()).thenReturn(mockConfig);

        // Inject mock operator into PolarisOperators singleton
        injectGovernanceOperator(mockOperator);

        URL url = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        polarisRouter = new PolarisRouter(url);
    }

    @After
    public void after() throws Exception {
        clearGovernanceOperators();
    }

    @SuppressWarnings("unchecked")
    private void injectGovernanceOperator(PolarisOperator operator) throws Exception {
        Field instanceField = PolarisOperators.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);

        Field mapField = PolarisOperators.class.getDeclaredField("polarisOperatorMap");
        mapField.setAccessible(true);
        java.util.Map<PolarisOperators.OperatorType, java.util.Map<String, PolarisOperator>> map =
                (java.util.Map<PolarisOperators.OperatorType, java.util.Map<String, PolarisOperator>>) mapField.get(
                        instance);
        map.get(PolarisOperators.OperatorType.GOVERNANCE).put("test:0", operator);
    }

    @SuppressWarnings("unchecked")
    private void clearGovernanceOperators() throws Exception {
        Field instanceField = PolarisOperators.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);

        Field mapField = PolarisOperators.class.getDeclaredField("polarisOperatorMap");
        mapField.setAccessible(true);
        java.util.Map<PolarisOperators.OperatorType, java.util.Map<String, PolarisOperator>> map =
                (java.util.Map<PolarisOperators.OperatorType, java.util.Map<String, PolarisOperator>>) mapField.get(
                        instance);
        map.get(PolarisOperators.OperatorType.GOVERNANCE).clear();
    }

    @Test
    public void testRouteWithNullInvokers() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        Invocation invocation = Mockito.mock(Invocation.class);

        List<Invoker<Object>> result = polarisRouter.route(null, url, invocation);
        Assert.assertNull(result);
    }

    @Test
    public void testRouteWithEmptyInvokers() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        Invocation invocation = Mockito.mock(Invocation.class);
        List<Invoker<Object>> invokers = new ArrayList<>();

        List<Invoker<Object>> result = polarisRouter.route(invokers, url, invocation);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testRouteWithNullOperator() throws Exception {
        // Create a router with null polarisOperator
        clearGovernanceOperators();
        URL routerUrl = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        PolarisRouter routerWithNullOp = new PolarisRouter(routerUrl);

        Invocation invocation = Mockito.mock(Invocation.class);

        @SuppressWarnings("unchecked")
        Invoker<Object> invoker = Mockito.mock(Invoker.class);
        Mockito.when(invoker.getUrl()).thenReturn(routerUrl);
        List<Invoker<Object>> invokers = Collections.singletonList(invoker);

        List<Invoker<Object>> result = routerWithNullOp.route(invokers, routerUrl, invocation);
        Assert.assertSame(invokers, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRouteNormalFlow() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        Invocation invocation = Mockito.mock(Invocation.class);
        Mockito.when(invocation.getMethodName()).thenReturn("sayHello");

        Invoker<Object> invoker = Mockito.mock(Invoker.class);
        Mockito.when(invoker.getUrl()).thenReturn(url);
        List<Invoker<Object>> invokers = new ArrayList<>();
        invokers.add(invoker);

        ServiceRule serviceRule = new ServiceRuleByProto();
        Mockito.when(mockOperator.getServiceRule(anyString(), eq(EventType.ROUTING))).thenReturn(serviceRule);

        List<Instance> routedInstances = new ArrayList<>();
        Instance mockInstance = Mockito.mock(Instance.class);
        routedInstances.add(mockInstance);
        Mockito.when(mockOperator.route(anyString(), anyString(), anySet(), anyList())).thenReturn(routedInstances);

        List<Invoker<Object>> result = polarisRouter.route(invokers, url, invocation);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());

        Mockito.verify(mockOperator).getServiceRule("com.test.TestService", EventType.ROUTING);
        Mockito.verify(mockOperator).route(eq("com.test.TestService"), eq("sayHello"), anySet(), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRouteWithNearbyEnabled() {
        Mockito.when(mockConfig.isNearbyEnabled()).thenReturn(true);

        URL url = URL.valueOf("dubbo://127.0.0.1:20880/com.test.TestService");
        Invocation invocation = Mockito.mock(Invocation.class);
        Mockito.when(invocation.getMethodName()).thenReturn("sayHello");

        Invoker<Object> invoker = Mockito.mock(Invoker.class);
        Mockito.when(invoker.getUrl()).thenReturn(url);
        List<Invoker<Object>> invokers = new ArrayList<>();
        invokers.add(invoker);

        ServiceRule serviceRule = new ServiceRuleByProto();
        Mockito.when(mockOperator.getServiceRule(anyString(), eq(EventType.ROUTING))).thenReturn(serviceRule);

        List<Instance> routedInstances = new ArrayList<>();
        Instance mockInstance = Mockito.mock(Instance.class);
        routedInstances.add(mockInstance);
        Mockito.when(mockOperator.route(anyString(), anyString(), anySet(), anyList())).thenReturn(routedInstances);

        List<Invoker<Object>> result = polarisRouter.route(invokers, url, invocation);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());

        // Verify route was called (nearby metadata is set internally before route call)
        Mockito.verify(mockOperator).route(eq("com.test.TestService"), eq("sayHello"), anySet(), anyList());
    }
}
