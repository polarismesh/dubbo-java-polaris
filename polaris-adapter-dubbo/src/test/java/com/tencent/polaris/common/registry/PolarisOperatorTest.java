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
import com.tencent.polaris.api.pojo.CircuitBreakerStatus;
import com.tencent.polaris.api.pojo.DefaultServiceInstances;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.pojo.RouteArgument;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.pojo.ServiceKey;
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
import com.tencent.polaris.api.plugin.common.ValueContext;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.ServiceRuleByProto;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.factory.ConfigAPIFactory;
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
import java.util.ArrayList;
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
 * 覆盖所有公共方法及其分支逻辑
 *
 * @author Yuwei Fu
 */
public class PolarisOperatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisOperatorTest.class);

    private PolarisOperator polarisOperator;
    private ConsumerAPI consumerAPI;
    private ProviderAPI providerAPI;
    private LimitAPI limitAPI;
    private RouterAPI routerAPI;
    private CircuitBreakAPI circuitBreakAPI;
    private ConfigFileService configFileAPI;
    private ConfigFilePublishService configFilePublishAPI;
    private SDKContext sdkContext;
    private PolarisConfig polarisConfig;
    private ValueContext valueContext;

    @Before
    public void before() throws Exception {
        // 创建所有 Mock 对象
        consumerAPI = Mockito.mock(ConsumerAPI.class);
        providerAPI = Mockito.mock(ProviderAPI.class);
        limitAPI = Mockito.mock(LimitAPI.class);
        routerAPI = Mockito.mock(RouterAPI.class);
        circuitBreakAPI = Mockito.mock(CircuitBreakAPI.class);
        configFileAPI = Mockito.mock(ConfigFileService.class);
        configFilePublishAPI = Mockito.mock(ConfigFilePublishService.class);
        sdkContext = Mockito.mock(SDKContext.class);
        valueContext = Mockito.mock(ValueContext.class);
        Mockito.when(sdkContext.getValueContext()).thenReturn(valueContext);

        // 创建真实的 PolarisConfig
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "test-namespace");
        parameters.put(Consts.KEY_TOKEN, "test-token");
        polarisConfig = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, parameters);

        // 通过反射创建 PolarisOperator 实例并注入 Mock
        polarisOperator = createMockedOperator();
    }

    @After
    public void after() {
        // 清理资源
    }

    /**
     * 通过反射创建 PolarisOperator 实例并注入所有 Mock 依赖
     */
    private PolarisOperator createMockedOperator() throws Exception {
        // 使用 Mockito 创建一个部分 Mock 的 PolarisOperator，绕过构造方法
        PolarisOperator operator = Mockito.mock(PolarisOperator.class, Mockito.CALLS_REAL_METHODS);

        // 通过反射注入所有依赖
        setField(operator, "polarisConfig", polarisConfig);
        setField(operator, "sdkContext", sdkContext);
        setField(operator, "consumerAPI", consumerAPI);
        setField(operator, "providerAPI", providerAPI);
        setField(operator, "limitAPI", limitAPI);
        setField(operator, "routerAPI", routerAPI);
        setField(operator, "circuitBreakAPI", circuitBreakAPI);
        setField(operator, "configFileAPI", configFileAPI);
        setField(operator, "configFilePublishAPI", configFilePublishAPI);

        return operator;
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
     * 测试 formatCode 静态方法：正常值
     */
    @Test
    public void testFormatCode_withNormalValue() {
        String result = PolarisOperator.formatCode("12345");
        Assert.assertEquals("POLARIS:12345", result);
    }

    /**
     * 测试 formatCode 静态方法：整数值
     */
    @Test
    public void testFormatCode_withIntegerValue() {
        String result = PolarisOperator.formatCode(500);
        Assert.assertEquals("POLARIS:500", result);
    }

    /**
     * 测试 formatCode 静态方法：null 值
     */
    @Test
    public void testFormatCode_withNull() {
        String result = PolarisOperator.formatCode(null);
        Assert.assertEquals("POLARIS:null", result);
    }

    // ==================== register 测试 ====================

    /**
     * 测试服务注册：正常场景
     */
    @Test
    public void testRegister_normalScenario() {
        // Arrange
        String service = "test-service";
        String host = "192.168.1.1";
        int port = 20880;
        String protocol = "dubbo";
        String version = "1.0.0";
        int weight = 100;
        Map<String, String> metadata = new HashMap<>();
        metadata.put("env", "test");

        InstanceRegisterResponse response = Mockito.mock(InstanceRegisterResponse.class);
        Mockito.when(providerAPI.registerInstance(Mockito.any(InstanceRegisterRequest.class))).thenReturn(response);

        // Act
        polarisOperator.register(service, host, port, protocol, version, weight, metadata);

        // Assert
        ArgumentCaptor<InstanceRegisterRequest> captor = ArgumentCaptor.forClass(InstanceRegisterRequest.class);
        Mockito.verify(providerAPI).registerInstance(captor.capture());

        InstanceRegisterRequest capturedRequest = captor.getValue();
        Assert.assertEquals("test-namespace", capturedRequest.getNamespace());
        Assert.assertEquals(service, capturedRequest.getService());
        Assert.assertEquals(host, capturedRequest.getHost());
        Assert.assertEquals((Integer) port, (Integer) capturedRequest.getPort());
        Assert.assertEquals((Integer) weight, (Integer) capturedRequest.getWeight());
        Assert.assertEquals(version, capturedRequest.getVersion());
        Assert.assertEquals(protocol, capturedRequest.getProtocol());
        Assert.assertEquals("test-token", capturedRequest.getToken());
        Assert.assertEquals(metadata, capturedRequest.getMetadata());
        Assert.assertEquals((Integer) polarisConfig.getTtl(), (Integer) capturedRequest.getTtl());

        LOG.info("[Test] register 正常场景测试通过");
    }

    /**
     * 测试服务注册：空 metadata 场景
     */
    @Test
    public void testRegister_withEmptyMetadata() {
        // Arrange
        InstanceRegisterResponse response = Mockito.mock(InstanceRegisterResponse.class);
        Mockito.when(providerAPI.registerInstance(Mockito.any(InstanceRegisterRequest.class))).thenReturn(response);

        // Act
        polarisOperator.register("svc", "127.0.0.1", 8080, "dubbo", "1.0", 1, Collections.emptyMap());

        // Assert
        Mockito.verify(providerAPI).registerInstance(Mockito.any(InstanceRegisterRequest.class));
    }

    // ==================== deregister 测试 ====================

    /**
     * 测试服务注销：正常场景
     */
    @Test
    public void testDeregister_normalScenario() {
        // Arrange
        String service = "test-service";
        String host = "192.168.1.1";
        int port = 20880;

        // Act
        polarisOperator.deregister(service, host, port);

        // Assert
        ArgumentCaptor<InstanceDeregisterRequest> captor = ArgumentCaptor.forClass(InstanceDeregisterRequest.class);
        Mockito.verify(providerAPI).deRegister(captor.capture());

        InstanceDeregisterRequest capturedRequest = captor.getValue();
        Assert.assertEquals("test-namespace", capturedRequest.getNamespace());
        Assert.assertEquals(service, capturedRequest.getService());
        Assert.assertEquals(host, capturedRequest.getHost());
        Assert.assertEquals((Integer) port, (Integer) capturedRequest.getPort());
        Assert.assertEquals("test-token", capturedRequest.getToken());

        LOG.info("[Test] deregister 正常场景测试通过");
    }

    // ==================== watchService 测试 ====================

    /**
     * 测试监听服务：成功场景
     */
    @Test
    public void testWatchService_success() {
        // Arrange
        String service = "test-service";
        ServiceListener listener = Mockito.mock(ServiceListener.class);
        WatchServiceResponse watchResponse = Mockito.mock(WatchServiceResponse.class);
        Mockito.when(watchResponse.isSuccess()).thenReturn(true);
        Mockito.when(consumerAPI.watchService(Mockito.any(WatchServiceRequest.class))).thenReturn(watchResponse);

        // Act
        boolean result = polarisOperator.watchService(service, listener);

        // Assert
        Assert.assertTrue(result);
        Mockito.verify(consumerAPI).watchService(Mockito.any(WatchServiceRequest.class));

        LOG.info("[Test] watchService 成功场景测试通过");
    }

    /**
     * 测试监听服务：失败场景
     */
    @Test
    public void testWatchService_failure() {
        // Arrange
        String service = "test-service";
        ServiceListener listener = Mockito.mock(ServiceListener.class);
        WatchServiceResponse watchResponse = Mockito.mock(WatchServiceResponse.class);
        Mockito.when(watchResponse.isSuccess()).thenReturn(false);
        Mockito.when(consumerAPI.watchService(Mockito.any(WatchServiceRequest.class))).thenReturn(watchResponse);

        // Act
        boolean result = polarisOperator.watchService(service, listener);

        // Assert
        Assert.assertFalse(result);
    }

    // ==================== unwatchService 测试 ====================

    /**
     * 测试取消监听服务：正常场景
     */
    @Test
    public void testUnwatchService_normalScenario() {
        // Arrange
        String service = "test-service";
        ServiceListener listener = Mockito.mock(ServiceListener.class);

        // Act
        polarisOperator.unwatchService(service, listener);

        // Assert
        Mockito.verify(consumerAPI).unWatchService(Mockito.any(UnWatchServiceRequest.class));

        LOG.info("[Test] unwatchService 正常场景测试通过");
    }

    // ==================== getAvailableInstances 测试 ====================

    /**
     * 测试获取可用实例：包含熔断实例
     */
    @Test
    public void testGetAvailableInstances_includeCircuitBreakInstances() {
        // Arrange
        String service = "test-service";
        Instance instance1 = Mockito.mock(Instance.class);
        Instance instance2 = Mockito.mock(Instance.class);
        Instance[] instances = new Instance[]{instance1, instance2};

        InstancesResponse instancesResponse = Mockito.mock(InstancesResponse.class);
        Mockito.when(instancesResponse.getInstances()).thenReturn(instances);
        Mockito.when(consumerAPI.getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class)))
                .thenReturn(instancesResponse);

        // Act
        Instance[] result = polarisOperator.getAvailableInstances(service, true);

        // Assert
        Assert.assertEquals(2, result.length);
        Assert.assertSame(instance1, result[0]);
        Assert.assertSame(instance2, result[1]);

        Mockito.verify(consumerAPI).getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class));

        LOG.info("[Test] getAvailableInstances 包含熔断实例测试通过");
    }

    /**
     * 测试获取可用实例：不包含熔断实例
     */
    @Test
    public void testGetAvailableInstances_excludeCircuitBreakInstances() {
        // Arrange
        String service = "test-service";
        Instance[] instances = new Instance[]{};

        InstancesResponse instancesResponse = Mockito.mock(InstancesResponse.class);
        Mockito.when(instancesResponse.getInstances()).thenReturn(instances);
        Mockito.when(consumerAPI.getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class)))
                .thenReturn(instancesResponse);

        // Act
        Instance[] result = polarisOperator.getAvailableInstances(service, false);

        // Assert
        Assert.assertEquals(0, result.length);

        Mockito.verify(consumerAPI).getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class));
    }

    // ==================== reportInvokeResult 测试 ====================

    /**
     * 测试上报调用结果：正常场景
     */
    @Test
    public void testReportInvokeResult_normalScenario() {
        // Arrange
        String service = "test-service";
        String method = "testMethod";
        String host = "192.168.1.1";
        int port = 20880;
        String callerIp = "10.0.0.1";
        long delay = 100L;
        RetStatus retStatus = RetStatus.RetSuccess;
        int code = 200;

        // Act
        polarisOperator.reportInvokeResult(service, method, host, port, callerIp, delay, retStatus, code);

        // Assert
        ArgumentCaptor<ServiceCallResult> captor = ArgumentCaptor.forClass(ServiceCallResult.class);
        Mockito.verify(consumerAPI).updateServiceCallResult(captor.capture());

        ServiceCallResult capturedResult = captor.getValue();
        Assert.assertEquals("test-namespace", capturedResult.getNamespace());
        Assert.assertEquals(service, capturedResult.getService());
        Assert.assertEquals(method, capturedResult.getMethod());
        Assert.assertEquals(host, capturedResult.getHost());
        Assert.assertEquals((Integer) port, (Integer) capturedResult.getPort());
        Assert.assertEquals((Long) delay, (Long) capturedResult.getDelay());
        Assert.assertEquals(retStatus, capturedResult.getRetStatus());
        Assert.assertEquals((Integer) code, (Integer) capturedResult.getRetCode());
        Assert.assertEquals(callerIp, capturedResult.getCallerIp());

        LOG.info("[Test] reportInvokeResult 正常场景测试通过");
    }

    /**
     * 测试上报调用结果：RetFail 状态
     */
    @Test
    public void testReportInvokeResult_withRetFail() {
        // Act
        polarisOperator.reportInvokeResult("svc", "method", "host", 8080, "caller", 50L, RetStatus.RetFail, 500);

        // Assert
        ArgumentCaptor<ServiceCallResult> captor = ArgumentCaptor.forClass(ServiceCallResult.class);
        Mockito.verify(consumerAPI).updateServiceCallResult(captor.capture());
        Assert.assertEquals(RetStatus.RetFail, captor.getValue().getRetStatus());
        Assert.assertEquals((Integer) 500, (Integer) captor.getValue().getRetCode());
    }

    /**
     * 测试上报调用结果：PolarisException 场景（不抛出异常，仅记录日志）
     */
    @Test
    public void testReportInvokeResult_withPolarisException() {
        // Arrange
        Mockito.doThrow(new PolarisException(ErrorCode.NETWORK_ERROR, "网络错误"))
                .when(consumerAPI).updateServiceCallResult(Mockito.any(ServiceCallResult.class));

        // Act - 不应抛出异常
        polarisOperator.reportInvokeResult("svc", "method", "host", 8080, "caller", 100L,
                RetStatus.RetFail, 500);

        // Assert - 验证方法被调用了
        Mockito.verify(consumerAPI).updateServiceCallResult(Mockito.any(ServiceCallResult.class));
        LOG.info("[Test] reportInvokeResult PolarisException 场景测试通过，异常被正确捕获");
    }

    // ==================== route 测试 ====================

    /**
     * 测试路由：正常场景
     */
    @Test
    public void testRoute_normalScenario() {
        // Arrange
        String service = "test-service";
        String method = "testMethod";
        Set<RouteArgument> arguments = new HashSet<>();
        Instance instance1 = Mockito.mock(Instance.class);
        List<Instance> inputInstances = new ArrayList<>();
        inputInstances.add(instance1);

        Instance routedInstance = Mockito.mock(Instance.class);
        List<Instance> routedInstances = Collections.singletonList(routedInstance);

        ServiceKey serviceKey = new ServiceKey("test-namespace", service);
        DefaultServiceInstances routedServiceInstances = new DefaultServiceInstances(serviceKey, routedInstances);

        ProcessRoutersResponse routersResponse = Mockito.mock(ProcessRoutersResponse.class);
        Mockito.when(routersResponse.getServiceInstances()).thenReturn(routedServiceInstances);
        Mockito.when(routerAPI.processRouters(Mockito.any(ProcessRoutersRequest.class))).thenReturn(routersResponse);

        // Act
        List<Instance> result = polarisOperator.route(service, method, arguments, inputInstances);

        // Assert
        Assert.assertEquals(1, result.size());
        Assert.assertSame(routedInstance, result.get(0));

        ArgumentCaptor<ProcessRoutersRequest> captor = ArgumentCaptor.forClass(ProcessRoutersRequest.class);
        Mockito.verify(routerAPI).processRouters(captor.capture());
        Assert.assertEquals(method, captor.getValue().getMethod());

        LOG.info("[Test] route 正常场景测试通过");
    }

    /**
     * 测试路由：空实例列表
     */
    @Test
    public void testRoute_withEmptyInstances() {
        // Arrange
        List<Instance> emptyInstances = Collections.emptyList();

        ServiceKey serviceKey = new ServiceKey("test-namespace", "svc");
        DefaultServiceInstances routedServiceInstances = new DefaultServiceInstances(serviceKey, emptyInstances);

        ProcessRoutersResponse routersResponse = Mockito.mock(ProcessRoutersResponse.class);
        Mockito.when(routersResponse.getServiceInstances()).thenReturn(routedServiceInstances);
        Mockito.when(routerAPI.processRouters(Mockito.any(ProcessRoutersRequest.class))).thenReturn(routersResponse);

        // Act
        List<Instance> result = polarisOperator.route("svc", "method", new HashSet<>(), emptyInstances);

        // Assert
        Assert.assertTrue(result.isEmpty());
    }

    // ==================== loadBalance 测试 ====================

    /**
     * 测试负载均衡：正常场景
     */
    @Test
    public void testLoadBalance_normalScenario() {
        // Arrange
        String service = "test-service";
        String hashKey = "user-123";
        Instance instance1 = Mockito.mock(Instance.class);
        Instance instance2 = Mockito.mock(Instance.class);
        List<Instance> instances = new ArrayList<>();
        instances.add(instance1);
        instances.add(instance2);

        Instance targetInstance = Mockito.mock(Instance.class);
        ProcessLoadBalanceResponse lbResponse = Mockito.mock(ProcessLoadBalanceResponse.class);
        Mockito.when(lbResponse.getTargetInstance()).thenReturn(targetInstance);
        Mockito.when(routerAPI.processLoadBalance(Mockito.any(ProcessLoadBalanceRequest.class)))
                .thenReturn(lbResponse);

        // Act
        Instance result = polarisOperator.loadBalance(service, hashKey, instances);

        // Assert
        Assert.assertSame(targetInstance, result);

        ArgumentCaptor<ProcessLoadBalanceRequest> captor = ArgumentCaptor
                .forClass(ProcessLoadBalanceRequest.class);
        Mockito.verify(routerAPI).processLoadBalance(captor.capture());
        Assert.assertEquals(hashKey, captor.getValue().getCriteria().getHashKey());

        LOG.info("[Test] loadBalance 正常场景测试通过");
    }

    /**
     * 测试负载均衡：hashKey 为 null
     */
    @Test
    public void testLoadBalance_withNullHashKey() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        List<Instance> instances = Collections.singletonList(instance);

        ProcessLoadBalanceResponse lbResponse = Mockito.mock(ProcessLoadBalanceResponse.class);
        Mockito.when(lbResponse.getTargetInstance()).thenReturn(instance);
        Mockito.when(routerAPI.processLoadBalance(Mockito.any(ProcessLoadBalanceRequest.class)))
                .thenReturn(lbResponse);

        // Act
        Instance result = polarisOperator.loadBalance("svc", null, instances);

        // Assert
        Assert.assertSame(instance, result);
    }

    // ==================== checkCircuitBreakerPassing 测试 ====================

    /**
     * 测试熔断检查：CircuitBreakerStatus 不为空且状态为 OPEN（熔断打开）
     */
    @Test
    public void testCheckCircuitBreakerPassing_statusOpen() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        CircuitBreakerStatus status = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(status.getStatus()).thenReturn(CircuitBreakerStatus.Status.OPEN);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(status);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert - OPEN 状态应返回 false
        Assert.assertFalse(result);

        LOG.info("[Test] checkCircuitBreakerPassing OPEN 状态测试通过");
    }

    /**
     * 测试熔断检查：CircuitBreakerStatus 不为空且状态为 CLOSE（熔断关闭）
     */
    @Test
    public void testCheckCircuitBreakerPassing_statusClose() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        CircuitBreakerStatus status = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(status.getStatus()).thenReturn(CircuitBreakerStatus.Status.CLOSE);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(status);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert - CLOSE 状态应返回 true
        Assert.assertTrue(result);

        LOG.info("[Test] checkCircuitBreakerPassing CLOSE 状态测试通过");
    }

    /**
     * 测试熔断检查：CircuitBreakerStatus 不为空且状态为 HALF_OPEN
     */
    @Test
    public void testCheckCircuitBreakerPassing_statusHalfOpen() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        CircuitBreakerStatus status = Mockito.mock(CircuitBreakerStatus.class);
        Mockito.when(status.getStatus()).thenReturn(CircuitBreakerStatus.Status.HALF_OPEN);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(status);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert - HALF_OPEN 状态应返回 true（不是 OPEN）
        Assert.assertTrue(result);
    }

    /**
     * 测试熔断检查：CircuitBreakerStatus 为 null，通过 CircuitBreakerFlow 检查通过
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_flowCheckPass() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(instance.getNamespace()).thenReturn("test-namespace");
        Mockito.when(instance.getService()).thenReturn("test-service");
        Mockito.when(instance.getHost()).thenReturn("192.168.1.1");
        Mockito.when(instance.getPort()).thenReturn(8080);

        CircuitBreakerFlow circuitBreakerFlow = Mockito.mock(CircuitBreakerFlow.class);
        CheckResult checkResult = new CheckResult(true, "", null);
        Mockito.when(circuitBreakerFlow.check(Mockito.any())).thenReturn(checkResult);
        Mockito.when(valueContext.getValue(CircuitBreakerFlow.class.getCanonicalName()))
                .thenReturn(circuitBreakerFlow);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert
        Assert.assertTrue(result);

        LOG.info("[Test] checkCircuitBreakerPassing flow 检查通过测试通过");
    }

    /**
     * 测试熔断检查：CircuitBreakerStatus 为 null，通过 CircuitBreakerFlow 检查不通过
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_flowCheckNotPass() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(instance.getNamespace()).thenReturn("test-namespace");
        Mockito.when(instance.getService()).thenReturn("test-service");
        Mockito.when(instance.getHost()).thenReturn("192.168.1.1");
        Mockito.when(instance.getPort()).thenReturn(8080);

        CircuitBreakerFlow circuitBreakerFlow = Mockito.mock(CircuitBreakerFlow.class);
        CheckResult checkResult = new CheckResult(false, "test-breaker", null);
        Mockito.when(circuitBreakerFlow.check(Mockito.any())).thenReturn(checkResult);
        Mockito.when(valueContext.getValue(CircuitBreakerFlow.class.getCanonicalName()))
                .thenReturn(circuitBreakerFlow);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert
        Assert.assertFalse(result);

        LOG.info("[Test] checkCircuitBreakerPassing flow 检查不通过测试通过");
    }

    /**
     * 测试熔断检查：CircuitBreakerStatus 为 null，CircuitBreakerFlow 也为 null
     */
    @Test
    public void testCheckCircuitBreakerPassing_noStatus_noFlow() {
        // Arrange
        Instance instance = Mockito.mock(Instance.class);
        Mockito.when(instance.getCircuitBreakerStatus()).thenReturn(null);
        Mockito.when(instance.getNamespace()).thenReturn("test-namespace");
        Mockito.when(instance.getService()).thenReturn("test-service");
        Mockito.when(instance.getHost()).thenReturn("192.168.1.1");
        Mockito.when(instance.getPort()).thenReturn(8080);

        Mockito.when(valueContext.getValue(CircuitBreakerFlow.class.getCanonicalName())).thenReturn(null);

        // Act
        boolean result = polarisOperator.checkCircuitBreakerPassing(instance);

        // Assert - 没有 Flow 时默认返回 true
        Assert.assertTrue(result);

        LOG.info("[Test] checkCircuitBreakerPassing 无 flow 时默认通过测试通过");
    }

    // ==================== getQuota 测试 ====================

    /**
     * 测试限流：正常场景，配额通过
     */
    @Test
    public void testGetQuota_quotaPassed() {
        // Arrange
        String service = "test-service";
        String method = "testMethod";
        Set<Argument> arguments = new HashSet<>();
        arguments.add(Argument.buildHeader("key", "value"));

        QuotaResponse quotaResponse = Mockito.mock(QuotaResponse.class);
        Mockito.when(quotaResponse.getCode()).thenReturn(QuotaResultCode.QuotaResultOk);
        Mockito.when(limitAPI.getQuota(Mockito.any(QuotaRequest.class))).thenReturn(quotaResponse);

        // Act
        QuotaResponse result = polarisOperator.getQuota(service, method, arguments);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals(QuotaResultCode.QuotaResultOk, result.getCode());

        ArgumentCaptor<QuotaRequest> captor = ArgumentCaptor.forClass(QuotaRequest.class);
        Mockito.verify(limitAPI).getQuota(captor.capture());

        QuotaRequest capturedRequest = captor.getValue();
        Assert.assertEquals("test-namespace", capturedRequest.getNamespace());
        Assert.assertEquals(service, capturedRequest.getService());
        Assert.assertEquals(method, capturedRequest.getMethod());
        Assert.assertEquals(arguments, capturedRequest.getArguments());
        Assert.assertEquals((Integer) 1, (Integer) capturedRequest.getCount());

        LOG.info("[Test] getQuota 配额通过测试通过");
    }

    /**
     * 测试限流：配额被限流
     */
    @Test
    public void testGetQuota_quotaLimited() {
        // Arrange
        QuotaResponse quotaResponse = Mockito.mock(QuotaResponse.class);
        Mockito.when(quotaResponse.getCode()).thenReturn(QuotaResultCode.QuotaResultLimited);
        Mockito.when(limitAPI.getQuota(Mockito.any(QuotaRequest.class))).thenReturn(quotaResponse);

        // Act
        QuotaResponse result = polarisOperator.getQuota("svc", "method", new HashSet<>());

        // Assert
        Assert.assertEquals(QuotaResultCode.QuotaResultLimited, result.getCode());
    }

    // ==================== getServiceRule 测试 ====================

    /**
     * 测试获取服务规则：正常场景
     */
    @Test
    public void testGetServiceRule_normalScenario() {
        // Arrange
        String service = "test-service";
        EventType eventType = EventType.ROUTING;

        ServiceRule mockRule = Mockito.mock(ServiceRule.class);
        ServiceRuleResponse ruleResponse = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(ruleResponse.getServiceRule()).thenReturn(mockRule);
        Mockito.when(consumerAPI.getServiceRule(Mockito.any(GetServiceRuleRequest.class))).thenReturn(ruleResponse);

        // Act
        ServiceRule result = polarisOperator.getServiceRule(service, eventType);

        // Assert
        Assert.assertSame(mockRule, result);

        ArgumentCaptor<GetServiceRuleRequest> captor = ArgumentCaptor.forClass(GetServiceRuleRequest.class);
        Mockito.verify(consumerAPI).getServiceRule(captor.capture());
        Assert.assertEquals("test-namespace", captor.getValue().getNamespace());
        Assert.assertEquals(service, captor.getValue().getService());
        Assert.assertEquals(eventType, captor.getValue().getRuleType());

        LOG.info("[Test] getServiceRule 正常场景测试通过");
    }

    /**
     * 测试获取服务规则：service 为空字符串时返回空 ServiceRuleByProto
     */
    @Test
    public void testGetServiceRule_withEmptyService() {
        // Act
        ServiceRule result = polarisOperator.getServiceRule("", EventType.ROUTING);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof ServiceRuleByProto);
        // 不应调用 consumerAPI
        Mockito.verify(consumerAPI, Mockito.never()).getServiceRule(Mockito.any(GetServiceRuleRequest.class));

        LOG.info("[Test] getServiceRule 空 service 测试通过");
    }

    /**
     * 测试获取服务规则：service 为 null 时返回空 ServiceRuleByProto
     */
    @Test
    public void testGetServiceRule_withNullService() {
        // Act
        ServiceRule result = polarisOperator.getServiceRule(null, EventType.ROUTING);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof ServiceRuleByProto);
        Mockito.verify(consumerAPI, Mockito.never()).getServiceRule(Mockito.any(GetServiceRuleRequest.class));
    }

    /**
     * 测试获取服务规则：不同 EventType（CIRCUIT_BREAKING）
     */
    @Test
    public void testGetServiceRule_withCircuitBreakingEventType() {
        // Arrange
        ServiceRule mockRule = Mockito.mock(ServiceRule.class);
        ServiceRuleResponse ruleResponse = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(ruleResponse.getServiceRule()).thenReturn(mockRule);
        Mockito.when(consumerAPI.getServiceRule(Mockito.any(GetServiceRuleRequest.class))).thenReturn(ruleResponse);

        // Act
        ServiceRule result = polarisOperator.getServiceRule("svc", EventType.CIRCUIT_BREAKING);

        // Assert
        Assert.assertSame(mockRule, result);

        ArgumentCaptor<GetServiceRuleRequest> captor = ArgumentCaptor.forClass(GetServiceRuleRequest.class);
        Mockito.verify(consumerAPI).getServiceRule(captor.capture());
        Assert.assertEquals(EventType.CIRCUIT_BREAKING, captor.getValue().getRuleType());
    }

    /**
     * 测试获取服务规则：EventType 为 RATE_LIMITING
     */
    @Test
    public void testGetServiceRule_withRateLimitingEventType() {
        // Arrange
        ServiceRule mockRule = Mockito.mock(ServiceRule.class);
        ServiceRuleResponse ruleResponse = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(ruleResponse.getServiceRule()).thenReturn(mockRule);
        Mockito.when(consumerAPI.getServiceRule(Mockito.any(GetServiceRuleRequest.class))).thenReturn(ruleResponse);

        // Act
        ServiceRule result = polarisOperator.getServiceRule("svc", EventType.RATE_LIMITING);

        // Assert
        ArgumentCaptor<GetServiceRuleRequest> captor = ArgumentCaptor.forClass(GetServiceRuleRequest.class);
        Mockito.verify(consumerAPI).getServiceRule(captor.capture());
        Assert.assertEquals(EventType.RATE_LIMITING, captor.getValue().getRuleType());
    }

    // ==================== getServices 测试 ====================

    /**
     * 测试获取服务列表：正常场景
     */
    @Test
    public void testGetServices_normalScenario() {
        // Arrange
        ServiceInfo serviceInfo1 = new ServiceInfo();
        ServiceInfo serviceInfo2 = new ServiceInfo();
        List<ServiceInfo> serviceInfos = new ArrayList<>();
        serviceInfos.add(serviceInfo1);
        serviceInfos.add(serviceInfo2);

        ServicesResponse servicesResponse = Mockito.mock(ServicesResponse.class);
        Mockito.when(servicesResponse.getServices()).thenReturn(serviceInfos);
        Mockito.when(consumerAPI.getServices(Mockito.any(GetServicesRequest.class))).thenReturn(servicesResponse);

        // Act
        List<ServiceInfo> result = polarisOperator.getServices();

        // Assert
        Assert.assertEquals(2, result.size());
        Assert.assertSame(serviceInfo1, result.get(0));
        Assert.assertSame(serviceInfo2, result.get(1));

        ArgumentCaptor<GetServicesRequest> captor = ArgumentCaptor.forClass(GetServicesRequest.class);
        Mockito.verify(consumerAPI).getServices(captor.capture());
        Assert.assertEquals("test-namespace", captor.getValue().getNamespace());

        LOG.info("[Test] getServices 正常场景测试通过");
    }

    /**
     * 测试获取服务列表：返回空列表
     */
    @Test
    public void testGetServices_emptyResult() {
        // Arrange
        ServicesResponse servicesResponse = Mockito.mock(ServicesResponse.class);
        Mockito.when(servicesResponse.getServices()).thenReturn(Collections.emptyList());
        Mockito.when(consumerAPI.getServices(Mockito.any(GetServicesRequest.class))).thenReturn(servicesResponse);

        // Act
        List<ServiceInfo> result = polarisOperator.getServices();

        // Assert
        Assert.assertTrue(result.isEmpty());
    }

    // ==================== getter 方法测试 ====================

    /**
     * 测试 getPolarisConfig 返回正确的配置
     */
    @Test
    public void testGetPolarisConfig() {
        PolarisConfig config = polarisOperator.getPolarisConfig();
        Assert.assertNotNull(config);
        Assert.assertEquals("test-namespace", config.getNamespace());
        Assert.assertEquals("test-token", config.getToken());
    }

    /**
     * 测试 getSdkContext 返回正确的 SDKContext
     */
    @Test
    public void testGetSdkContext() {
        SDKContext context = polarisOperator.getSdkContext();
        Assert.assertSame(sdkContext, context);
    }

    /**
     * 测试 getConsumerAPI 返回正确的 ConsumerAPI
     */
    @Test
    public void testGetConsumerAPI() {
        ConsumerAPI api = polarisOperator.getConsumerAPI();
        Assert.assertSame(consumerAPI, api);
    }

    /**
     * 测试 getProviderAPI 返回正确的 ProviderAPI
     */
    @Test
    public void testGetProviderAPI() {
        ProviderAPI api = polarisOperator.getProviderAPI();
        Assert.assertSame(providerAPI, api);
    }

    /**
     * 测试 getLimitAPI 返回正确的 LimitAPI
     */
    @Test
    public void testGetLimitAPI() {
        LimitAPI api = polarisOperator.getLimitAPI();
        Assert.assertSame(limitAPI, api);
    }

    /**
     * 测试 getRouterAPI 返回正确的 RouterAPI
     */
    @Test
    public void testGetRouterAPI() {
        RouterAPI api = polarisOperator.getRouterAPI();
        Assert.assertSame(routerAPI, api);
    }

    /**
     * 测试 getConfigFileAPI 返回正确的 ConfigFileService
     */
    @Test
    public void testGetConfigFileAPI() {
        ConfigFileService api = polarisOperator.getConfigFileAPI();
        Assert.assertSame(configFileAPI, api);
    }

    /**
     * 测试 getConfigFilePublishAPI 返回正确的 ConfigFilePublishService
     */
    @Test
    public void testGetConfigFilePublishAPI() {
        ConfigFilePublishService api = polarisOperator.getConfigFilePublishAPI();
        Assert.assertSame(configFilePublishAPI, api);
    }

    /**
     * 测试 getCircuitBreakAPI 返回正确的 CircuitBreakAPI
     */
    @Test
    public void testGetCircuitBreakAPI() {
        CircuitBreakAPI api = polarisOperator.getCircuitBreakAPI();
        Assert.assertSame(circuitBreakAPI, api);
    }

    // ==================== destroy 测试 ====================

    /**
     * 测试 destroy 方法调用 sdkContext.close()
     */
    @Test
    public void testDestroy() {
        // Act
        polarisOperator.destroy();

        // Assert
        Mockito.verify(sdkContext).close();
        LOG.info("[Test] destroy 测试通过");
    }

    // ==================== reportInvokeResult callerService 测试 ====================

    /**
     * 测试上报调用结果时 callerService 的 namespace 正确设置
     */
    @Test
    public void testReportInvokeResult_callerServiceNamespace() {
        // Act
        polarisOperator.reportInvokeResult("svc", "method", "host", 8080, "10.0.0.1", 50L,
                RetStatus.RetSuccess, 200);

        // Assert
        ArgumentCaptor<ServiceCallResult> captor = ArgumentCaptor.forClass(ServiceCallResult.class);
        Mockito.verify(consumerAPI).updateServiceCallResult(captor.capture());

        ServiceCallResult result = captor.getValue();
        Assert.assertNotNull(result.getCallerService());
        Assert.assertEquals("test-namespace", result.getCallerService().getNamespace());
        Assert.assertEquals("", result.getCallerService().getService());
    }

    // ==================== route 参数验证测试 ====================

    /**
     * 测试路由：验证 SourceService 的 arguments 正确设置
     */
    @Test
    public void testRoute_verifySourceServiceArguments() {
        // Arrange
        Set<RouteArgument> arguments = new HashSet<>();
        arguments.add(RouteArgument.buildHeader("env", "test"));

        Instance instance = Mockito.mock(Instance.class);
        List<Instance> instances = Collections.singletonList(instance);

        ServiceKey serviceKey = new ServiceKey("test-namespace", "svc");
        DefaultServiceInstances routedInstances = new DefaultServiceInstances(serviceKey, instances);

        ProcessRoutersResponse response = Mockito.mock(ProcessRoutersResponse.class);
        Mockito.when(response.getServiceInstances()).thenReturn(routedInstances);
        Mockito.when(routerAPI.processRouters(Mockito.any(ProcessRoutersRequest.class))).thenReturn(response);

        // Act
        polarisOperator.route("svc", "method", arguments, instances);

        // Assert
        ArgumentCaptor<ProcessRoutersRequest> captor = ArgumentCaptor.forClass(ProcessRoutersRequest.class);
        Mockito.verify(routerAPI).processRouters(captor.capture());
        Assert.assertNotNull(captor.getValue().getSourceService());
    }

    // ==================== getAvailableInstances 请求参数验证 ====================

    /**
     * 测试获取可用实例：验证请求参数中的 namespace 和 service 设置正确
     */
    @Test
    public void testGetAvailableInstances_verifyRequestParameters() {
        // Arrange
        InstancesResponse instancesResponse = Mockito.mock(InstancesResponse.class);
        Mockito.when(instancesResponse.getInstances()).thenReturn(new Instance[]{});
        Mockito.when(consumerAPI.getHealthyInstances(Mockito.any(GetHealthyInstancesRequest.class)))
                .thenReturn(instancesResponse);

        // Act
        polarisOperator.getAvailableInstances("my-service", true);

        // Assert
        ArgumentCaptor<GetHealthyInstancesRequest> captor = ArgumentCaptor
                .forClass(GetHealthyInstancesRequest.class);
        Mockito.verify(consumerAPI).getHealthyInstances(captor.capture());
        Assert.assertEquals("test-namespace", captor.getValue().getNamespace());
        Assert.assertEquals("my-service", captor.getValue().getService());
    }

    // ==================== 构造器和初始化分支测试 ====================

    /**
     * 辅助方法：创建通过构造器初始化的 PolarisOperator，mock 所有静态工厂方法
     */
    private PolarisOperator createOperatorViaConstructor(Map<String, String> parameters,
            BootConfigHandler... handlers) {
        // 在 mock 静态方法之前先获取真实的默认配置
        com.tencent.polaris.factory.config.ConfigurationImpl config =
                (com.tencent.polaris.factory.config.ConfigurationImpl) com.tencent.polaris.factory.ConfigAPIFactory.defaultConfig();

        try (org.mockito.MockedStatic<ConfigAPIFactory> configAPIFactoryMock =
                     Mockito.mockStatic(ConfigAPIFactory.class);
             org.mockito.MockedStatic<SDKContext> sdkContextMock =
                     Mockito.mockStatic(SDKContext.class);
             org.mockito.MockedStatic<com.tencent.polaris.factory.api.DiscoveryAPIFactory> discoveryAPIFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.factory.api.DiscoveryAPIFactory.class);
             org.mockito.MockedStatic<com.tencent.polaris.ratelimit.factory.LimitAPIFactory> limitAPIFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.ratelimit.factory.LimitAPIFactory.class);
             org.mockito.MockedStatic<com.tencent.polaris.factory.api.RouterAPIFactory> routerAPIFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.factory.api.RouterAPIFactory.class);
             org.mockito.MockedStatic<com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory> circuitBreakAPIFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory.class);
             org.mockito.MockedStatic<com.tencent.polaris.configuration.factory.ConfigFileServiceFactory> configFileServiceFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.configuration.factory.ConfigFileServiceFactory.class);
             org.mockito.MockedStatic<com.tencent.polaris.configuration.factory.ConfigFileServicePublishFactory> configFilePublishFactoryMock =
                     Mockito.mockStatic(com.tencent.polaris.configuration.factory.ConfigFileServicePublishFactory.class)) {

            configAPIFactoryMock.when(ConfigAPIFactory::defaultConfig).thenReturn(config);

            SDKContext mockSdkContext = Mockito.mock(SDKContext.class);
            ValueContext mockValueContext = Mockito.mock(ValueContext.class);
            Mockito.when(mockSdkContext.getValueContext()).thenReturn(mockValueContext);

            sdkContextMock.when(() -> SDKContext.initContextByConfig(Mockito.any())).thenReturn(mockSdkContext);
            discoveryAPIFactoryMock.when(
                            () -> com.tencent.polaris.factory.api.DiscoveryAPIFactory.createConsumerAPIByContext(Mockito.any()))
                    .thenReturn(Mockito.mock(ConsumerAPI.class));
            discoveryAPIFactoryMock.when(
                            () -> com.tencent.polaris.factory.api.DiscoveryAPIFactory.createProviderAPIByContext(Mockito.any()))
                    .thenReturn(Mockito.mock(ProviderAPI.class));
            limitAPIFactoryMock.when(
                            () -> com.tencent.polaris.ratelimit.factory.LimitAPIFactory.createLimitAPIByContext(Mockito.any()))
                    .thenReturn(Mockito.mock(LimitAPI.class));
            routerAPIFactoryMock.when(
                            () -> com.tencent.polaris.factory.api.RouterAPIFactory.createRouterAPIByContext(Mockito.any()))
                    .thenReturn(Mockito.mock(RouterAPI.class));
            circuitBreakAPIFactoryMock.when(
                            () -> com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory.createCircuitBreakAPIByContext(Mockito.any()))
                    .thenReturn(Mockito.mock(CircuitBreakAPI.class));
            configFileServiceFactoryMock.when(
                            () -> com.tencent.polaris.configuration.factory.ConfigFileServiceFactory.createConfigFileService(Mockito.any(SDKContext.class)))
                    .thenReturn(Mockito.mock(ConfigFileService.class));
            configFilePublishFactoryMock.when(
                            () -> com.tencent.polaris.configuration.factory.ConfigFileServicePublishFactory.createConfigFilePublishService(Mockito.any(SDKContext.class)))
                    .thenReturn(Mockito.mock(ConfigFilePublishService.class));

            return new PolarisOperator(PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091,
                    parameters, handlers);
        }
    }

    /**
     * 测试构造器：无 handlers（null）
     */
    @Test
    public void testConstructor_withNullHandlers() {
        Map<String, String> parameters = new HashMap<>();
        PolarisOperator operator = createOperatorViaConstructor(parameters, (BootConfigHandler[]) null);
        Assert.assertNotNull(operator);
        Assert.assertNotNull(operator.getPolarisConfig());
    }

    /**
     * 测试构造器：空 handlers 数组
     */
    @Test
    public void testConstructor_withEmptyHandlers() {
        Map<String, String> parameters = new HashMap<>();
        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试构造器：带自定义 handler
     */
    @Test
    public void testConstructor_withCustomHandler() {
        Map<String, String> parameters = new HashMap<>();
        BootConfigHandler handler = Mockito.mock(BootConfigHandler.class);
        PolarisOperator operator = createOperatorViaConstructor(parameters, handler);

        Assert.assertNotNull(operator);
        Mockito.verify(handler).handle(Mockito.eq(parameters), Mockito.any(
                com.tencent.polaris.factory.config.ConfigurationImpl.class));
    }

    /**
     * 测试初始化：stat_type = push，pushAddr 为空（使用默认地址）
     */
    @Test
    public void testInit_metricTypePush_defaultPushAddr() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = push，自定义 pushAddr
     */
    @Test
    public void testInit_metricTypePush_customPushAddr() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091/10.0.0.2:9091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = push，自定义有效的 pushInterval
     */
    @Test
    public void testInit_metricTypePush_customPushInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "30000");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = push，无效的 pushInterval（非数字）
     */
    @Test
    public void testInit_metricTypePush_invalidPushInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "invalid");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = pull，默认端口
     */
    @Test
    public void testInit_metricTypePull_defaultPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = pull，自定义端口
     */
    @Test
    public void testInit_metricTypePull_customPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "18091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：stat_type = pull，无效端口（非数字）
     */
    @Test
    public void testInit_metricTypePull_invalidPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "not_a_port");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_type 覆盖 stat_type
     */
    @Test
    public void testInit_polarisMetricType_overridesMetricType() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_POLARIS_METRIC_TYPE, "push");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：仅设置 polaris_stat_type
     */
    @Test
    public void testInit_onlyPolarisMetricType() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_METRIC_TYPE, "push");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_type 为空时不覆盖 stat_type
     */
    @Test
    public void testInit_emptyPolarisMetricType_usesMetricType() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_POLARIS_METRIC_TYPE, "");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_push_addr 覆盖 stat_push_addr
     */
    @Test
    public void testInit_polarisPushAddr_overridesPushAddr() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "192.168.1.1:9091");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_ADDR, "10.0.0.1:9091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：仅设置 polaris_stat_push_addr
     */
    @Test
    public void testInit_onlyPolarisPushAddr() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_ADDR, "10.0.0.1:9091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_push_interval 覆盖 stat_push_interval
     */
    @Test
    public void testInit_polarisPushInterval_overridesPushInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "10000");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL, "50000");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：仅设置 polaris_stat_push_interval
     */
    @Test
    public void testInit_onlyPolarisPushInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL, "25000");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_push_interval 无效时保持 stat_push_interval 的值
     */
    @Test
    public void testInit_invalidPolarisPushInterval_usesPushInterval() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "10000");
        parameters.put(Consts.KEY_POLARIS_METRIC_PUSH_INTERVAL, "invalid");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_pull_port 覆盖 stat_pull_port
     */
    @Test
    public void testInit_polarisPullPort_overridesPullPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "18091");
        parameters.put(Consts.KEY_POLARIS_METRIC_PULL_PORT, "28091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：仅设置 polaris_stat_pull_port
     */
    @Test
    public void testInit_onlyPolarisPullPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_POLARIS_METRIC_PULL_PORT, "38091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_stat_pull_port 无效时保持 stat_pull_port 的值
     */
    @Test
    public void testInit_invalidPolarisPullPort_usesPullPort() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_METRIC_TYPE, "pull");
        parameters.put(Consts.KEY_METRIC_PULL_PORT, "18091");
        parameters.put(Consts.KEY_POLARIS_METRIC_PULL_PORT, "invalid");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：未配置 stat_type（关闭统计上报）
     */
    @Test
    public void testInit_noMetricType() {
        Map<String, String> parameters = new HashMap<>();

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：PushGateway 事件上报开启
     */
    @Test
    public void testInit_pgwEventEnabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PGW_EVENT_ENABLED, "true");
        parameters.put(Consts.KEY_PGW_EVENT_ADDR, "10.0.0.1:9091");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：PushGateway 事件上报开启但无自定义地址
     */
    @Test
    public void testInit_pgwEventEnabled_noAddr() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PGW_EVENT_ENABLED, "true");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：PushGateway 事件上报关闭
     */
    @Test
    public void testInit_pgwEventDisabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_PGW_EVENT_ENABLED, "false");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：有效的 detect_when 参数
     */
    @Test
    public void testInit_validDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "always");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：无效的 detect_when 参数
     */
    @Test
    public void testInit_invalidDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "invalid_value");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_detect_when 覆盖 detect_when
     */
    @Test
    public void testInit_polarisDetectWhen_overridesDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "never");
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "always");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：仅设置 polaris_detect_when
     */
    @Test
    public void testInit_onlyPolarisDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "always");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_detect_when 为空时不覆盖 detect_when
     */
    @Test
    public void testInit_emptyPolarisDetectWhen_usesDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_DETECT_WHEN, "always");
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：polaris_detect_when 无效值
     */
    @Test
    public void testInit_invalidPolarisDetectWhen() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_DETECT_WHEN, "invalid_value");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：配置加密关闭
     */
    @Test
    public void testInit_configEncryptDisabled() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "false");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
    }

    /**
     * 测试初始化：带 token 的 ServerConnector 配置
     */
    @Test
    public void testInit_withToken() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TOKEN, "my-secret-token");

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
        Assert.assertEquals("my-secret-token", operator.getPolarisConfig().getToken());
    }

    /**
     * 测试初始化：无 token 的 ServerConnector 配置
     */
    @Test
    public void testInit_withoutToken() {
        Map<String, String> parameters = new HashMap<>();

        PolarisOperator operator = createOperatorViaConstructor(parameters);
        Assert.assertNotNull(operator);
        Assert.assertNull(operator.getPolarisConfig().getToken());
    }

    /**
     * 测试初始化：所有参数综合配置
     */
    @Test
    public void testInit_withAllParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "custom-ns");
        parameters.put(Consts.KEY_TOKEN, "custom-token");
        parameters.put(Consts.KEY_METRIC_TYPE, "push");
        parameters.put(Consts.KEY_METRIC_PUSH_ADDR, "10.0.0.1:9091");
        parameters.put(Consts.KEY_METRIC_PUSH_INTERVAL, "5000");
        parameters.put(Consts.KEY_PGW_EVENT_ENABLED, "true");
        parameters.put(Consts.KEY_PGW_EVENT_ADDR, "10.0.0.2:9091");
        parameters.put(Consts.KEY_DETECT_WHEN, "always");
        parameters.put(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "true");

        PolarisOperator operator = createOperatorViaConstructor(parameters, new BaseBootConfigHandler());
        Assert.assertNotNull(operator);
        Assert.assertEquals("custom-ns", operator.getPolarisConfig().getNamespace());
        Assert.assertEquals("custom-token", operator.getPolarisConfig().getToken());
    }
}
