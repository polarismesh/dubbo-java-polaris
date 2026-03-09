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

package com.tencent.polaris.dubbo.loadbalance;

import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.common.router.InstanceInvoker;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractPolarisLoadBalance extends AbstractLoadBalance {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPolarisLoadBalance.class);

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        PolarisOperator operator = PolarisOperators.getGovernancePolarisOperator();
        if (operator == null) {
            LOGGER.warn("[POLARIS] PolarisOperator not initialized, falling back to random selection");
            return randomSelect(invokers);
        }

        try {
            List<Instance> instances;
            if (invokers.get(0) instanceof Instance) {
                instances = (List<Instance>) ((List<?>) invokers);
            } else {
                instances = new ArrayList<>();
                for (Invoker<T> invoker : invokers) {
                    instances.add(new InstanceInvoker<>(invoker, operator.getPolarisConfig().getNamespace()));
                }
            }

            String service = url.getServiceInterface();
            String hashKey = buildHashKey(url, invocation);
            Instance selected = operator.loadBalance(service, getLbPolicy(), hashKey, instances);
            if (selected instanceof Invoker) {
                return (Invoker<T>) selected;
            }

            LOGGER.warn("[POLARIS] loadBalance returned non-Invoker instance, falling back to random selection");
        } catch (Exception e) {
            LOGGER.error("[POLARIS] loadBalance failed, falling back to random selection", e);
        }
        return randomSelect(invokers);
    }

    private <T> Invoker<T> randomSelect(List<Invoker<T>> invokers) {
        return invokers.get(ThreadLocalRandom.current().nextInt(invokers.size()));
    }

    protected abstract String getLbPolicy();

    protected String buildHashKey(URL url, Invocation invocation) {
        return "";
    }
}
