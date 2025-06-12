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

package com.tencent.polaris.common.utils;

public interface Consts {

    String DEFAULT_NAMESPACE = "default";

    int DEFAULT_TTL = 5;

    int CONFIG_PORT = 8093;

    String KEY_NAMESPACE = "namespace";

    String KEY_TIMEOUT = "timeout";

    String KEY_PERSIST_ENABLE = "persist_enable";

    String KEY_TOKEN = "token";

    String KEY_TTL = "ttl";

    String KEY_DETECT_WHEN = "detect_when";

    String INSTANCE_KEY_HEALTHY = "_internal_healthy";

    String INSTANCE_KEY_ISOLATED = "_internal_isolated";

    String INSTANCE_KEY_CIRCUIT_BREAKER = "_internal_circuit_breaker";

    String INSTANCE_KEY_ID = "_internal_id";
}
