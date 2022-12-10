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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PolarisOperators {

    private final Map<String, PolarisOperator> polarisOperatorMap = new ConcurrentHashMap<>();

    private PolarisOperators() {
    }

    public static final PolarisOperators INSTANCE = new PolarisOperators();

    public void addPolarisOperator(PolarisOperator polarisOperator) {
        polarisOperatorMap.put(polarisOperator.getPolarisConfig().getRegistryAddress(), polarisOperator);
    }

    public PolarisOperator getPolarisOperator(String host, int port) {
        String address = String.format("%s:%d", host, port);
        return polarisOperatorMap.get(address);
    }

    public PolarisOperator getFirstPolarisOperator() {
        if (polarisOperatorMap.isEmpty()) {
            return null;
        }
        return polarisOperatorMap.values().iterator().next();
    }

    public void deletePolarisOperator(String host, int port) {
        String address = String.format("%s:%d", host, port);
        polarisOperatorMap.remove(address);
    }

}
