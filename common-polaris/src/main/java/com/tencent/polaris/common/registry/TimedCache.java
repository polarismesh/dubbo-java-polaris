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

public class TimedCache<T> {

    private static final long EXPIRE_MILLI = 5000;

    private final long createTimeMilli;

    private final T value;

    public TimedCache(T value) {
        this.createTimeMilli = System.currentTimeMillis();
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public boolean isExpired() {
        long curTimeMilli = System.currentTimeMillis();
        return curTimeMilli - createTimeMilli >= EXPIRE_MILLI;
    }
}
