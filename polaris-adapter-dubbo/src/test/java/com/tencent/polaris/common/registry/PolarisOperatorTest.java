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

import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.ErrorCode;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.plugin.common.ValueContext;
import com.tencent.polaris.api.pojo.CircuitBreakerStatus;
import com.tencent.polaris.api.pojo.DefaultServiceInstances;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.pojo.RouteArgument;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.rpc.GetHealthyInstancesRequest;
import com.tencent.polaris.api.rpc.GetServiceRuleRequest;
import com.tencent.polaris.api.rpc.GetServicesRequest;
import com.tencent.polaris.api.rpc.InstanceDeregisterRequest;
import com.tencent.polaris.api.rpc.InstanceRegisterRequest;
import com.tencent.polaris.api.rpc.InstanceRegisterResponse;
import com.tencent.polaris.api.rpc.InstancesResponse;
import com.tencent.polaris.api.rpc.ServiceCallResult;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.api.rpc.ServicesResponse;
import com.tencent.polaris.api.rpc.UnWatchServiceRequest;
import com.tencent.polaris.api.rpc.WatchServiceRequest;
import com.tencent.polaris.api.rpc.WatchServiceResponse;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.api.flow.CircuitBreakerFlow;
import com.tencent.polaris.circuitbreak.api.pojo.CheckResult;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.ServiceRuleByProto;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaRequest;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResultCode;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.router.api.rpc.ProcessLoadBalanceRequest;
import com.tencent.polaris.router.api.rpc.ProcessLoadBalanceResponse;
import com.tencent.polaris.router.api.rpc.ProcessRoutersRequest;
import com.tencent.polaris.router.api.rpc.ProcessRoutersResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PolarisOperator 单元测试类
 *
 * @author dubbo-polaris
 * @date 2026-02-28
 */
