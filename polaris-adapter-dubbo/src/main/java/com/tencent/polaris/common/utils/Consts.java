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

    String KEY_DETECT_WHEN = "detect_when";

    String KEY_POLARIS_DETECT_WHEN = "polaris_detect_when";

    String KEY_METRIC_TYPE = "stat_type";

    String KEY_POLARIS_METRIC_TYPE = "polaris_stat_type";

    String KEY_METRIC_PULL_PORT = "stat_pull_port";

    String KEY_POLARIS_METRIC_PULL_PORT = "polaris_stat_pull_port";

    String KEY_METRIC_PUSH_ADDR = "stat_push_addr";

    String KEY_POLARIS_METRIC_PUSH_ADDR = "polaris_stat_push_addr";

    String KEY_METRIC_PUSH_INTERVAL = "stat_push_interval";

    String KEY_POLARIS_METRIC_PUSH_INTERVAL = "polaris_stat_push_interval";

    String KEY_CONFIG_ENCRYPT_ENABLED = "polaris_config_encrypt_enabled";

    String KEY_PGW_EVENT_ENABLED = "polaris_pgw_event_enabled";

    String KEY_PGW_EVENT_ADDR = "polaris_pgw_event_addr";

    String KEY_OTHER_ADDRESSES = "polaris_other_addresses";

    String KEY_LB_POLICY = "polaris_lb_policy";

    String KEY_SERVER_SWITCH_INTERVAL = "polaris_server_switch_interval";

    String KEY_CONFIG_PORT = "config_port";

    String KEY_DISCOVER_PORT = "discover_port";

    String KEY_POLARIS_CONFIG_PORT = "polaris_config_port";

    String KEY_POLARIS_DISCOVER_PORT = "polaris_discover_port";

    String KEY_NEARBY_ENABLED = "polaris_nearby_enabled";

    String KEY_NEARBY_MATCH_LEVEL = "polaris_nearby_match_level";

    String INSTANCE_KEY_HEALTHY = "_internal_healthy";

    String INSTANCE_KEY_ISOLATED = "_internal_isolated";

    String INSTANCE_KEY_CIRCUIT_BREAKER = "_internal_circuit_breaker";

    String INSTANCE_KEY_ID = "_internal_id";

    String INSTANCE_VERSION = "version";

    String INSTANCE_WEIGHT = "weight";

    String DUBBO_PROTOCOL = "dubbo";

    String DEFAULT_VERSION = "1.0.0";

    String ADDRESSES_SEPARATOR = "/";

    String KEY_POLARIS_LOSSLESS_ENABLED = "polaris_lossless_enabled";

    String KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL = "polaris_lossless_delay_register_interval";

    String KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH = "polaris_lossless_health_check_path";

    String KEY_POLARIS_LOSSLESS_ADMIN_PORT = "polaris_lossless_admin_port";

    String KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL = "polaris_lossless_health_check_interval";

    // ==================== Nacos Connector 配置 ====================

    String KEY_POLARIS_NACOS_ENABLED = "polaris_nacos_enabled";

    String KEY_POLARIS_NACOS_SERVER_ADDR = "polaris_nacos_server_addr";

    String KEY_POLARIS_NACOS_REGISTER_ENABLED = "polaris_nacos_register_enabled";

    String KEY_POLARIS_NACOS_DISCOVERY_ENABLED = "polaris_nacos_discovery_enabled";

    String KEY_POLARIS_NACOS_USERNAME = "polaris_nacos_username";

    String KEY_POLARIS_NACOS_PASSWORD = "polaris_nacos_password";

    String KEY_POLARIS_NACOS_NAMESPACE = "polaris_nacos_namespace";

    String KEY_POLARIS_NACOS_GROUP = "polaris_nacos_group";

    String KEY_POLARIS_NACOS_CLUSTER = "polaris_nacos_cluster";

    String KEY_POLARIS_NACOS_EPHEMERAL = "polaris_nacos_ephemeral";

    String KEY_POLARIS_NACOS_WEIGHT = "polaris_nacos_weight";

    String KEY_POLARIS_NACOS_CONTEXT_PATH = "polaris_nacos_context_path";

    String KEY_POLARIS_NACOS_DUBBO_ADAPT = "polaris_nacos_dubbo_adapt";

    String NACOS_CONNECTOR_ID = "nacos";

    String NACOS_CONNECTOR_PROTOCOL = "nacos";

    String NACOS_DEFAULT_NAMESPACE = "public";

    String NACOS_DEFAULT_GROUP = "DEFAULT_GROUP";

    String NACOS_DEFAULT_CLUSTER = "DEFAULT";

    String NACOS_METADATA_KEY_GROUP = "nacos.group";

    String NACOS_METADATA_KEY_CLUSTER = "nacos.cluster";

    String NACOS_METADATA_KEY_EPHEMERAL = "nacos.ephemeral";

    String NACOS_METADATA_KEY_WEIGHT = "nacos.weight";

    String NACOS_METADATA_KEY_USERNAME = "username";

    String NACOS_METADATA_KEY_PASSWORD = "password";

    String NACOS_METADATA_KEY_NAMESPACE = "namespace";

    String NACOS_METADATA_KEY_CONTEXT_PATH = "contextPath";

    String NACOS_METADATA_KEY_DUBBO_ADAPT = "nacos.dubboAdapt";
}
