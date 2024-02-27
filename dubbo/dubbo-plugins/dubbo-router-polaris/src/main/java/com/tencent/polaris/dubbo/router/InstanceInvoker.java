/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
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

import com.tencent.polaris.api.pojo.CircuitBreakerStatus;
import com.tencent.polaris.api.pojo.DefaultInstance;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.StatusDimension;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import com.tencent.polaris.common.registry.DubboServiceInfo;
import com.tencent.polaris.common.utils.Consts;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceInvoker<T> implements Instance, Invoker<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceInvoker.class);

    private final Invoker<T> invoker;

    private final DefaultInstance defaultInstance;

    public InstanceInvoker(Invoker<T> invoker, DubboServiceInfo serviceInfo, String namespace) {
        this.invoker = invoker;
        defaultInstance = new DefaultInstance();
        defaultInstance.setNamespace(namespace);
        URL url = invoker.getUrl();
        defaultInstance.setService(serviceInfo.getService());
        defaultInstance.setHost(url.getHost());
        defaultInstance.setPort(url.getPort());
        defaultInstance.setId(url.getParameter(Consts.INSTANCE_KEY_ID));
        defaultInstance.setHealthy(Boolean.parseBoolean(url.getParameter(Consts.INSTANCE_KEY_HEALTHY)));
        defaultInstance.setIsolated(Boolean.parseBoolean(url.getParameter(Consts.INSTANCE_KEY_ISOLATED)));
        defaultInstance.setVersion(url.getParameter(CommonConstants.VERSION_KEY));
        defaultInstance.setWeight(url.getParameter(Constants.WEIGHT_KEY, 100));
        defaultInstance.setMetadata(url.getParameters());
        LOGGER.debug("[POLARIS] construct instance from invoker, url {}, instance {}", url, defaultInstance);
    }

    @Override
    public Class<T> getInterface() {
        return invoker.getInterface();
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    @Override
    public URL getUrl() {
        return invoker.getUrl();
    }

    @Override
    public boolean isAvailable() {
        return invoker.isAvailable();
    }

    @Override
    public void destroy() {
        invoker.destroy();
    }

    @Override
    public String getNamespace() {
        return defaultInstance.getNamespace();
    }

    @Override
    public String getService() {
        return defaultInstance.getService();
    }

    @Override
    public String getRevision() {
        return defaultInstance.getRevision();
    }

    @Override
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        return defaultInstance.getCircuitBreakerStatus();
    }

    @Override
    public Collection<StatusDimension> getStatusDimensions() {
        return defaultInstance.getStatusDimensions();
    }

    @Override
    public CircuitBreakerStatus getCircuitBreakerStatus(StatusDimension statusDimension) {
        return defaultInstance.getCircuitBreakerStatus(statusDimension);
    }

    @Override
    public boolean isHealthy() {
        return defaultInstance.isHealthy();
    }

    @Override
    public boolean isIsolated() {
        return defaultInstance.isIsolated();
    }

    @Override
    public String getProtocol() {
        return defaultInstance.getProtocol();
    }

    @Override
    public String getId() {
        return defaultInstance.getId();
    }

    @Override
    public String getHost() {
        return defaultInstance.getHost();
    }

    @Override
    public int getPort() {
        return defaultInstance.getPort();
    }

    @Override
    public String getVersion() {
        return defaultInstance.getVersion();
    }

    @Override
    public Map<String, String> getMetadata() {
        return defaultInstance.getMetadata();
    }

    @Override
    public boolean isEnableHealthCheck() {
        return defaultInstance.isEnableHealthCheck();
    }

    @Override
    public String getRegion() {
        return defaultInstance.getRegion();
    }

    @Override
    public String getZone() {
        return defaultInstance.getZone();
    }

    @Override
    public String getCampus() {
        return defaultInstance.getCampus();
    }

    @Override
    public int getPriority() {
        return defaultInstance.getPriority();
    }

    @Override
    public int getWeight() {
        return defaultInstance.getWeight();
    }

    @Override
    public String getLogicSet() {
        return defaultInstance.getLogicSet();
    }

    @Override
    public int compareTo(Instance o) {
        return defaultInstance.compareTo(o);
    }

    private static final String SEP_CIRCUIT_BREAKER = ",";

    private static final String SEP_CIRCUIT_BREAKER_VALUE = ":";

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InstanceInvoker)) {
            return false;
        }
        InstanceInvoker<?> that = (InstanceInvoker<?>) o;
        return Objects.equals(defaultInstance, that.defaultInstance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultInstance);
    }
}
