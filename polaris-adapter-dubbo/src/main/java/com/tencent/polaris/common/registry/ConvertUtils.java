/*
 * Tencent is pleased to support the open source community by making dubbo-polaris-java available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

import com.tencent.polaris.api.pojo.CircuitBreakerStatus;
import com.tencent.polaris.api.pojo.CircuitBreakerStatus.Status;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.StatusDimension;

import java.util.*;

public class ConvertUtils {

    private static final String SEP_CIRCUIT_BREAKER = ",";

    private static final String SEP_CIRCUIT_BREAKER_VALUE = ":";

    public static String circuitBreakersToString(Instance instance) {
        List<String> values = new ArrayList<>();
        Collection<StatusDimension> statusDimensions = instance.getStatusDimensions();
        if (null != statusDimensions && statusDimensions.size() > 0) {
            for (StatusDimension statusDimension : statusDimensions) {
                CircuitBreakerStatus circuitBreakerStatus = instance.getCircuitBreakerStatus(statusDimension);
                if (null != circuitBreakerStatus) {
                    values.add(
                            statusDimension.getMethod() + SEP_CIRCUIT_BREAKER_VALUE + circuitBreakerStatus.getStatus()
                                    .name());
                }
            }
        }
        if (values.isEmpty()) {
            return "";
        }
        return String.join(SEP_CIRCUIT_BREAKER, values.toArray(new String[0]));
    }

    public static Map<StatusDimension, CircuitBreakerStatus> stringToCircuitBreakers(String value) {
        Map<StatusDimension, CircuitBreakerStatus> values = new HashMap<>();
        if (null == value || value.length() == 0) {
            return values;
        }

        String[] tokens = value.split(SEP_CIRCUIT_BREAKER);
        for (String token : tokens) {
            String[] splits = token.split(SEP_CIRCUIT_BREAKER_VALUE);
            if (splits.length != 2) {
                continue;
            }
            StatusDimension dimension = new StatusDimension(splits[0], null);
            CircuitBreakerStatus circuitBreakerStatus = new CircuitBreakerStatus(
                    "", Status.valueOf(splits[1]), 0);
            values.put(dimension, circuitBreakerStatus);
        }
        return values;
    }
}
