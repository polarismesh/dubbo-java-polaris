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

import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceChangeEvent;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.common.utils.ConvertUtils;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.rpc.cluster.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;

public class PolarisRegistry extends FailbackRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisRegistry.class);

    private final Set<URL> registeredInstances = new ConcurrentHashSet<>();

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private final Map<URL, Set<NotifyListener>> dubboListeners = new ConcurrentHashMap<>();

    private final Map<URL, ServiceListener> serviceListeners = new ConcurrentHashMap<>();
    private final PolarisOperator polarisOperator;

    public PolarisRegistry(URL url) {
        super(url);
        polarisOperator = PolarisOperators.loadOrStoreForGovernance(url.getHost(), url.getPort(), url.getParameters());
    }

    @Override
    public void doRegister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        LOGGER.info("[POLARIS] register service to polaris: {}", url);
        Map<String, String> metadata = new HashMap<>(url.getParameters());
        metadata.put(CommonConstants.PATH_KEY, url.getPath());
        int port = url.getPort();
        if (port > 0) {
            int weight = url.getParameter(Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT);
            String version = url.getParameter(CommonConstants.VERSION_KEY, "1.0.0");
            polarisOperator.register(url.getServiceInterface(), url.getHost(), port, url.getProtocol(), version, weight,
                    metadata);
            registeredInstances.add(url);
        } else {
            LOGGER.warn("[POLARIS] skip register url {} for zero port value", url);
        }
    }

    private boolean shouldRegister(URL url) {
        return StringUtils.equals(url.getSide(), CommonConstants.PROVIDER);
    }

    @Override
    public void doUnregister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        LOGGER.info("[POLARIS] unregister service from polaris: {}", url);
        int port = url.getPort();
        if (port > 0) {
            polarisOperator.deregister(url.getServiceInterface(), url.getHost(), url.getPort());
            registeredInstances.remove(url);
        }
    }

    @Override
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            super.destroy();
            Collection<URL> urls = Collections.unmodifiableCollection(registeredInstances);
            for (URL url : urls) {
                doUnregister(url);
            }
            polarisOperator.destroy();
        }
    }

    @Override
    public void doSubscribe(URL url, NotifyListener listener) {
        String service = url.getServiceInterface();
        Instance[] instances = polarisOperator.getAvailableInstances(service, true);
        onInstances(url, listener, instances);
        LOGGER.info("[POLARIS] submit watch task for service {}", service);

        dubboListeners.computeIfAbsent(url, s -> new ConcurrentHashSet<>());
        dubboListeners.get(url).add(listener);

        serviceListeners.computeIfAbsent(url, dubboUrl -> {
            ServiceListener serviceListener = new DubboServiceListener(url, this);
            polarisOperator.watchService(service, serviceListener);
            return serviceListener;
        });
    }

    private void onInstances(URL url, NotifyListener listener, Instance[] instances) {
        String requireInterface = url.getServiceInterface();
        LOGGER.info("[POLARIS] update instances count: {}, service: {}", null == instances ? 0 : instances.length,
                requireInterface);
        List<URL> urls = new ArrayList<>();
        if (null != instances) {
            for (Instance instance : instances) {
                urls.add(instanceToURL(requireInterface, instance));
            }
        }
        notify(url, listener, toUrlWithEmpty(url, urls));
    }

    private static URL instanceToURL(String requireInterface, Instance instance) {
        Map<String, String> newMetadata = new HashMap<>(instance.getMetadata());
        boolean hasWeight = false;
        newMetadata.put("interface", requireInterface);
        if (newMetadata.containsKey(Constants.WEIGHT_KEY)) {
            String weightStr = newMetadata.get(Constants.WEIGHT_KEY);
            try {
                int weightValue = Integer.parseInt(weightStr);
                if (weightValue == instance.getWeight()) {
                    hasWeight = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (!hasWeight) {
            newMetadata.put(Constants.WEIGHT_KEY, Integer.toString(instance.getWeight()));
        }
        newMetadata.put(Consts.INSTANCE_KEY_ID, instance.getId());
        newMetadata.put(Consts.INSTANCE_KEY_HEALTHY, Boolean.toString(instance.isHealthy()));
        newMetadata.put(Consts.INSTANCE_KEY_ISOLATED, Boolean.toString(instance.isIsolated()));
        newMetadata.put(Consts.INSTANCE_KEY_CIRCUIT_BREAKER, ConvertUtils.circuitBreakersToString(instance));
        return new URL(instance.getProtocol(),
                instance.getHost(),
                instance.getPort(),
                newMetadata.get(CommonConstants.PATH_KEY),
                newMetadata);
    }

    private List<URL> toUrlWithEmpty(URL providerUrl, List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            LOGGER.warn("[POLARIS] received empty url address list, will clear current available addresses");
            URL empty = URLBuilder.from(providerUrl)
                    .setProtocol(EMPTY_PROTOCOL)
                    .addParameter(CATEGORY_KEY, DEFAULT_CATEGORY)
                    .build();
            urls.add(empty);
        }
        return urls;
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        LOGGER.info("[polaris] unsubscribe service: {}", url.toString());
        ServiceListener serviceListener = serviceListeners.get(listener);
        if (null != serviceListener) {
            polarisOperator.unwatchService(url.getServiceInterface(), serviceListener);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private static class DubboServiceListener implements ServiceListener {

        private final URL url;

        private final String service;

        private final PolarisRegistry registry;

        private DubboServiceListener(URL url, PolarisRegistry registry) {
            this.url = url;
            this.service = url.getServiceInterface();
            this.registry = registry;
        }

        @Override
        public void onEvent(ServiceChangeEvent serviceChangeEvent) {
            try {
                Set<NotifyListener> listeners = registry.dubboListeners.getOrDefault(url, Collections.emptySet());
                Instance[] curInstances = registry.polarisOperator.getAvailableInstances(service, true);
                for (NotifyListener listener : listeners) {
                    registry.onInstances(url, listener, curInstances);
                }
            } catch (PolarisException e) {
                LOGGER.error("[POLARIS] fail to fetch instances for service {}: {}", service, e.toString());
            }
        }
    }
}
