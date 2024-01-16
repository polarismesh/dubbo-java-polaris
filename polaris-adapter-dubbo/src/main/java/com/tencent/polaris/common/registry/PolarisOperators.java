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

package com.tencent.polaris.common.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PolarisOperators {

    enum OperatorType {
        GOVERNANCE,
        CONFIG,
    }

    private final Map<OperatorType, Map<String, PolarisOperator>> polarisOperatorMap;

    private PolarisOperators() {
        polarisOperatorMap = new ConcurrentHashMap<>();
        polarisOperatorMap.put(OperatorType.GOVERNANCE, new ConcurrentHashMap<>());
        polarisOperatorMap.put(OperatorType.CONFIG, new ConcurrentHashMap<>());
    }

    public static final PolarisOperators INSTANCE = new PolarisOperators();

    public PolarisOperator loadOrStoreForGovernance(String host, int port, Map<String, String> parameters) {
        Map<String, PolarisOperator> operatorMap = polarisOperatorMap.get(OperatorType.GOVERNANCE);
        Map<String, String> params = Optional.ofNullable(parameters).orElse(Collections.emptyMap());
        String key = host + ":" + port + "|hash_code:" + params.hashCode();
        return operatorMap.computeIfAbsent(key, s -> new PolarisOperator(OperatorType.GOVERNANCE, host, port, parameters, new BaseBootConfigHandler()));
    }

    public PolarisOperator loadOrStoreForConfig(String host, int port, Map<String, String> parameters) {
        Map<String, PolarisOperator> operatorMap = polarisOperatorMap.get(OperatorType.CONFIG);
        Map<String, String> params = Optional.ofNullable(parameters).orElse(Collections.emptyMap());
        String key = host + ":" + port + "|hash_code:" + params.hashCode();
        return operatorMap.computeIfAbsent(key, s -> new PolarisOperator(OperatorType.CONFIG, host, port, parameters, new BaseBootConfigHandler()));
    }


    public PolarisOperator getGovernancePolarisOperator() {
        Map<String, PolarisOperator> operatorMap = polarisOperatorMap.get(OperatorType.GOVERNANCE);
        if (operatorMap.isEmpty()) {
            return null;
        }
        return operatorMap.values().iterator().next();
    }

}
