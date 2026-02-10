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

import com.tencent.polaris.api.config.consumer.OutlierDetectionConfig;
import com.tencent.polaris.api.config.global.StatReporterConfig;
import com.tencent.polaris.api.config.plugin.DefaultPlugins;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.plugin.circuitbreaker.entity.InstanceResource;
import com.tencent.polaris.api.plugin.circuitbreaker.entity.Resource;
import com.tencent.polaris.api.pojo.*;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.rpc.*;
import com.tencent.polaris.api.utils.CollectionUtils;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.api.flow.CircuitBreakerFlow;
import com.tencent.polaris.circuitbreak.api.pojo.CheckResult;
import com.tencent.polaris.circuitbreak.factory.CircuitBreakAPIFactory;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.pojo.ServiceRuleByProto;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.configuration.factory.ConfigFileServiceFactory;
import com.tencent.polaris.configuration.factory.ConfigFileServicePublishFactory;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.global.AdminConfigImpl;
import com.tencent.polaris.factory.config.global.ServerConnectorConfigImpl;
import com.tencent.polaris.plugins.event.pushgateway.PushGatewayEventReporterConfig;
import com.tencent.polaris.plugins.stat.prometheus.handler.PrometheusHandlerConfig;
import com.tencent.polaris.ratelimit.api.core.LimitAPI;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaRequest;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.factory.LimitAPIFactory;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.router.api.rpc.ProcessLoadBalanceRequest;
import com.tencent.polaris.router.api.rpc.ProcessLoadBalanceResponse;
import com.tencent.polaris.router.api.rpc.ProcessRoutersRequest;
import com.tencent.polaris.router.api.rpc.ProcessRoutersResponse;
import org.apache.dubbo.common.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PolarisOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisOperator.class);

    private final PolarisConfig polarisConfig;

    private SDKContext sdkContext;

    private ConsumerAPI consumerAPI;

    private ProviderAPI providerAPI;

    private LimitAPI limitAPI;

    private RouterAPI routerAPI;

    private CircuitBreakAPI circuitBreakAPI;

    private ConfigFileService configFileAPI;

    private ConfigFilePublishService configFilePublishAPI;

    PolarisOperator(PolarisOperators.OperatorType operatorType, String host, int port, Map<String, String> parameters, BootConfigHandler... handlers) {
        polarisConfig = new PolarisConfig(operatorType, host, port, parameters);
        init(operatorType, parameters, handlers);
    }

    private void init(PolarisOperators.OperatorType operatorType, Map<String, String> parameters, BootConfigHandler... handlers) {
        ConfigurationImpl configuration = (ConfigurationImpl) ConfigAPIFactory.defaultConfig();
        configuration.setDefault();
        if (null != handlers) {
            for (BootConfigHandler bootConfigHandler : handlers) {
                bootConfigHandler.handle(parameters, configuration);
            }
        }
        initServerConnectorConfig(configuration);
        initSDKContextConfig(configuration, parameters, polarisConfig);
        configuration.getGlobal().getAPI().setBindIP(NetUtils.getLocalHost());

        // 设置服务治理连接地址
        configuration.getGlobal().getServerConnector()
                .setAddresses(Collections.singletonList(polarisConfig.getDiscoverAddress()));
        // 设置配置中心连接地址
        configuration.getConfigFile().getServerConnector()
                .setAddresses(Collections.singletonList(polarisConfig.getConfigAddress()));
        sdkContext = SDKContext.initContextByConfig(configuration);
        consumerAPI = DiscoveryAPIFactory.createConsumerAPIByContext(sdkContext);
        providerAPI = DiscoveryAPIFactory.createProviderAPIByContext(sdkContext);
        limitAPI = LimitAPIFactory.createLimitAPIByContext(sdkContext);
        routerAPI = RouterAPIFactory.createRouterAPIByContext(sdkContext);
        circuitBreakAPI = CircuitBreakAPIFactory.createCircuitBreakAPIByContext(sdkContext);
        //
        configFileAPI = ConfigFileServiceFactory.createConfigFileService(sdkContext);
        configFilePublishAPI = ConfigFileServicePublishFactory.createConfigFilePublishService(sdkContext);
    }

    private void initSDKContextConfig(ConfigurationImpl configuration, Map<String, String> parameters, PolarisConfig polarisConfig) {

        PrometheusHandlerConfig prometheusHandlerConfig = configuration.getGlobal().getStatReporter()
                .getPluginConfig(StatReporterConfig.DEFAULT_REPORTER_PROMETHEUS, PrometheusHandlerConfig.class);

        AdminConfigImpl adminConfig = configuration.getGlobal().getAdmin();
        // stat reporter
        if (parameters.containsKey(Consts.KEY_METRIC_TYPE)) {
            String statType = parameters.get(Consts.KEY_METRIC_TYPE);
            switch (statType) {
                case "push":
                    String pushAddr = parameters.get(Consts.KEY_METRIC_PUSH_ADDR);
                    if (StringUtils.isBlank(pushAddr)) {
                        pushAddr = polarisConfig.getDiscoverAddress().split(":")[0] + ":9091";
                    }
                    configuration.getGlobal().getStatReporter().setEnable(true);
                    prometheusHandlerConfig.setType("push");
                    prometheusHandlerConfig.setAddress(Collections.singletonList(pushAddr));

                    // 默认为 10s
                    long interval = 10 * 1000L;
                    if (parameters.containsKey(Consts.KEY_METRIC_PUSH_INTERVAL)) {
                        try {
                            interval = Integer.parseInt(parameters.get(Consts.KEY_METRIC_PUSH_INTERVAL));
                        } catch (NumberFormatException ignore) {}
                    }
                    prometheusHandlerConfig.setPushInterval(interval);
                    break;
                case "pull":
                    int port = 9091;
                    if (parameters.containsKey(Consts.KEY_METRIC_PULL_PORT)) {
                        try {
                            port = Integer.parseInt(parameters.get(Consts.KEY_METRIC_PULL_PORT));
                        } catch (NumberFormatException ignore) {}
                    }
                    configuration.getGlobal().getStatReporter().setEnable(true);
                    prometheusHandlerConfig.setType("pull");
                    adminConfig.setPort(port);
                    break;
            }
        } else {
            configuration.getGlobal().getStatReporter().setEnable(false);
        }
        configuration.getGlobal().getStatReporter().setPluginConfig("prometheus", prometheusHandlerConfig);

        // event reporter
        PushGatewayEventReporterConfig pushGatewayEventReporterConfig = configuration.getGlobal().getEventReporter()
                .getPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE, PushGatewayEventReporterConfig.class);
        configuration.getGlobal().getEventReporter().getReporters().add(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE);
        if (parameters.containsKey(Consts.KEY_PGW_EVENT_ENABLED)){
            boolean enabled = Boolean.parseBoolean(parameters.get(Consts.KEY_PGW_EVENT_ENABLED));
            pushGatewayEventReporterConfig.setEnable(enabled);
            if (parameters.containsKey(Consts.KEY_PGW_EVENT_ADDR)){
                pushGatewayEventReporterConfig.setAddress(Collections.singletonList(parameters.get(Consts.KEY_PGW_EVENT_ADDR)));
            }
            pushGatewayEventReporterConfig.setEventQueueSize(1000);
            pushGatewayEventReporterConfig.setMaxBatchSize(100);
            pushGatewayEventReporterConfig.setNamespace("polaris");
            pushGatewayEventReporterConfig.setService("polaris.pushgateway");
        }

        configuration.getGlobal().getEventReporter().setPluginConfig(DefaultPlugins.PUSH_GATEWAY_EVENT_REPORTER_TYPE, pushGatewayEventReporterConfig);
        // 设置主动探测
        if (parameters.containsKey(Consts.KEY_DETECT_WHEN)) {
            String detectWhen = parameters.get(Consts.KEY_DETECT_WHEN);
            try {
                configuration.getConsumer().getOutlierDetection().setWhen(OutlierDetectionConfig.When.valueOf(detectWhen));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid detectWhen value: {}, valid values are: {}",
                        detectWhen, Arrays.toString(OutlierDetectionConfig.When.values()));
            }
        }

        configuration.getConfigFile().getConfigFilterConfig().setEnable(Boolean.parseBoolean(parameters.getOrDefault(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "true")));
        configuration.getConfigFile().getConfigFilterConfig().getChain().add("crypto");
        configuration.getConfigFile().getConfigFilterConfig().getPlugin().put("crypto", Collections.singletonMap("type", "AES"));
        configuration.getConsumer().getServiceRouter().getPlugin().put("recoverRouter", Collections.singletonMap("excludeCircuitBreakInstances", "false"));
        configuration.getConsumer().getServiceRouter().getPlugin().put("recoverRouter", Collections.singletonMap("allRecoverEnable", "false"));
    }

    private void initServerConnectorConfig(ConfigurationImpl configuration) {
        if (StringUtils.isNotBlank(polarisConfig.getToken())) {
            // 设置服务治理 ServerConnector 的配置
            ServerConnectorConfigImpl connector = configuration.getGlobal().getServerConnector();
            if (Objects.nonNull(connector)) {
                connector.setToken(polarisConfig.getToken());
            }
            List<ServerConnectorConfigImpl> connectors = configuration.getGlobal().getServerConnectors();
            if (CollectionUtils.isNotEmpty(connectors)) {
                connectors.forEach(connectorConfig -> connectorConfig.setToken(polarisConfig.getToken()));
            }

            // 设置配置中心 ServerConnector 的配置
            ServerConnectorConfigImpl configConnector = configuration.getConfigFile().getServerConnector();
            if (Objects.nonNull(connector)) {
                configConnector.setToken(polarisConfig.getToken());
            }
        }
    }

    /**
     * 服务注册
     */
    public void register(String service, String host, int port, String protocol, String version, int weight,
                         Map<String, String> metadata) {
        LOGGER.info(
                "[POLARIS] start to register: service {}, host {}, port {}， protocol {}, version {}, weight {}, metadata {}",
                service, host, port, protocol, version, weight, metadata);
        String namespace = polarisConfig.getNamespace();
        int ttl = polarisConfig.getTtl();
        String token = polarisConfig.getToken();
        InstanceRegisterRequest instanceRegisterRequest = new InstanceRegisterRequest();
        instanceRegisterRequest.setNamespace(namespace);
        instanceRegisterRequest.setService(service);
        instanceRegisterRequest.setHost(host);
        instanceRegisterRequest.setPort(port);
        instanceRegisterRequest.setWeight(weight);
        instanceRegisterRequest.setVersion(version);
        instanceRegisterRequest.setTtl(ttl);
        instanceRegisterRequest.setMetadata(metadata);
        instanceRegisterRequest.setProtocol(protocol);
        instanceRegisterRequest.setToken(token);
        InstanceRegisterResponse response = providerAPI.registerInstance(instanceRegisterRequest);
        LOGGER.info("register result is {} for service {}", response, service);
    }

    public void deregister(String service, String host, int port) {
        LOGGER.info("[POLARIS] start to deregister: service {}, host {}, port {}", service, host, port);
        InstanceDeregisterRequest instanceDeregisterRequest = new InstanceDeregisterRequest();
        instanceDeregisterRequest.setNamespace(polarisConfig.getNamespace());
        instanceDeregisterRequest.setService(service);
        instanceDeregisterRequest.setPort(port);
        instanceDeregisterRequest.setHost(host);
        instanceDeregisterRequest.setToken(polarisConfig.getToken());
        providerAPI.deRegister(instanceDeregisterRequest);
        LOGGER.info("[POLARIS] deregister service {}", service);
    }

    public boolean watchService(String service, ServiceListener listener) {
        WatchServiceRequest watchServiceRequest = new WatchServiceRequest();
        watchServiceRequest.setNamespace(polarisConfig.getNamespace());
        watchServiceRequest.setService(service);
        watchServiceRequest.setListeners(Collections.singletonList(listener));
        return consumerAPI.watchService(watchServiceRequest).isSuccess();
    }

    public void unwatchService(String service, ServiceListener serviceListener) {
        UnWatchServiceRequest watchServiceRequest = UnWatchServiceRequest.UnWatchServiceRequestBuilder
                .anUnWatchServiceRequest()
                .namespace(polarisConfig.getNamespace())
                .service(service)
                .listeners(Collections.singletonList(serviceListener))
                .build();
        consumerAPI.unWatchService(watchServiceRequest);
    }

    /**
     * 调用CONSUMER_API获取实例信息
     *
     * @param service 服务的service
     * @return Polaris选择的Instance对象
     */
    public Instance[] getAvailableInstances(String service, boolean includeCircuitBreakInstances) {
        GetHealthyInstancesRequest request = new GetHealthyInstancesRequest();
        request.setNamespace(polarisConfig.getNamespace());
        request.setService(service);
        request.setIncludeCircuitBreakInstances(includeCircuitBreakInstances);
        InstancesResponse instances = consumerAPI.getHealthyInstances(request);
        return instances.getInstances();
    }

    /**
     * 调用CONSUMER_API上报服务请求结果
     *
     * @param delay 本次服务调用延迟，单位ms
     */
    public void reportInvokeResult(String service, String method, String host, int port, String callerIp, long delay, RetStatus retStatus,
                                   int code) {
        ServiceCallResult serviceCallResult = new ServiceCallResult();
        serviceCallResult.setNamespace(polarisConfig.getNamespace());
        serviceCallResult.setService(service);
        serviceCallResult.setMethod(method);
        serviceCallResult.setHost(host);
        serviceCallResult.setPort(port);
        serviceCallResult.setDelay(delay);
        serviceCallResult.setRetStatus(retStatus);
        serviceCallResult.setRetCode(code);
        serviceCallResult.setCallerIp(callerIp);
        serviceCallResult.setCallerService(new ServiceKey(polarisConfig.getNamespace(), ""));
        try {
            consumerAPI.updateServiceCallResult(serviceCallResult);
        } catch (PolarisException e) {
            LOGGER.error(formatCode(e.getCode()),
                    e.getMessage(),
                    "",
                    "report invoke result fail");
        }
    }

    public List<Instance> route(String service, String method, Set<RouteArgument> arguments, List<Instance> instances) {
        ServiceKey serviceKey = new ServiceKey(polarisConfig.getNamespace(), service);
        DefaultServiceInstances defaultServiceInstances = new DefaultServiceInstances(serviceKey, instances);
        SourceService serviceInfo = new SourceService();
        serviceInfo.setArguments(arguments);
        ProcessRoutersRequest request = new ProcessRoutersRequest();
        request.setDstInstances(defaultServiceInstances);
        request.setMethod(method);
        request.setSourceService(serviceInfo);
        ProcessRoutersResponse processRoutersResponse = routerAPI.processRouters(request);
        return processRoutersResponse.getServiceInstances().getInstances();
    }

    public Instance loadBalance(String service, String hashKey, List<Instance> instances) {
        ServiceKey serviceKey = new ServiceKey(polarisConfig.getNamespace(), service);
        DefaultServiceInstances defaultServiceInstances = new DefaultServiceInstances(serviceKey, instances);
        ProcessLoadBalanceRequest processLoadBalanceRequest = new ProcessLoadBalanceRequest();
        processLoadBalanceRequest.setDstInstances(defaultServiceInstances);
        Criteria criteria = new Criteria();
        criteria.setHashKey(hashKey);
        processLoadBalanceRequest.setCriteria(criteria);
        ProcessLoadBalanceResponse processLoadBalanceResponse = routerAPI.processLoadBalance(processLoadBalanceRequest);
        return processLoadBalanceResponse.getTargetInstance();
    }

    public boolean checkCircuitBreakerPassing(Instance instance) {
        CircuitBreakerStatus circuitBreakerStatus = instance.getCircuitBreakerStatus();
        if (null != circuitBreakerStatus) {
            return circuitBreakerStatus.getStatus() != CircuitBreakerStatus.Status.OPEN;
        }
        Resource resource = new InstanceResource(new ServiceKey(instance.getNamespace(), instance.getService()),
                instance.getHost(), instance.getPort(), new ServiceKey());
        CircuitBreakerFlow circuitBreakerFlow = getSdkContext().getValueContext().getValue(
                CircuitBreakerFlow.class.getCanonicalName());
        if (null != circuitBreakerFlow) {
            CheckResult check = circuitBreakerFlow.check(resource);
            return check.isPass();
        }
        return true;
    }

    /**
     * 调用LIMIT_API进行服务限流
     *
     * @return 是否通过，为false则需要对本次请求限流
     */
    public QuotaResponse getQuota(String service, String method, Set<Argument> arguments) {
        QuotaRequest quotaRequest = new QuotaRequest();
        quotaRequest.setNamespace(polarisConfig.getNamespace());
        quotaRequest.setService(service);
        quotaRequest.setMethod(method);
        quotaRequest.setArguments(arguments);
        quotaRequest.setCount(1);
        return limitAPI.getQuota(quotaRequest);
    }

    public ServiceRule getServiceRule(String service, EventType eventType) {
        if (StringUtils.isBlank(service)) {
            return new ServiceRuleByProto();
        }
        GetServiceRuleRequest getServiceRuleRequest = new GetServiceRuleRequest();
        getServiceRuleRequest.setNamespace(polarisConfig.getNamespace());
        getServiceRuleRequest.setService(service);
        getServiceRuleRequest.setRuleType(eventType);
        ServiceRuleResponse serviceRule = consumerAPI.getServiceRule(getServiceRuleRequest);
        return serviceRule.getServiceRule();
    }

    public List<ServiceInfo> getServices() {
        GetServicesRequest getServicesRequest = new GetServicesRequest();
        getServicesRequest.setNamespace(polarisConfig.getNamespace());
        ServicesResponse services = consumerAPI.getServices(getServicesRequest);
        return services.getServices();
    }

    public PolarisConfig getPolarisConfig() {
        return polarisConfig;
    }

    public void destroy() {
        sdkContext.close();
    }

    public SDKContext getSdkContext() {
        return sdkContext;
    }

    public ConsumerAPI getConsumerAPI() {
        return consumerAPI;
    }

    public ProviderAPI getProviderAPI() {
        return providerAPI;
    }

    public LimitAPI getLimitAPI() {
        return limitAPI;
    }

    public RouterAPI getRouterAPI() {
        return routerAPI;
    }

    public ConfigFileService getConfigFileAPI() {
        return configFileAPI;
    }

    public ConfigFilePublishService getConfigFilePublishAPI() {
        return configFilePublishAPI;
    }

    public CircuitBreakAPI getCircuitBreakAPI() {
        return circuitBreakAPI;
    }

    protected static String formatCode(Object val) {
        return "POLARIS:" + val;
    }
}