public class PolarisOperatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisOperatorTest.class);

    private static final String TEST_NAMESPACE = "default";
    private static final String TEST_SERVICE = "test-service";
    private static final String TEST_HOST = "127.0.0.1";
    private static final int TEST_PORT = 20880;
    private static final String TEST_TOKEN = "test-token";

    private PolarisOperator polarisOperator;

    private ConsumerAPI mockConsumerAPI;
    private ProviderAPI mockProviderAPI;
    private LimitAPI mockLimitAPI;
    private RouterAPI mockRouterAPI;
    private CircuitBreakAPI mockCircuitBreakAPI;
    private ConfigFileService mockConfigFileAPI;
    private ConfigFilePublishService mockConfigFilePublishAPI;
    private SDKContext mockSdkContext;
    private PolarisConfig mockPolarisConfig;

    @Before
    public void before() throws Exception {
        // 创建 mock 对象
        mockConsumerAPI = Mockito.mock(ConsumerAPI.class);
        mockProviderAPI = Mockito.mock(ProviderAPI.class);
        mockLimitAPI = Mockito.mock(LimitAPI.class);
        mockRouterAPI = Mockito.mock(RouterAPI.class);
        mockCircuitBreakAPI = Mockito.mock(CircuitBreakAPI.class);
        mockConfigFileAPI = Mockito.mock(ConfigFileService.class);
        mockConfigFilePublishAPI = Mockito.mock(ConfigFilePublishService.class);
        mockSdkContext = Mockito.mock(SDKContext.class);
        mockPolarisConfig = Mockito.mock(PolarisConfig.class);

        // 设置 PolarisConfig 默认行为
        Mockito.when(mockPolarisConfig.getNamespace()).thenReturn(TEST_NAMESPACE);
        Mockito.when(mockPolarisConfig.getTtl()).thenReturn(5);
        Mockito.when(mockPolarisConfig.getToken()).thenReturn(TEST_TOKEN);

        // 通过 Mockito 创建 PolarisOperator 实例并注入 mock 字段
        polarisOperator = Mockito.mock(PolarisOperator.class, Mockito.CALLS_REAL_METHODS);
        setField(polarisOperator, "polarisConfig", mockPolarisConfig);
        setField(polarisOperator, "sdkContext", mockSdkContext);
        setField(polarisOperator, "consumerAPI", mockConsumerAPI);
        setField(polarisOperator, "providerAPI", mockProviderAPI);
        setField(polarisOperator, "limitAPI", mockLimitAPI);
        setField(polarisOperator, "routerAPI", mockRouterAPI);
        setField(polarisOperator, "circuitBreakAPI", mockCircuitBreakAPI);
        setField(polarisOperator, "configFileAPI", mockConfigFileAPI);
        setField(polarisOperator, "configFilePublishAPI", mockConfigFilePublishAPI);
    }

    @After
    public void after() {
        // 清理资源
    }

    /**
     * 通过反射设置私有字段
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PolarisOperator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ==================== formatCode 测试 ====================

    /**
     * 测试 formatCode 静态方法
     */
    @Test
    public void testFormatCode() {
        String result = PolarisOperator.formatCode("12345");
        Assert.assertEquals("POLARIS:12345", result);

        String resultInt = PolarisOperator.formatCode(500);
        Assert.assertEquals("POLARIS:500", resultInt);
    }

    // ==================== register 测试 ====================

    /**
     * 测试正常服务注册
     */
    @Test
    public void testRegister() {
        // Arrange
        String protocol = "dubbo";
        String version = "1.0.0";
        int weight = 100;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("env", "test");

        InstanceRegisterResponse mockResponse = Mockito.mock(InstanceRegisterResponse.class);
        Mockito.when(mockProviderAPI.registerInstance(Mockito.any(InstanceRegisterRequest.class)))
                .thenReturn(mockResponse);

        // Act
        polarisOperator.register(TEST_SERVICE, TEST_HOST, TEST_PORT, protocol, version, weight, metadata);

        // Assert
        ArgumentCaptor<InstanceRegisterRequest> captor = ArgumentCaptor.forClass(InstanceRegisterRequest.class);
        Mockito.verify(mockProviderAPI).registerInstance(captor.capture());

        InstanceRegisterRequest capturedRequest = captor.getValue();
        Assert.assertEquals(TEST_NAMESPACE, capturedRequest.getNamespace());
        Assert.assertEquals(TEST_SERVICE, capturedRequest.getService());
        Assert.assertEquals(TEST_HOST, capturedRequest.getHost());
        Assert.assertEquals(TEST_PORT, (int) capturedRequest.getPort());
        Assert.assertEquals(weight, (int) capturedRequest.getWeight());
        Assert.assertEquals(version, capturedRequest.getVersion());
        Assert.assertEquals(protocol, capturedRequest.getProtocol());
        Assert.assertEquals(TEST_TOKEN, capturedRequest.getToken());
        Assert.assertEquals(5, (int) capturedRequest.getTtl());
        Assert.assertEquals(metadata, capturedRequest.getMetadata());

        LOG.info("[Test] 服务注册测试通过");
    }

    /**
     * 测试服务注册：metadata 为空
     */
    @Test
    public void testRegister_withNullMetadata() {
        // Arrange
        InstanceRegisterResponse mockResponse = Mockito.mock(InstanceRegisterResponse.class);
        Mockito.when(mockProviderAPI.registerInstance(Mockito.any(InstanceRegisterRequest.class)))
                .thenReturn(mockResponse);

        // Act
        polarisOperator.register(TEST_SERVICE, TEST_HOST, TEST_PORT, "dubbo", "1.0.0", 100, null);

        // Assert
        ArgumentCaptor<InstanceRegisterRequest> captor = ArgumentCaptor.forClass(InstanceRegisterRequest.class);
        Mockito.verify(mockProviderAPI).registerInstance(captor.capture());
        Assert.assertNull(captor.getValue().getMetadata());
    }

    // ==================== deregister 测试 ====================

    /**
     * 测试正常服务反注册
     */
    @Test
    public void testDeregister() {
        // Act
        polarisOperator.deregister(TEST_SERVICE, TEST_HOST, TEST_PORT);

        // Assert
        ArgumentCaptor<InstanceDeregisterRequest> captor = ArgumentCaptor.forClass(InstanceDeregisterRequest.class);
        Mockito.verify(mockProviderAPI).deRegister(captor.capture());

        InstanceDeregisterRequest capturedRequest = captor.getValue();
        Assert.assertEquals(TEST_NAMESPACE, capturedRequest.getNamespace());
        Assert.assertEquals(TEST_SERVICE, capturedRequest.getService());
        Assert.assertEquals(TEST_HOST, capturedRequest.getHost());
        Assert.assertEquals(TEST_PORT, (int) capturedRequest.getPort());
        Assert.assertEquals(TEST_TOKEN, capturedRequest.getToken());

        LOG.info("[Test] 服务反注册测试通过");
    }

    // ==================== watchService 测试 ====================

    /**
     * 测试订阅服务变化：成功场景
     */
    @Test
    public void testWatchService_success() {
        // Arrange
        ServiceListener mockListener = Mockito.mock(ServiceListener.class);
        WatchServiceResponse mockResponse = Mockito.mock(WatchServiceResponse.class);
        Mockito.when(mockResponse.isSuccess()).thenReturn(true);
        Mockito.when(mockConsumerAPI.watchService(Mockito.any(WatchServiceRequest.class)))
                .thenReturn(mockResponse);

        // Act
        boolean result = polarisOperator.watchService(TEST_SERVICE, mockListener);

        // Assert
        Assert.assertTrue(result);
        Mockito.verify(mockConsumerAPI).watchService(Mockito.any(WatchServiceRequest.class));
    }

    /**
     * 测试订阅服务变化：失败场景
     */
    @Test
    public void testWatchService_failure() {
        // Arrange
        ServiceListener mockListener = Mockito.mock(ServiceListener.class);
        WatchServiceResponse mockResponse = Mockito.mock(WatchServiceResponse.class);
        Mockito.when(mockResponse.isSuccess()).thenReturn(false);
        Mockito.when(mockConsumerAPI.watchService(Mockito.any(WatchServiceRequest.class)))
                .thenReturn(mockResponse);

        // Act
        boolean result = polarisOperator.watchService(TEST_SERVICE, mockListener);

        // Assert
        Assert.assertFalse(result);
    }

    // ==================== unwatchService 测试 ====================

    /**
     * 测试取消订阅服务变化
     */
    @Test
    public void testUnwatchService() {
        // Arrange
        ServiceListener mockListener = Mockito.mock(ServiceListener.class);

        // Act
        polarisOperator.unwatchService(TEST_SERVICE, mockListener);

        // Assert
        Mockito.verify(mockConsumerAPI).unWatchService(Mockito.any(UnWatchServiceRequest.class));
    }

    // ==================== getAvailableInstances 测试 ====================

    /**
     * 测试获取可用实例：正常场景
     */
    @Test
    public void testGetAvailableInstances() {
        // Arrange
        Instance mockInstance1 = Mockito.mock(Instance.class);
        Instance mockInstance2 = Mockito.mock(Instance.class);
        Instance[] expectedInstances = new Instance[]{mockInstance1, mockInstance2};

        InstancesResponse mockResponse = Mockito.mock(InstancesResponse.class);
        Mockito.when(mockResponse.getInstances()).thenReturn(expectedInstances);
        Mockito.when(mockConsumerAPI.getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Instance[] result = polarisOperator.getAvailableInstances(TEST_SERVICE, false);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.length);
        Assert.assertSame(mockInstance1, result[0]);
        Assert.assertSame(mockInstance2, result[1]);

        ArgumentCaptor<GetHealthyInstancesRequest> captor = ArgumentCaptor.forClass(GetHealthyInstancesRequest.class);
        Mockito.verify(mockConsumerAPI).getHealthyInstances(captor.capture());
        Assert.assertEquals(TEST_NAMESPACE, captor.getValue().getNamespace());
        Assert.assertEquals(TEST_SERVICE, captor.getValue().getService());
        Assert.assertEquals(Boolean.FALSE, captor.getValue().getIncludeCircuitBreakInstances());
    }

    /**
     * 测试获取可用实例：包含熔断实例
     */
    @Test
    public void testGetAvailableInstances_includeCircuitBreakInstances() {
        // Arrange
        Instance[] expectedInstances = new Instance[]{};
        InstancesResponse mockResponse = Mockito.mock(InstancesResponse.class);
        Mockito.when(mockResponse.getInstances()).thenReturn(expectedInstances);
        Mockito.when(mockConsumerAPI.getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Instance[] result = polarisOperator.getAvailableInstances(TEST_SERVICE, true);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);

        ArgumentCaptor<GetHealthyInstancesRequest> captor = ArgumentCaptor.forClass(GetHealthyInstancesRequest.class);
        Mockito.verify(mockConsumerAPI).getHealthyInstances(captor.capture());
        Assert.assertEquals(Boolean.TRUE, captor.getValue().getIncludeCircuitBreakInstances());
    }

    // ==================== reportInvokeResult 测试 ====================

    /**
     * 测试上报调用结果：正常场景
     */
    @Test
    public void testReportInvokeResult() {
        // Arrange
        String method = "sayHello";
        String callerIp = "10.0.0.1";
        long delay = 100L;
        int code = 200;

        // Act
        polarisOperator.reportInvokeResult(TEST_SERVICE, method, TEST_HOST, TEST_PORT,
                callerIp, delay, RetStatus.RetSuccess, code);

        // Assert
        ArgumentCaptor<ServiceCallResult> captor = ArgumentCaptor.forClass(ServiceCallResult.class);
        Mockito.verify(mockConsumerAPI).updateServiceCallResult(captor.capture());

        ServiceCallResult capturedResult = captor.getValue();
        Assert.assertEquals(TEST_NAMESPACE, capturedResult.getNamespace());
        Assert.assertEquals(TEST_SERVICE, capturedResult.getService());
        Assert.assertEquals(method, capturedResult.getMethod());
        Assert.assertEquals(TEST_HOST, capturedResult.getHost());
        Assert.assertEquals(TEST_PORT, (int) capturedResult.getPort());
        Assert.assertEquals(delay, (long) capturedResult.getDelay());
        Assert.assertEquals(RetStatus.RetSuccess, capturedResult.getRetStatus());
        Assert.assertEquals(code, (int) capturedResult.getRetCode());
        Assert.assertEquals(callerIp, capturedResult.getCallerIp());

        LOG.info("[Test] 上报调用结果测试通过");
    }

    /**
     * 测试上报调用结果：失败场景（PolarisException）
     */
    @Test
    public void testReportInvokeResult_withException() {
        // Arrange
        Mockito.doThrow(new PolarisException(ErrorCode.API_TIMEOUT, "timeout"))
                .when(mockConsumerAPI).updateServiceCallResult(Mockito.any(ServiceCallResult.class));

        // Act - 不应抛出异常，内部会捕获
        polarisOperator.reportInvokeResult(TEST_SERVICE, "sayHello", TEST_HOST, TEST_PORT,
                "10.0.0.1", 100L, RetStatus.RetFail, 500);

        // Assert - 方法不抛出异常即通过
        Mockito.verify(mockConsumerAPI).updateServiceCallResult(Mockito.any(ServiceCallResult.class));
        LOG.info("[Test] 上报调用结果异常处理测试通过");
    }

    // ==================== route 测试 ====================

    /**
     * 测试路由：正常场景
     */
    @Test
    public void testRoute() {
        // Arrange
        String method = "sayHello";
        Set<RouteArgument> arguments = new HashSet<>();

        Instance mockInstance = Mockito.mock(Instance.class);
        List<Instance> inputInstances = Collections.singletonList(mockInstance);

        Instance mockRoutedInstance = Mockito.mock(Instance.class);
        List<Instance> routedInstances = Collections.singletonList(mockRoutedInstance);

        DefaultServiceInstances mockServiceInstances = Mockito.mock(DefaultServiceInstances.class);
        Mockito.when(mockServiceInstances.getInstances()).thenReturn(routedInstances);

        ProcessRoutersResponse mockResponse = Mockito.mock(ProcessRoutersResponse.class);
        Mockito.when(mockResponse.getServiceInstances()).thenReturn(mockServiceInstances);
        Mockito.when(mockRouterAPI.processRouters(Mockito.any(ProcessRoutersRequest.class)))
                .thenReturn(mockResponse);

        // Act
        List<Instance> result = polarisOperator.route(TEST_SERVICE, method, arguments, inputInstances);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        Assert.assertSame(mockRoutedInstance, result.get(0));

        ArgumentCaptor<ProcessRoutersRequest> captor = ArgumentCaptor.forClass(ProcessRoutersRequest.class);
        Mockito.verify(mockRouterAPI).processRouters(captor.capture());
        Assert.assertEquals(method, captor.getValue().getMethod());
    }

    // ==================== loadBalance 测试 ====================

    /**
     * 测试负载均衡：正常场景
     */
    @Test
    public void testLoadBalance() {
        // Arrange
        String hashKey = "user-123";
        Instance mockInstance1 = Mockito.mock(Instance.class);
        Instance mockInstance2 = Mockito.mock(Instance.class);
        List<Instance> instances = Arrays.asList(mockInstance1, mockInstance2);

        ProcessLoadBalanceResponse mockResponse = Mockito.mock(ProcessLoadBalanceResponse.class);
        Mockito.when(mockResponse.getTargetInstance()).thenReturn(mockInstance1);
        Mockito.when(mockRouterAPI.processLoadBalance(Mockito.any(ProcessLoadBalanceRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Instance result = polarisOperator.loadBalance(TEST_SERVICE, hashKey, instances);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertSame(mockInstance1, result);

        Mockito.verify(mockRouterAPI).processLoadBalance(Mockito.any(ProcessLoadBalanceRequest.class));
    }

    /**
     * 测试负载均衡：hashKey 为 null
     */
    @Test
    public void testLoadBalance_withNullHashKey() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        List<Instance> instances = Collections.singletonList(mockInstance);

        ProcessLoadBalanceResponse mockResponse = Mockito.mock(ProcessLoadBalanceResponse.class);
        Mockito.when(mockResponse.getTargetInstance()).thenReturn(mockInstance);
        Mockito.when(mockRouterAPI.processLoadBalance(Mockito.any(ProcessLoadBalanceRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Instance result = polarisOperator.loadBalance(TEST_SERVICE, null, instances);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertSame(mockInstance, result);
    }

    // ==================== checkCircuitBreakerPassing 测试 ====================

    /**
     * 测试熔断检查：实例有熔断状态且状态为 OPEN
     */
    @Test
    public void testCheckCircuitBreakerPassing_instanceOpen() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        CircuitBreakerStatus mockStatus = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(mockStatus.getStatus()).thenReturn(CircuitBreakerStatus.Status.OPEN);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(mockStatus);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert - OPEN 状态应返回 false
        Assert.assertFalse(result);
    }

    /**
     * 测试熔断检查：实例有熔断状态且状态为 CLOSE
     */
    @Test
    public void testCheckCircuitBreakerPassing_instanceClosed() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        CircuitBreakerStatus mockStatus = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(mockStatus.getStatus()).thenReturn(CircuitBreakerStatus.Status.CLOSE);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(mockStatus);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert - CLOSE 状态应返回 true
        Assert.assertTrue(result);
    }

    /**
     * 测试熔断检查：实例有熔断状态且状态为 HALF_OPEN
     */
    @Test
    public void testCheckCircuitBreakerPassing_instanceHalfOpen() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        CircuitBreakerStatus mockStatus = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(mockStatus.getStatus()).thenReturn(CircuitBreakerStatus.Status.HALF_OPEN);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(mockStatus);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert - HALF_OPEN 状态应返回 true
        Assert.assertTrue(result);
    }

    /**
     * 测试熔断检查：实例无熔断状态，通过 CircuitBreakerFlow 检查通过
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_flowPass() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(mockInstance.getNamespace()).thenReturn(TEST_NAMESPACE);
        Mockito.when(mockInstance.getService()).thenReturn(TEST_SERVICE);
        Mockito.when(mockInstance.getHost()).thenReturn(TEST_HOST);
        Mockito.when(mockInstance.getPort()).thenReturn(TEST_PORT);

        ValueContext mockValueContext = Mockito.mock(ValueContext.class);
        CircuitBreakerFlow mockFlow = Mockito.mock(CircuitBreakerFlow.class);
        CheckResult mockCheckResult = new CheckResult(true, "", null);

        Mockito.when(mockSdkContext.getValueContext()).thenReturn(mockValueContext);
        Mockito.when(mockValueContext.getValue(CircuitBreakerFlow.class.getCanonicalName()))
                .thenReturn(mockFlow);
        Mockito.when(mockFlow.check(Mockito.any())).thenReturn(mockCheckResult);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert
        Assert.assertTrue(result);
    }

    /**
     * 测试熔断检查：实例无熔断状态，通过 CircuitBreakerFlow 检查不通过
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_flowNotPass() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(mockInstance.getNamespace()).thenReturn(TEST_NAMESPACE);
        Mockito.when(mockInstance.getService()).thenReturn(TEST_SERVICE);
        Mockito.when(mockInstance.getHost()).thenReturn(TEST_HOST);
        Mockito.when(mockInstance.getPort()).thenReturn(TEST_PORT);

        ValueContext mockValueContext = Mockito.mock(ValueContext.class);
        CircuitBreakerFlow mockFlow = Mockito.mock(CircuitBreakerFlow.class);
        CheckResult mockCheckResult = new CheckResult(false, "", null);

        Mockito.when(mockSdkContext.getValueContext()).thenReturn(mockValueContext);
        Mockito.when(mockValueContext.getValue(CircuitBreakerFlow.class.getCanonicalName()))
                .thenReturn(mockFlow);
        Mockito.when(mockFlow.check(Mockito.any())).thenReturn(mockCheckResult);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert
        Assert.assertFalse(result);
    }

    /**
     * 测试熔断检查：实例无熔断状态，CircuitBreakerFlow 为 null
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_noFlow() {
        // Arrange
        Instance mockInstance = Mockito.mock(Instance.class);
        Mockito.when(mockInstance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(mockInstance.getNamespace()).thenReturn(TEST_NAMESPACE);
        Mockito.when(mockInstance.getService()).thenReturn(TEST_SERVICE);
        Mockito.when(mockInstance.getHost()).thenReturn(TEST_HOST);
        Mockito.when(mockInstance.getPort()).thenReturn(TEST_PORT);

        ValueContext mockValueContext = Mockito.mock(ValueContext.class);
        Mockito.when(mockSdkContext.getValueContext()).thenReturn(mockValueContext);
        Mockito.when(mockValueContext.getValue(CircuitBreakerFlow.class.getCanonicalName()))
                .thenReturn(null);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(mockInstance);

        // Assert - 无 flow 应返回 true
        Assert.assertTrue(result);
    }

    // ==================== getQuota 测试 ====================

    /**
     * 测试限流：正常通过场景
     */
    @Test
    public void testGetQuota() {
        // Arrange
        String method = "sayHello";
        Set<Argument> arguments = new HashSet<>();

        QuotaResponse mockResponse = Mockito.mock(QuotaResponse.class);
        Mockito.when(mockResponse.getCode()).thenReturn(QuotaResultCode.QuotaResultOk);
        Mockito.when(mockLimitAPI.getQuota(Mockito.any(QuotaRequest.class)))
                .thenReturn(mockResponse);

        // Act
        QuotaResponse result = polarisOperator.getQuota(TEST_SERVICE, method, arguments);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(QuotaResultCode.QuotaResultOk, result.getCode());

        ArgumentCaptor<QuotaRequest> captor = ArgumentCaptor.forClass(QuotaRequest.class);
        Mockito.verify(mockLimitAPI).getQuota(captor.capture());
        Assert.assertEquals(TEST_NAMESPACE, captor.getValue().getNamespace());
        Assert.assertEquals(TEST_SERVICE, captor.getValue().getService());
        Assert.assertEquals(method, captor.getValue().getMethod());
        Assert.assertEquals(1, (int) captor.getValue().getCount());
    }

    /**
     * 测试限流：被限流场景
     */
    @Test
    public void testGetQuota_rateLimited() {
        // Arrange
        QuotaResponse mockResponse = Mockito.mock(QuotaResponse.class);
        Mockito.when(mockResponse.getCode()).thenReturn(QuotaResultCode.QuotaResultLimited);
        Mockito.when(mockLimitAPI.getQuota(Mockito.any(QuotaRequest.class)))
                .thenReturn(mockResponse);

        // Act
        QuotaResponse result = polarisOperator.getQuota(TEST_SERVICE, "sayHello", new HashSet<>());

        // Assert
        Assert.assertEquals(QuotaResultCode.QuotaResultLimited, result.getCode());
    }

    // ==================== getServiceRule 测试 ====================

    /**
     * 测试获取服务规则：正常场景
     */
    @Test
    public void testGetServiceRule() {
        // Arrange
        ServiceRule mockServiceRule = Mockito.mock(ServiceRule.class);
        ServiceRuleResponse mockResponse = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(mockResponse.getServiceRule()).thenReturn(mockServiceRule);
        Mockito.when(mockConsumerAPI.getServiceRule(Mockito.any(GetServiceRuleRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ServiceRule result = polarisOperator.getServiceRule(TEST_SERVICE, EventType.ROUTING);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertSame(mockServiceRule, result);

        ArgumentCaptor<GetServiceRuleRequest> captor = ArgumentCaptor.forClass(GetServiceRuleRequest.class);
        Mockito.verify(mockConsumerAPI).getServiceRule(captor.capture());
        Assert.assertEquals(TEST_NAMESPACE, captor.getValue().getNamespace());
        Assert.assertEquals(TEST_SERVICE, captor.getValue().getService());
        Assert.assertEquals(EventType.ROUTING, captor.getValue().getRuleType());
    }

    /**
     * 测试获取服务规则：service 为空白字符串时返回空 ServiceRuleByProto
     */
    @Test
    public void testGetServiceRule_blankService() {
        // Act
        ServiceRule result = polarisOperator.getServiceRule("", EventType.ROUTING);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof ServiceRuleByProto);

        // 不应调用 consumerAPI
        Mockito.verify(mockConsumerAPI, Mockito.never()).getServiceRule(Mockito.any());
    }

    /**
     * 测试获取服务规则：service 为 null 时返回空 ServiceRuleByProto
     */
    @Test
    public void testGetServiceRule_nullService() {
        // Act
        ServiceRule result = polarisOperator.getServiceRule(null, EventType.ROUTING);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof ServiceRuleByProto);
        Mockito.verify(mockConsumerAPI, Mockito.never()).getServiceRule(Mockito.any());
    }

    // ==================== getServices 测试 ====================

    /**
     * 测试获取服务列表：正常场景
     */
    @Test
    public void testGetServices() {
        // Arrange
        ServiceInfo serviceInfo1 = new ServiceInfo();
        ServiceInfo serviceInfo2 = new ServiceInfo();
        List<ServiceInfo> expectedServices = Arrays.asList(serviceInfo1, serviceInfo2);

        ServicesResponse mockResponse = Mockito.mock(ServicesResponse.class);
        Mockito.when(mockResponse.getServices()).thenReturn(expectedServices);
        Mockito.when(mockConsumerAPI.getServices(Mockito.any(GetServicesRequest.class)))
                .thenReturn(mockResponse);

        // Act
        List<ServiceInfo> result = polarisOperator.getServices();

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());

        ArgumentCaptor<GetServicesRequest> captor = ArgumentCaptor.forClass(GetServicesRequest.class);
        Mockito.verify(mockConsumerAPI).getServices(captor.capture());
        Assert.assertEquals(TEST_NAMESPACE, captor.getValue().getNamespace());
    }

    /**
     * 测试获取服务列表：空列表
     */
    @Test
    public void testGetServices_empty() {
        // Arrange
        ServicesResponse mockResponse = Mockito.mock(ServicesResponse.class);
        Mockito.when(mockResponse.getServices()).thenReturn(Collections.emptyList());
        Mockito.when(mockConsumerAPI.getServices(Mockito.any(GetServicesRequest.class)))
                .thenReturn(mockResponse);

        // Act
        List<ServiceInfo> result = polarisOperator.getServices();

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    // ==================== getter 方法测试 ====================

    /**
     * 测试 getPolarisConfig
     */
    @Test
    public void testGetPolarisConfig() {
        PolarisConfig config = polarisOperator.getPolarisConfig();
        Assert.assertSame(mockPolarisConfig, config);
    }

    /**
     * 测试 getSdkContext
     */
    @Test
    public void testGetSdkContext() {
        SDKContext context = polarisOperator.getSdkContext();
        Assert.assertSame(mockSdkContext, context);
    }

    /**
     * 测试 getConsumerAPI
     */
    @Test
    public void testGetConsumerAPI() {
        ConsumerAPI api = polarisOperator.getConsumerAPI();
        Assert.assertSame(mockConsumerAPI, api);
    }

    /**
     * 测试 getProviderAPI
     */
    @Test
    public void testGetProviderAPI() {
        ProviderAPI api = polarisOperator.getProviderAPI();
        Assert.assertSame(mockProviderAPI, api);
    }

    /**
     * 测试 getLimitAPI
     */
    @Test
    public void testGetLimitAPI() {
        LimitAPI api = polarisOperator.getLimitAPI();
        Assert.assertSame(mockLimitAPI, api);
    }

    /**
     * 测试 getRouterAPI
     */
    @Test
    public void testGetRouterAPI() {
        RouterAPI api = polarisOperator.getRouterAPI();
        Assert.assertSame(mockRouterAPI, api);
    }

    /**
     * 测试 getConfigFileAPI
     */
    @Test
    public void testGetConfigFileAPI() {
        ConfigFileService api = polarisOperator.getConfigFileAPI();
        Assert.assertSame(mockConfigFileAPI, api);
    }

    /**
     * 测试 getConfigFilePublishAPI
     */
    @Test
    public void testGetConfigFilePublishAPI() {
        ConfigFilePublishService api = polarisOperator.getConfigFilePublishAPI();
        Assert.assertSame(mockConfigFilePublishAPI, api);
    }

    /**
     * 测试 getCircuitBreakAPI
     */
    @Test
    public void testGetCircuitBreakAPI() {
        CircuitBreakAPI api = polarisOperator.getCircuitBreakAPI();
        Assert.assertSame(mockCircuitBreakAPI, api);
    }

    // ==================== destroy 测试 ====================

    /**
     * 测试 destroy 方法调用 SDKContext.close()
     */
    @Test
    public void testDestroy() {
        // Act
        polarisOperator.destroy();

        // Assert
        Mockito.verify(mockSdkContext).close();
    }
}
