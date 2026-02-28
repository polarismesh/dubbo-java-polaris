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

package com.tencent.polaris.common.utils;

public interface Consts {

    String DEFAULT_NAMESPACE = "default";

    int DEFAULT_TTL = 5;

    String KEY_NAMESPACE = "namespace";

    String KEY_POLARIS_NAMESPACE = "polaris_namespace";

    String KEY_TIMEOUT = "timeout";

    String KEY_POLARIS_TIMEOUT = "polaris_timeout";

    String KEY_PERSIST_ENABLE = "persist_enable";

    String KEY_POLARIS_PERSIST_ENABLE = "polaris_persist_enable";

    String KEY_TOKEN = "token";

    String KEY_POLARIS_TOKEN = "polaris_token";

    String KEY_TTL = "ttl";

    String KEY_POLARIS_TTL = "polaris_ttl";

    String KEY_DETECT_WHEN = "polaris_detect_when";

    String KEY_METRIC_TYPE = "polaris_stat_type";

    String KEY_METRIC_PULL_PORT = "polaris_stat_pull_port";

    String KEY_METRIC_PUSH_ADDR = "polaris_stat_push_addr";

    String KEY_METRIC_PUSH_INTERVAL = "polaris_stat_push_interval";

    String KEY_CONFIG_ENCRYPT_ENABLED = "polaris_config_encrypt_enabled";

    String KEY_PGW_EVENT_ENABLED = "polaris_pgw_event_enabled";

    String KEY_PGW_EVENT_ADDR = "polaris_pgw_event_addr";

    String KEY_OTHER_ADDRESSES = "polaris_other_addresses";

    String KEY_LB_POLICY = "polaris_lb_policy";

    String KEY_SERVER_SWITCH_INTERVAL = "polaris_server_switch_interval";

    String INSTANCE_KEY_HEALTHY = "_internal_healthy";

    String INSTANCE_KEY_ISOLATED = "_internal_isolated";

    String INSTANCE_KEY_CIRCUIT_BREAKER = "_internal_circuit_breaker";

    String INSTANCE_KEY_ID = "_internal_id";

    String INSTANCE_VERSION = "version";

    String INSTANCE_WEIGHT = "weight";

    String DUBBO_PROTOCOL = "dubbo";

    String CONFIG_PORT = "config_port";

    String DISCOVER_PORT = "discover_port";

    String DEFAULT_VERSION = "1.0.0";

    String ADDRESSES_SEPARATOR = "/";
}
