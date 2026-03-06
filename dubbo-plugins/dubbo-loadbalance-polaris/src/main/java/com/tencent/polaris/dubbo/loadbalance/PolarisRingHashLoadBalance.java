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

import com.tencent.polaris.api.config.consumer.LoadBalanceConfig;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;

public class PolarisRingHashLoadBalance extends AbstractPolarisLoadBalance {

    private static final String HASH_ARGUMENTS = "hash.arguments";

    private static final String DEFAULT_HASH_ARGUMENTS = "0";

    @Override
    protected String getLbPolicy() {
        return LoadBalanceConfig.LOAD_BALANCE_RING_HASH;
    }

    @Override
    protected String buildHashKey(URL url, Invocation invocation) {
        String methodName = invocation.getMethodName();
        String hashArguments = url.getMethodParameter(methodName, HASH_ARGUMENTS, DEFAULT_HASH_ARGUMENTS);
        Object[] arguments = invocation.getArguments();
        StringBuilder hashKeyBuilder = new StringBuilder(methodName);
        if (arguments != null && arguments.length > 0) {
            String[] indices = hashArguments.split(",");
            for (String index : indices) {
                try {
                    int idx = Integer.parseInt(index.trim());
                    if (idx >= 0 && idx < arguments.length && arguments[idx] != null) {
                        hashKeyBuilder.append(arguments[idx]);
                    }
                } catch (NumberFormatException e) {
                    // skip invalid index
                }
            }
        }
        return hashKeyBuilder.toString();
    }
}
