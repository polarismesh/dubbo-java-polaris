/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.polaris.common.registry;

import com.tencent.polaris.api.config.Configuration;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.DefaultServiceInstances;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.pojo.RouteArgument;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.pojo.SourceService;
import com.tencent.polaris.api.rpc.Criteria;
import com.tencent.polaris.api.rpc.GetHealthyInstancesRequest;
import com.tencent.polaris.api.rpc.GetServiceRuleRequest;
import com.tencent.polaris.api.rpc.InstanceDeregisterRequest;
import com.tencent.polaris.api.rpc.InstanceRegisterRequest;
import com.tencent.polaris.api.rpc.InstanceRegisterResponse;
import com.tencent.polaris.api.rpc.InstancesResponse;
import com.tencent.polaris.api.rpc.ServiceCallResult;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.api.rpc.UnWatchServiceRequest;
import com.tencent.polaris.api.rpc.WatchServiceRequest;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.configuration.api.core.ConfigFile;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.configuration.factory.ConfigFileServiceFactory;
import com.tencent.polaris.factory.ConfigAPIFactory;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import com.tencent.polaris.factory.api.RouterAPIFactory;
import com.tencent.polaris.factory.config.ConfigurationImpl;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shade.polaris.com.google.protobuf.InvalidProtocolBufferException;
import shade.polaris.com.google.protobuf.Message;
import shade.polaris.com.google.protobuf.Message.Builder;
import shade.polaris.com.google.protobuf.util.JsonFormat;
import shade.polaris.com.google.protobuf.util.JsonFormat.Parser;

public class PolarisOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisOperator.class);

    private final PolarisConfig polarisConfig;

    private SDKContext sdkContext;

    private ConsumerAPI consumerAPI;

    private ProviderAPI providerAPI;

    private LimitAPI limitAPI;

    private RouterAPI routerAPI;

    private ConfigFileService configFileService;

    private final Object lock = new Object();

    private final Map<String, TimedCache<Message>> messageCache = new ConcurrentHashMap<>();

    public PolarisOperator(String host, int port, Map<String, String> parameters) {
        polarisConfig = new PolarisConfig(host, port, parameters);
        init();
    }

    private void init() {
        Configuration configuration = ConfigAPIFactory.defaultConfig();
        ((ConfigurationImpl) configuration).setDefault();
        ((ConfigurationImpl) configuration).getGlobal().getServerConnector()
                .setAddresses(Collections.singletonList(polarisConfig.getRegistryAddress()));
        ((ConfigurationImpl) configuration).getConfigFile().getServerConnector()
                .setAddresses(Collections.singletonList(polarisConfig.getConfigAddress()));
        if (polarisConfig.getTimeout() > 0) {
            ((ConfigurationImpl) configuration).getGlobal().getAPI().setTimeout(polarisConfig.getTimeout());
        }
        sdkContext = SDKContext.initContextByConfig(configuration);
        consumerAPI = DiscoveryAPIFactory.createConsumerAPIByContext(sdkContext);
        providerAPI = DiscoveryAPIFactory.createProviderAPIByContext(sdkContext);
        limitAPI = LimitAPIFactory.createLimitAPIByContext(sdkContext);
        routerAPI = RouterAPIFactory.createRouterAPIByContext(sdkContext);
        configFileService = ConfigFileServiceFactory.createConfigFileService(sdkContext);
    }

    public void destroy() {
        sdkContext.close();
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
    public Instance[] getAvailableInstances(String service) {
        GetHealthyInstancesRequest request = new GetHealthyInstancesRequest();
        request.setNamespace(polarisConfig.getNamespace());
        request.setService(service);
        InstancesResponse instances = consumerAPI.getHealthyInstances(request);
        return instances.getInstances();
    }

    /**
     * 调用CONSUMER_API上报服务请求结果
     *
     * @param delay 本次服务调用延迟，单位ms
     */
    public void reportInvokeResult(String service, String method, String host, int port, long delay, boolean success,
            int code) {
        ServiceCallResult serviceCallResult = new ServiceCallResult();
        serviceCallResult.setNamespace(polarisConfig.getNamespace());
        serviceCallResult.setService(service);
        serviceCallResult.setMethod(method);
        serviceCallResult.setHost(host);
        serviceCallResult.setPort(port);
        serviceCallResult.setDelay(delay);
        serviceCallResult.setRetStatus(success ? RetStatus.RetSuccess : RetStatus.RetFail);
        serviceCallResult.setRetCode(code);
        consumerAPI.updateServiceCallResult(serviceCallResult);
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

    public Message getConfigAsMessage(String configGroup, String fileName, Builder builder) {
        ConfigFile configFile = configFileService.getConfigFile(polarisConfig.getNamespace(), configGroup, fileName);
        String content = configFile.getContent();
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String ruleHash = DigestUtils.md2Hex(content.getBytes());
        TimedCache<Message> messageTimedCache = messageCache.get(ruleHash);
        if (null != messageTimedCache && !messageTimedCache.isExpired()) {
            return messageTimedCache.getValue();
        }
        synchronized (lock) {
            messageTimedCache = messageCache.get(ruleHash);
            if (null != messageTimedCache && !messageTimedCache.isExpired()) {
                return messageTimedCache.getValue();
            }
            boolean hasError = false;
            Parser parser = JsonFormat.parser();
            try {
                parser.merge(content, builder);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("[POLARIS] fail to unmarshal content {} to message", content, e);
                hasError = true;
            }
            if (hasError) {
                messageTimedCache = new TimedCache<>(null);
            } else {
                messageTimedCache = new TimedCache<>(builder.build());
            }
            messageCache.put(ruleHash, messageTimedCache);
        }
        return messageTimedCache.getValue();
    }

    public ServiceRule getServiceRule(String service, EventType eventType) {
        GetServiceRuleRequest getServiceRuleRequest = new GetServiceRuleRequest();
        getServiceRuleRequest.setNamespace(polarisConfig.getNamespace());
        getServiceRuleRequest.setService(service);
        getServiceRuleRequest.setRuleType(eventType);
        ServiceRuleResponse serviceRule = consumerAPI.getServiceRule(getServiceRuleRequest);
        return serviceRule.getServiceRule();
    }

    public PolarisConfig getPolarisConfig() {
        return polarisConfig;
    }
}
