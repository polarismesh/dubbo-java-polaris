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

import static com.alibaba.dubbo.common.Constants.PATH_KEY;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.cluster.RouterFactory;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceChangeEvent;
import com.tencent.polaris.client.util.NamedThreadFactory;
import com.tencent.polaris.common.registry.Consts;
import com.tencent.polaris.common.registry.ConvertUtils;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.ExtensionConsts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolarisRegistry extends FailbackRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisRegistry.class);

    private static final TaskScheduler taskScheduler = new TaskScheduler();

    private final Map<String, Protocol> protocols = new HashMap<>();

    private final Set<URL> registeredInstances = new ConcurrentHashSet<>();

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private final Map<NotifyListener, ServiceListener> serviceListeners = new ConcurrentHashMap<>();

    private final PolarisOperator polarisOperator;

    private final URL routerURL;

    public PolarisRegistry(URL url) {
        super(url);
        ExtensionLoader<Protocol> extensionLoader = ExtensionLoader.getExtensionLoader(Protocol.class);
        Set<String> supportedExtensions = extensionLoader.getSupportedExtensions();
        for (String supportedExtension : supportedExtensions) {
            protocols.put(supportedExtension, extensionLoader.getExtension(supportedExtension));
        }
        polarisOperator = new PolarisOperator(url.getHost(), url.getPort(), url.getParameters());
        PolarisOperators.INSTANCE.addPolarisOperator(polarisOperator);
        this.routerURL = buildRouterURL(url.getHost(), url.getPort());
    }

    private URL buildRouterURL(String host, int port) {
        ExtensionLoader<RouterFactory> extensionLoader = ExtensionLoader.getExtensionLoader(RouterFactory.class);
        URL routerURL = null;
        if (extensionLoader.hasExtension(ExtensionConsts.PLUGIN_ROUTER_NAME)) {
            routerURL = new URL(Constants.ROUTE_PROTOCOL, host, port);
            routerURL = routerURL.setServiceInterface(Constants.ANY_VALUE);
            routerURL = routerURL.addParameter(Constants.ROUTER_KEY, ExtensionConsts.PLUGIN_ROUTER_NAME);
        }
        return routerURL;
    }

    public PolarisOperator getPolarisOperator() {
        return polarisOperator;
    }

    private int parsePort(String protocolStr, int port) {
        if (port > 0) {
            return port;
        }
        Protocol protocol = protocols.get(protocolStr);
        if (null == protocol) {
            return 0;
        }
        return protocol.getDefaultPort();
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
        int weight = url.getParameter(Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT);
        String version = url.getParameter(Constants.VERSION_KEY, "");
        polarisOperator.register(url.getServiceInterface(), url.getHost(), port, url.getProtocol(), version, weight,
                metadata);
        registeredInstances.add(url);
    }

    private boolean shouldRegister(URL url) {
        return protocols.containsKey(url.getProtocol());
    }

    @Override
    public void doUnregister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        LOGGER.info("[POLARIS] unregister service from polaris: {}", url.toString());
        int port = parsePort(url.getProtocol(), url.getPort());
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
        Instance[] instances = polarisOperator.getAvailableInstances(service);
        onInstances(url, listener, instances);
        LOGGER.info("[POLARIS] submit watch task for service {}", service);
        taskScheduler.submitWatchTask(new WatchTask(url, listener, service));
    }

    private class WatchTask implements Runnable {

        private final String service;

        private final ServiceListener serviceListener;

        private final NotifyListener listener;

        private final FetchTask fetchTask;

        public WatchTask(URL url, NotifyListener listener,
                String service) {
            this.service = service;
            this.listener = listener;
            fetchTask = new FetchTask(url, listener);
            serviceListener = new ServiceListener() {
                @Override
                public void onEvent(ServiceChangeEvent event) {
                    PolarisRegistry.taskScheduler.submitFetchTask(fetchTask);
                }
            };
        }

        @Override
        public void run() {
            boolean result = polarisOperator.watchService(service, serviceListener);
            if (result) {
                serviceListeners.put(listener, serviceListener);
                PolarisRegistry.taskScheduler.submitFetchTask(fetchTask);
                return;
            }
            PolarisRegistry.taskScheduler.submitWatchTask(this);
        }
    }

    private class FetchTask implements Runnable {

        private final String service;

        private final URL url;

        private final NotifyListener listener;

        public FetchTask(URL url, NotifyListener listener) {
            this.service = url.getServiceInterface();
            this.url = url;
            this.listener = listener;
        }

        @Override
        public void run() {
            Instance[] instances;
            try {
                instances = polarisOperator.getAvailableInstances(service);
            } catch (PolarisException e) {
                LOGGER.error("[POLARIS] fail to fetch instances for service {}: {}", service, e.toString());
                return;
            }
            onInstances(url, listener, instances);
        }
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
        if (null != routerURL) {
            urls.add(routerURL);
        }
        PolarisRegistry.this.notify(url, listener, urls);
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

    private static class TaskScheduler {

        private final ExecutorService fetchExecutor = Executors
                .newSingleThreadExecutor(new NamedThreadFactory("agent-fetch"));

        private final ExecutorService watchExecutor = Executors
                .newSingleThreadExecutor(new NamedThreadFactory("agent-retry-watch"));

        private final AtomicBoolean executorDestroyed = new AtomicBoolean(false);

        private final Object lock = new Object();

        void submitFetchTask(Runnable fetchTask) {
            if (executorDestroyed.get()) {
                return;
            }
            synchronized (lock) {
                if (executorDestroyed.get()) {
                    return;
                }
                fetchExecutor.submit(fetchTask);
            }
        }

        void submitWatchTask(Runnable watchTask) {
            if (executorDestroyed.get()) {
                return;
            }
            synchronized (lock) {
                if (executorDestroyed.get()) {
                    return;
                }
                watchExecutor.submit(watchTask);
            }
        }

        boolean isDestroyed() {
            return executorDestroyed.get();
        }

        void destroy() {
            synchronized (lock) {
                if (executorDestroyed.compareAndSet(false, true)) {
                    fetchExecutor.shutdown();
                    watchExecutor.shutdown();
                }
            }
        }
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        LOGGER.info("[polaris] unsubscribe service: {}", url.toString());
        taskScheduler.submitWatchTask(new Runnable() {
            @Override
            public void run() {
                ServiceListener serviceListener = serviceListeners.get(listener);
                if (null != serviceListener) {
                    polarisOperator.unwatchService(url.getServiceInterface(), serviceListener);
                }
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
