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

package com.tencent.polaris.common.registry;

public interface Consts {

    String DEFAULT_NAMESPACE = "default";

    int DEFAULT_TTL = 5;

    int CONFIG_PORT = 8093;

    String KEY_NAMESPACE = "namespace";

    String KEY_TIMEOUT = "timeout";

    String KEY_TOKEN = "token";

    String KEY_TTL = "ttl";

    String INSTANCE_KEY_HEALTHY = "_internal_healthy";

    String INSTANCE_KEY_ISOLATED = "_internal_isolated";

    String INSTANCE_KEY_CIRCUIT_BREAKER = "_internal_circuit_breaker";

    String INSTANCE_KEY_ID = "_internal_id";
}
