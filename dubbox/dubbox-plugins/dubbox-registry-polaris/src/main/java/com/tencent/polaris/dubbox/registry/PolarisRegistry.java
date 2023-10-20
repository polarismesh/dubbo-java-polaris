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

package com.tencent.polaris.dubbox.registry;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.cluster.RouterFactory;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.registry.Consts;
import com.tencent.polaris.common.registry.ConvertUtils;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.ExtensionConsts;
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

import static com.alibaba.dubbo.common.Constants.CATEGORY_KEY;
import static com.alibaba.dubbo.common.Constants.DEFAULT_CATEGORY;
import static com.alibaba.dubbo.common.Constants.EMPTY_PROTOCOL;
import static com.alibaba.dubbo.common.Constants.PATH_KEY;

public class PolarisRegistry extends FailbackRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisRegistry.class);

    private final Set<URL> registeredInstances = new ConcurrentHashSet<>();

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private final Map<NotifyListener, ServiceListener> serviceListeners = new ConcurrentHashMap<>();

    private final PolarisOperator polarisOperator;

    public PolarisRegistry(URL url) {
        super(url);
        polarisOperator = PolarisOperators.INSTANCE.loadOrStore(url.getHost(), url.getPort(), url.getParameters());
    }

    @Override
    public void doRegister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        LOGGER.info("[POLARIS] register service to polaris: {}", url.toString());
        Map<String, String> metadata = new HashMap<>(url.getParameters());
        metadata.put(PATH_KEY, url.getPath());
        int port = url.getPort();
        if (port > 0) {
            int weight = url.getParameter(Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT);
            String version = url.getParameter(Constants.VERSION_KEY, "");
            polarisOperator.register(url.getServiceInterface(), url.getHost(), port, url.getProtocol(), version, weight,
                    metadata);
            registeredInstances.add(url);
        } else {
            LOGGER.warn("[POLARIS] skip register url {} for zero port value", url);
        }
    }

    private boolean shouldRegister(URL url) {
        return !StringUtils.equals(url.getProtocol(), Constants.CONSUMER);
    }

    @Override
    public void doUnregister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        LOGGER.info("[POLARIS] unregister service from polaris: {}", url.toString());
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

        serviceListeners.computeIfAbsent(listener, notifyListener -> {
            ServiceListener serviceListener = event -> {
                try {
                    Instance[] curInstances = polarisOperator.getAvailableInstances(service, true);
                    onInstances(url, listener, curInstances);
                } catch (PolarisException e) {
                    LOGGER.error("[POLARIS] fail to fetch instances for service {}: {}", service, e.toString());
                }
            };
            polarisOperator.watchService(service, serviceListener);
            return serviceListener;
        });
    }

    private void onInstances(URL url, NotifyListener listener, Instance[] instances) {
        LOGGER.info("[POLARIS] update instances count: {}, service: {}", null == instances ? 0 : instances.length,
                url.getServiceInterface());
        List<URL> urls = new ArrayList<>();
        if (null != instances) {
            for (Instance instance : instances) {
                urls.add(instanceToURL(instance));
            }
        }
        notify(url, listener, toUrlWithEmpty(url, urls));
    }

    private static URL instanceToURL(Instance instance) {
        Map<String, String> newMetadata = new HashMap<>(instance.getMetadata());
        boolean hasWeight = false;
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
                newMetadata.get(PATH_KEY),
                newMetadata);
    }

    private List<URL> toUrlWithEmpty(URL providerUrl, List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            LOGGER.warn("[POLARIS] received empty url address list, will clear current available addresses");
            URL empty = providerUrl
                    .setProtocol(EMPTY_PROTOCOL)
                    .addParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
            urls.add(empty);
        }
        return urls;
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        LOGGER.info("[polaris] unsubscribe service: {}", url.toString());
        ServiceListener serviceListener = serviceListeners.get(listener);
        if (serviceListener != null) {
            polarisOperator.unwatchService(url.getServiceInterface(), serviceListener);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
