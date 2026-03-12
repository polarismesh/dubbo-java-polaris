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

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.pojo.DefaultInstance;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.Node;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.metadata.StaticMetadataManager;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.ratelimit.factory.LimitAPIFactory;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.test.common.TestUtils;
import com.tencent.polaris.test.mock.discovery.NamingServer;
import com.tencent.polaris.test.mock.discovery.NamingService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.rpc.cluster.Constants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PolarisRegistryTest {

    private static final String TEST_NAMESPACE = "default";
    private static final String TEST_SERVICE = "com.test.TestService";

    private NamingServer namingServer;
    private PolarisOperator polarisOperator;
    private PolarisRegistry polarisRegistry;

    @Before
    public void before() throws Exception {
        namingServer = NamingServer.startNamingServer(-1);
        int port = namingServer.getPort();

        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        namingServer.getNamingService().addService(serviceKey);

        // Create a PolarisOperator backed by the mock server
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

        // Inject operator into PolarisOperators and reset StaticMetadataManager
        resetStaticMetadataManager();
        injectGovernanceOperator(polarisOperator);

        // Create PolarisRegistry using a mock approach (bypass constructor's PolarisOperators call)
        URL registryUrl = new URL("polaris", "127.0.0.1", port, new HashMap<>());
        polarisRegistry = createPolarisRegistry(registryUrl, polarisOperator, sdkContext);
    }

    @After
    public void after() throws Exception {
        if (polarisRegistry != null) {
            try {
                // Use reflection to set destroyed=true to avoid double destroy
                Field destroyedField = PolarisRegistry.class.getDeclaredField("destroyed");
                destroyedField.setAccessible(true);
                ((java.util.concurrent.atomic.AtomicBoolean) destroyedField.get(polarisRegistry)).set(true);
            } catch (Exception ignored) {
            }
        }
        clearGovernanceOperators();
        resetStaticMetadataManager();
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

    private PolarisRegistry createPolarisRegistry(URL url, PolarisOperator operator, SDKContext sdkContext) throws Exception {
        // We need to mock the registry because its constructor calls PolarisOperators.loadOrStoreForGovernance
        // and StaticMetadataManager.getOrCreate. We'll use a mock with CALLS_REAL_METHODS.
        PolarisRegistry registry = Mockito.mock(PolarisRegistry.class, Mockito.withSettings()
                .useConstructor(url)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        // Override the polarisOperator field to use our test operator
        Field opField = PolarisRegistry.class.getDeclaredField("polarisOperator");
        opField.setAccessible(true);
        opField.set(registry, operator);

        return registry;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PolarisOperator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private void injectGovernanceOperator(PolarisOperator operator) throws Exception {
        Field instanceField = PolarisOperators.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);

        Field mapField = PolarisOperators.class.getDeclaredField("polarisOperatorMap");
        mapField.setAccessible(true);
        Map<PolarisOperators.OperatorType, Map<String, PolarisOperator>> map =
                (Map<PolarisOperators.OperatorType, Map<String, PolarisOperator>>) mapField.get(instance);
        map.get(PolarisOperators.OperatorType.GOVERNANCE).put("127.0.0.1:" + namingServer.getPort(), operator);
    }

    @SuppressWarnings("unchecked")
    private void clearGovernanceOperators() throws Exception {
        Field instanceField = PolarisOperators.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);

        Field mapField = PolarisOperators.class.getDeclaredField("polarisOperatorMap");
        mapField.setAccessible(true);
        Map<PolarisOperators.OperatorType, Map<String, PolarisOperator>> map =
                (Map<PolarisOperators.OperatorType, Map<String, PolarisOperator>>) mapField.get(instance);
        map.get(PolarisOperators.OperatorType.GOVERNANCE).clear();
    }

    private void resetStaticMetadataManager() throws Exception {
        Field instanceField = StaticMetadataManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    public void testDoRegisterNormal() {
        URL url = new URL("dubbo", "127.0.0.1", 20880, TEST_SERVICE,
                new HashMap<String, String>() {{
                    put(CommonConstants.PATH_KEY, TEST_SERVICE);
                    put(Constants.WEIGHT_KEY, "100");
                }});

        // Should not throw
        polarisRegistry.doRegister(url);
    }

    @Test(expected = PolarisException.class)
    public void testDoRegisterZeroPort() {
        URL url = new URL("dubbo", "127.0.0.1", 0, TEST_SERVICE);
        polarisRegistry.doRegister(url);
    }

    @Test
    public void testDoRegisterConsumerSkip() {
        URL url = new URL("consumer", "127.0.0.1", 20880, TEST_SERVICE);
        // Consumer protocol should be skipped - no exception
        polarisRegistry.doRegister(url);
    }

    @Test
    public void testDoUnregisterNormal() {
        URL url = new URL("dubbo", "127.0.0.1", 20880, TEST_SERVICE,
                new HashMap<String, String>() {{
                    put(CommonConstants.PATH_KEY, TEST_SERVICE);
                    put(Constants.WEIGHT_KEY, "100");
                }});

        polarisRegistry.doRegister(url);
        // Should not throw
        polarisRegistry.doUnregister(url);
    }

    @Test
    public void testDoUnregisterConsumerSkip() {
        URL url = new URL("consumer", "127.0.0.1", 20880, TEST_SERVICE);
        // Consumer protocol should be skipped
        polarisRegistry.doUnregister(url);
    }

    @Test
    public void testDoSubscribe() {
        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        Node node = new Node("10.0.0.1", 8080);
        NamingService.InstanceParameter param = new NamingService.InstanceParameter();
        param.setHealthy(true);
        param.setIsolated(false);
        param.setWeight(100);
        param.setProtocol("dubbo");
        namingServer.getNamingService().addInstance(serviceKey, node, param);

        URL url = URL.valueOf("dubbo://127.0.0.1:20880/" + TEST_SERVICE);
        NotifyListener listener = Mockito.mock(NotifyListener.class);

        polarisRegistry.doSubscribe(url, listener);
        // Verify listener was notified
        Mockito.verify(listener, Mockito.atLeastOnce()).notify(Mockito.anyList());
    }

    @Test
    public void testDoUnsubscribe() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/" + TEST_SERVICE);
        NotifyListener listener = Mockito.mock(NotifyListener.class);

        // Subscribe first
        polarisRegistry.doSubscribe(url, listener);
        // Unsubscribe should not throw
        polarisRegistry.doUnsubscribe(url, listener);
    }

    @Test
    public void testIsAvailable() {
        Assert.assertTrue(polarisRegistry.isAvailable());
    }
}
