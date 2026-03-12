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

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.Node;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.ratelimit.factory.LimitAPIFactory;
import com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.test.common.TestUtils;
import com.tencent.polaris.test.mock.discovery.NamingServer;
import com.tencent.polaris.test.mock.discovery.NamingService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Integration test for PolarisOperator using NamingServer mock.
 */
public class PolarisOperatorIntegrationTest {

    private static final String TEST_NAMESPACE = "default";
    private static final String TEST_SERVICE = "integration-test-service";
    private static final String TEST_HOST = "127.0.0.1";
    private static final int TEST_INSTANCE_PORT = 20880;

    private NamingServer namingServer;
    private PolarisOperator polarisOperator;

    @Before
    public void before() throws IOException {
        namingServer = NamingServer.startNamingServer(-1);
        int port = namingServer.getPort();

        // Register our test service in the mock server
        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        namingServer.getNamingService().addService(serviceKey);

        // Create a PolarisOperator backed by the mock server using reflection
        Configuration configuration = TestUtils.createSimpleConfiguration(port);
        SDKContext sdkContext = SDKContext.initContextByConfig(configuration);
        ConsumerAPI consumerAPI = DiscoveryAPIFactory.createConsumerAPIByContext(sdkContext);
        ProviderAPI providerAPI = DiscoveryAPIFactory.createProviderAPIByContext(sdkContext);
        LimitAPI limitAPI = LimitAPIFactory.createLimitAPIByContext(sdkContext);
        RouterAPI routerAPI = RouterAPIFactory.createRouterAPIByContext(sdkContext);
        CircuitBreakAPI circuitBreakAPI = CircuitBreakAPIFactory.createCircuitBreakAPIByContext(sdkContext);

        PolarisConfig mockConfig = Mockito.mock(PolarisConfig.class);
        Mockito.when(mockConfig.getNamespace()).thenReturn(TEST_NAMESPACE);
        Mockito.when(mockConfig.getTtl()).thenReturn(5);
        Mockito.when(mockConfig.getToken()).thenReturn("");

        polarisOperator = Mockito.mock(PolarisOperator.class, Mockito.CALLS_REAL_METHODS);
        setField(polarisOperator, "polarisConfig", mockConfig);
        setField(polarisOperator, "sdkContext", sdkContext);
        setField(polarisOperator, "consumerAPI", consumerAPI);
        setField(polarisOperator, "providerAPI", providerAPI);
        setField(polarisOperator, "limitAPI", limitAPI);
        setField(polarisOperator, "routerAPI", routerAPI);
        setField(polarisOperator, "circuitBreakAPI", circuitBreakAPI);
    }

    @After
    public void after() {
        if (polarisOperator != null) {
            try {
                polarisOperator.destroy();
            } catch (Exception ignored) {
            }
        }
        if (namingServer != null) {
            namingServer.terminate();
        }
    }

    private void setField(Object target, String fieldName, Object value) throws IOException {
        try {
            Field field = PolarisOperator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IOException("Failed to set field " + fieldName, e);
        }
    }

    @Test
    public void testRegisterAndDeregister() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("env", "test");

        polarisOperator.register(TEST_SERVICE, TEST_HOST, TEST_INSTANCE_PORT, "dubbo", "1.0.0", 100, metadata);
        // No exception means success
        polarisOperator.deregister(TEST_SERVICE, TEST_HOST, TEST_INSTANCE_PORT);
    }

    @Test
    public void testGetAvailableInstances() {
        // Add instance to mock server
        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        Node node = new Node("10.0.0.1", 8080);
        NamingService.InstanceParameter param = new NamingService.InstanceParameter();
        param.setHealthy(true);
        param.setIsolated(false);
        param.setWeight(100);
        param.setProtocol("dubbo");
        namingServer.getNamingService().addInstance(serviceKey, node, param);

        Instance[] instances = polarisOperator.getAvailableInstances(TEST_SERVICE, true);
        Assert.assertNotNull(instances);
        Assert.assertTrue(instances.length > 0);

        boolean found = false;
        for (Instance inst : instances) {
            if ("10.0.0.1".equals(inst.getHost()) && inst.getPort() == 8080) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("Expected instance not found", found);
    }

    @Test
    public void testGetAvailableInstancesMultiple() {
        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);

        for (int i = 1; i <= 3; i++) {
            Node node = new Node("10.0.0." + i, 8080 + i);
            NamingService.InstanceParameter param = new NamingService.InstanceParameter();
            param.setHealthy(true);
            param.setIsolated(false);
            param.setWeight(100);
            param.setProtocol("dubbo");
            namingServer.getNamingService().addInstance(serviceKey, node, param);
        }

        Instance[] instances = polarisOperator.getAvailableInstances(TEST_SERVICE, true);
        Assert.assertNotNull(instances);
        Assert.assertTrue("Expected at least 3 instances", instances.length >= 3);
    }

    @Test
    public void testGetServiceRuleReturnsNonNull() {
        ServiceRule rule = polarisOperator.getServiceRule(TEST_SERVICE, EventType.ROUTING);
        Assert.assertNotNull(rule);
    }

    @Test
    public void testGetServiceRuleBlankService() {
        ServiceRule rule = polarisOperator.getServiceRule("", EventType.ROUTING);
        Assert.assertNotNull(rule);
    }

    @Test
    public void testGetServices() {
        // Add another service to the mock server
        ServiceKey anotherService = new ServiceKey(TEST_NAMESPACE, "another-service");
        namingServer.getNamingService().addService(anotherService);

        List<ServiceInfo> services = polarisOperator.getServices();
        Assert.assertNotNull(services);
        // The mock server should have at least our test services plus system services
        Assert.assertTrue("Expected services list not empty", services.size() > 0);
    }

    @Test
    public void testRegisterWithMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "2.0.0");
        metadata.put("region", "cn-north");
        metadata.put("custom-key", "custom-val");

        // Should not throw
        polarisOperator.register(TEST_SERVICE, TEST_HOST, 30880, "dubbo", "2.0.0", 200, metadata);
        polarisOperator.deregister(TEST_SERVICE, TEST_HOST, 30880);
    }
}
