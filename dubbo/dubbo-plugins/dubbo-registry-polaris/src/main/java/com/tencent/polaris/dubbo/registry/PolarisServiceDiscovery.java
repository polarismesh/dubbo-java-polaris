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

package com.tencent.polaris.dubbo.registry;

import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceChangeEvent;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.rpc.UnWatchServiceRequest;
import com.tencent.polaris.api.rpc.WatchServiceRequest;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.registry.client.AbstractServiceDiscovery;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PolarisServiceDiscovery extends AbstractServiceDiscovery {

    private final  PolarisOperator operator;

    private final ConsumerAPI consumerAPI;

    private final Map<String, ServiceListener> listenerMap = new ConcurrentHashMap<>();

    private Map<String, Set<ServiceInstancesChangedListener>> serviceListeners = new ConcurrentHashMap<>();

    public PolarisServiceDiscovery(ApplicationModel applicationModel, URL url) {
        super(applicationModel, url);
        this.operator = PolarisOperators.loadOrStoreForGovernance(url.getHost(), url.getPort(), url.getParameters());
        this.consumerAPI = operator.getConsumerAPI();
    }

    @Override
    protected void doRegister(ServiceInstance instance) throws RuntimeException {
        Map<String, String> metadata = new HashMap<>(instance.getMetadata());
        metadata.remove(Consts.INSTANCE_WEIGHT);
        metadata.remove(Consts.INSTANCE_VERSION);
        if (CollectionUtils.isEmptyMap(metadata)) {
            metadata = new HashMap<>();
        }
        metadata.replaceAll((s, s2) -> StringUtils.defaultString(s2));
        operator.register(
                serviceName,
                instance.getHost(),
                instance.getPort(),
                "dubbo",
                instance.getMetadata(Consts.INSTANCE_VERSION, "1.0.0"),
                Integer.parseInt(instance.getMetadata(Consts.INSTANCE_WEIGHT, "100")),
                metadata
                );
    }

    @Override
    protected void doUnregister(ServiceInstance instance) {
        operator.deregister(
                serviceName,
                instance.getHost(),
                instance.getPort()
        );
    }

    @Override
    protected void doDestroy() throws Exception {
        operator.destroy();
    }

    @Override
    public Set<String> getServices() {
        return operator.getServices().stream().map(ServiceInfo::getService).collect(Collectors.toSet());
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) throws NullPointerException {
        Instance[] instances = operator.getAvailableInstances(serviceName, true);
        if (Objects.isNull(instances) || instances.length == 0) {
            return Collections.emptyList();
        }
        List<ServiceInstance> ret = new ArrayList<>(instances.length);
        for (Instance instance : instances) {
            DefaultServiceInstance serviceInstance =
                    new DefaultServiceInstance(
                            instance.getService(),
                            instance.getHost(), instance.getPort(),
                            ScopeModelUtil.getApplicationModel(registryURL.getScopeModel()));
            serviceInstance.setMetadata(instance.getMetadata());
            serviceInstance.setEnabled(!instance.isIsolated());
            serviceInstance.setHealthy(instance.isHealthy());
            ret.add(serviceInstance);
        }
        return ret;
    }

    @Override
    public void addServiceInstancesChangedListener(ServiceInstancesChangedListener
                                                               listener) throws NullPointerException, IllegalArgumentException {
        if (!instanceListeners.add(listener)) {
            return;
        }
        Set<String> services = listener.getServiceNames();
        for (String service : services) {
            serviceListeners.computeIfAbsent(service, name -> {
                ServiceListener serviceListener = new InnerServiceListener(service);
                listenerMap.put(service, serviceListener);

                WatchServiceRequest request = new WatchServiceRequest();
                request.setNamespace(operator.getPolarisConfig().getNamespace());
                request.setService(service);
                request.setListeners(Collections.singletonList(serviceListener));
                consumerAPI.watchService(request);
                return new ConcurrentHashSet<>();
            });

            serviceListeners.get(service).add(listener);
        }
    }

    @Override
    public void removeServiceInstancesChangedListener(ServiceInstancesChangedListener
                                                                  listener) throws IllegalArgumentException {
        if (!instanceListeners.remove(listener)) {
            return;
        }
        Set<String> services = listener.getServiceNames();
        for (String service : services) {
            Set<ServiceInstancesChangedListener> listeners = serviceListeners.get(service);
            if (CollectionUtils.isEmpty(listeners)) {
                serviceListeners.remove(service);

                ServiceListener serviceListener = listenerMap.remove(service);
                if (Objects.nonNull(serviceListener)) {
                    UnWatchServiceRequest request = UnWatchServiceRequest.UnWatchServiceRequestBuilder
                            .anUnWatchServiceRequest()
                            .namespace(operator.getPolarisConfig().getNamespace())
                            .service(service)
                            .listeners(Collections.singletonList(serviceListener))
                            .build();
                    consumerAPI.unWatchService(request);
                }
                continue;
            }
            listeners.remove(listener);
        }
    }

    private class InnerServiceListener implements ServiceListener{

        private final String service;

        private InnerServiceListener(String service) {
            this.service = service;
        }

        @Override
        public void onEvent(ServiceChangeEvent event) {
            String serviceName = event.getServiceKey().getService();
            List<ServiceInstance> serviceInstances = event.getAllInstances()
                    .stream()
                    .map((Function<Instance, ServiceInstance>) instance -> {
                        DefaultServiceInstance serviceInstance =
                                new DefaultServiceInstance(
                                        instance.getService(),
                                        instance.getHost(), instance.getPort(),
                                        ScopeModelUtil.getApplicationModel(registryURL.getScopeModel()));
                        serviceInstance.setMetadata(instance.getMetadata());
                        serviceInstance.setEnabled(!instance.isIsolated());
                        serviceInstance.setHealthy(instance.isHealthy());
                        return serviceInstance;
                    })
                    .collect(Collectors.toList());

            Set<ServiceInstancesChangedListener> listeners = serviceListeners.getOrDefault(service, Collections.emptySet());

            ServiceInstancesChangedEvent changedEvent = new ServiceInstancesChangedEvent(serviceName, serviceInstances);
            listeners.forEach(listener -> listener.onEvent(changedEvent));
        }
    }
}
