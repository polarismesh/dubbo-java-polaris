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

package com.tencent.polaris.common.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Context {

    private static final Map<String, Object> GLOBAL_CONTEXT = new ConcurrentHashMap<>();

    public static <T> void saveToGlobal(String key, T val) {
        GLOBAL_CONTEXT.put(key, val);
    }

    public static <T> T getFromGlobal(String key) {
        return (T) GLOBAL_CONTEXT.get(key);
    }

    public static <T> T getFromGlobal(String key, T def) {
        if (!GLOBAL_CONTEXT.containsKey(key)) {
            return def;
        }
        return (T) GLOBAL_CONTEXT.get(key);
    }

}
