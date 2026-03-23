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

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.Node;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.metadata.MetadataContextHolder;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;
import com.tencent.polaris.plugins.router.nearby.NearbyRouter;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.ratelimit.factory.LimitAPIFactory;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.specification.api.v1.traffic.manage.RoutingProto;
import com.tencent.polaris.test.common.TestUtils;
import com.tencent.polaris.test.mock.discovery.LocationInfo;
import com.tencent.polaris.test.mock.discovery.NamingServer;
import com.tencent.polaris.test.mock.discovery.NamingService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Integration test for nearby routing using polaris-test NamingServer mock.
 * Registers instances with different locations and verifies that nearby routing
 * correctly filters instances based on caller location.
 */
public class NearbyRouteIntegrationTest {

    private static final String TEST_NAMESPACE = "default";
    private static final String TEST_SERVICE = "nearby-test-service";

    private NamingServer namingServer;
    private PolarisOperator polarisOperator;
    private SDKContext sdkContext;

    @Before
    public void before() throws Exception {
        namingServer = NamingServer.startNamingServer(-1);
        int port = namingServer.getPort();

        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        namingServer.getNamingService().addService(serviceKey);

        Configuration configuration = TestUtils.createSimpleConfiguration(port);
        sdkContext = SDKContext.initContextByConfig(configuration);
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
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.remove();
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PolarisOperator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setCallerLocation(String region, String zone, String campus) {
        sdkContext.getValueContext().setValue(
                RoutingProto.NearbyRoutingConfig.LocationLevel.REGION.name(), region);
        sdkContext.getValueContext().setValue(
                RoutingProto.NearbyRoutingConfig.LocationLevel.ZONE.name(), zone);
        sdkContext.getValueContext().setValue(
                RoutingProto.NearbyRoutingConfig.LocationLevel.CAMPUS.name(), campus);
        sdkContext.getValueContext().notifyAllForLocationReady();
    }

    private void enableNearbyRouter() {
        MetadataContextHolder.get()
                .getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataMapValue(NearbyRouter.ROUTER_TYPE_NEAR_BY,
                        NearbyRouter.ROUTER_ENABLED, "true", TransitiveType.NONE);
    }

    private void addInstance(String host, int port, String region, String zone, String campus) {
        ServiceKey serviceKey = new ServiceKey(TEST_NAMESPACE, TEST_SERVICE);
        Node node = new Node(host, port);
        NamingService.InstanceParameter param = new NamingService.InstanceParameter();
        param.setHealthy(true);
        param.setIsolated(false);
        param.setWeight(100);
        param.setProtocol("dubbo");
        param.setLocationInfo(new LocationInfo(region, zone, campus));
        namingServer.getNamingService().addInstance(serviceKey, node, param);
    }

    private List<Instance> getInstances() {
        Instance[] instances = polarisOperator.getAvailableInstances(TEST_SERVICE, true);
        List<Instance> list = new ArrayList<>();
        Collections.addAll(list, instances);
        return list;
    }

    /**
     * Test: caller in zone-a, instances in zone-a/zone-b/zone-c.
     * Default matchLevel=ZONE, so only zone-a instances should be returned.
     */
    @Test
    public void testNearbyRouteSameZone() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");
        addInstance("10.0.1.3", 8083, "cn-south", "zone-c", "campus-3");

        setCallerLocation("cn-north", "zone-a", "campus-1");
        enableNearbyRouter();

        List<Instance> allInstances = getInstances();
        Assert.assertEquals(3, allInstances.size());

        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(1, routed.size());
        Assert.assertEquals("10.0.1.1", routed.get(0).getHost());
        Assert.assertEquals(8081, routed.get(0).getPort());
    }

    /**
     * Test: caller in zone-b, only zone-b instance should be returned.
     */
    @Test
    public void testNearbyRouteDifferentCallerZone() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");
        addInstance("10.0.1.3", 8083, "cn-south", "zone-c", "campus-3");

        setCallerLocation("cn-north", "zone-b", "campus-2");
        enableNearbyRouter();

        List<Instance> allInstances = getInstances();
        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(1, routed.size());
        Assert.assertEquals("10.0.1.2", routed.get(0).getHost());
        Assert.assertEquals(8082, routed.get(0).getPort());
    }

    /**
     * Test: caller in zone-x (no instances in this zone), should degrade to REGION level.
     * Since caller region is cn-north, instances in cn-north (zone-a, zone-b) should be returned.
     */
    @Test
    public void testNearbyRouteDegradeToRegion() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");
        addInstance("10.0.1.3", 8083, "cn-south", "zone-c", "campus-3");

        setCallerLocation("cn-north", "zone-x", "campus-x");
        enableNearbyRouter();

        List<Instance> allInstances = getInstances();
        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(2, routed.size());
        for (Instance inst : routed) {
            Assert.assertTrue("Expected cn-north instances only",
                    "zone-a".equals(inst.getZone()) || "zone-b".equals(inst.getZone()));
        }
    }

    /**
     * Test: caller in a completely different region with no matching zone or region,
     * should degrade to ALL level and return all instances.
     */
    @Test
    public void testNearbyRouteDegradeToAll() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");

        setCallerLocation("eu-west", "zone-eu", "campus-eu");
        enableNearbyRouter();

        List<Instance> allInstances = getInstances();
        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(2, routed.size());
    }

    /**
     * Test: multiple instances in same zone as caller, all should be returned.
     */
    @Test
    public void testNearbyRouteMultipleInSameZone() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.4", 8084, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.5", 8085, "cn-north", "zone-a", "campus-2");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");
        addInstance("10.0.1.3", 8083, "cn-south", "zone-c", "campus-3");

        setCallerLocation("cn-north", "zone-a", "campus-1");
        enableNearbyRouter();

        List<Instance> allInstances = getInstances();
        Assert.assertEquals(5, allInstances.size());

        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(3, routed.size());
        for (Instance inst : routed) {
            Assert.assertEquals("zone-a", inst.getZone());
        }
    }

    /**
     * Test: without enabling nearby router, all instances should be returned (no filtering).
     */
    @Test
    public void testRouteWithoutNearbyEnabled() {
        addInstance("10.0.1.1", 8081, "cn-north", "zone-a", "campus-1");
        addInstance("10.0.1.2", 8082, "cn-north", "zone-b", "campus-2");
        addInstance("10.0.1.3", 8083, "cn-south", "zone-c", "campus-3");

        setCallerLocation("cn-north", "zone-a", "campus-1");
        // Do NOT enable nearby router

        List<Instance> allInstances = getInstances();
        List<Instance> routed = polarisOperator.route(TEST_SERVICE, "testMethod",
                new HashSet<>(), allInstances);

        Assert.assertNotNull(routed);
        Assert.assertEquals(3, routed.size());
    }
}
