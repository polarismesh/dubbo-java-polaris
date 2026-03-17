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

package com.tencent.polaris.common.config;

import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolarisConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisConfig.class);

    private final String namespace;

    private final Set<String> discoverAddresses;

    private final Set<String> configAddresses;

    private final String token;

    private final int ttl;

    private final boolean losslessEnabled;

    private final PolarisOperators.OperatorType operatorType;

    private final boolean nearbyEnabled;


    public PolarisConfig(PolarisOperators.OperatorType operatorType, String host, int port,
            Map<String, String> parameters) {
        this.discoverAddresses = new HashSet<>();
        this.configAddresses = new HashSet<>();
        this.operatorType = operatorType;
        initAddress(host, port, parameters);
        String namespaceStr = parameters.get(Consts.KEY_NAMESPACE);
        String polarisNamespaceStr = parameters.get(Consts.KEY_POLARIS_NAMESPACE);
        if (null == namespaceStr || namespaceStr.isEmpty()) {
            namespaceStr = Consts.DEFAULT_NAMESPACE;
        }
        if (null != polarisNamespaceStr && !polarisNamespaceStr.isEmpty()) {
            namespaceStr = polarisNamespaceStr;
        }
        this.namespace = namespaceStr;
        String tokenStr = parameters.get(Consts.KEY_TOKEN);
        String polarisTokenStr = parameters.get(Consts.KEY_POLARIS_TOKEN);
        if (null != polarisTokenStr && !polarisTokenStr.isEmpty()) {
            tokenStr = polarisTokenStr;
        }
        this.token = tokenStr;
        int healthTTL = Consts.DEFAULT_TTL;
        String ttlStr = System.getProperty(Consts.KEY_TTL);
        if (null != ttlStr && !ttlStr.isEmpty()) {
            try {
                healthTTL = Integer.parseInt(ttlStr);
            } catch (Exception e) {
                LOG.info("[Common] fail to convert ttlStr {}", ttlStr, e);
            }
        }
        String polarisTtlStr = System.getProperty(Consts.KEY_POLARIS_TTL);
        if (null != polarisTtlStr && !polarisTtlStr.isEmpty()) {
            try {
                healthTTL = Integer.parseInt(polarisTtlStr);
            } catch (Exception e) {
                LOG.info("[Common] fail to convert ttlStr {}", polarisTtlStr, e);
            }
        }
        this.ttl = healthTTL;
        String losslessEnabledStr = parameters.get(Consts.KEY_POLARIS_LOSSLESS_ENABLED);
        if (null != losslessEnabledStr && !losslessEnabledStr.isEmpty()) {
            this.losslessEnabled = Boolean.parseBoolean(losslessEnabledStr);
        } else {
            this.losslessEnabled = true;
        }
        if (parameters.containsKey(Consts.KEY_NEARBY_ENABLED)) {
            this.nearbyEnabled = Boolean.parseBoolean(parameters.get(Consts.KEY_NEARBY_ENABLED));
        } else {
            this.nearbyEnabled = false;
        }

        LOG.info("[Common] construct polarisConfig {}", this);
    }

    private void initAddress(String host, int port, Map<String, String> parameters) {
        int discoverPort = 8091;
        int configPort = 8093;
        switch (operatorType) {
            case CONFIG:
                configPort = port;
                // if config center setting has parameter "discover_port" or "polaris_discover_port" (high priority)
                String discoverPortStr = parameters.getOrDefault(Consts.KEY_DISCOVER_PORT, discoverPort + "");
                discoverPortStr = parameters.getOrDefault(Consts.KEY_POLARIS_DISCOVER_PORT, discoverPortStr);
                try {
                    discoverPort = Integer.parseInt(discoverPortStr);
                } catch (NumberFormatException e) {
                    LOG.info("[Common] fail to convert discoverPortStr {}, use default port {}", discoverPortStr,
                            discoverPort);
                }
                break;
            case GOVERNANCE:
                discoverPort = port;
                // if config center url has parameter "config_port" or "polaris_config_port" (high priority)
                String configPortStr = parameters.getOrDefault(Consts.KEY_CONFIG_PORT, configPort + "");
                configPortStr = parameters.getOrDefault(Consts.KEY_POLARIS_CONFIG_PORT, configPortStr);
                try {
                    configPort = Integer.parseInt(configPortStr);
                } catch (NumberFormatException e) {
                    LOG.info("[Common] fail to convert configPortStr {}, use default port {}", configPortStr,
                            configPort);
                }
                if (parameters.containsKey(Consts.KEY_OTHER_ADDRESSES)) {
                    // e.g. dubbo.registry.parameters=[{other_addresses:127.0.0.1:8091,127.0.0.1:8091}]
                    String discoverAddressesStr = parameters.get(Consts.KEY_OTHER_ADDRESSES);
                    Collections.addAll(this.discoverAddresses, discoverAddressesStr.split(Consts.ADDRESSES_SEPARATOR));
                }
                break;
            case METADATA_REPORT:
                discoverPort = port;
                configPort = Integer.parseInt(parameters.getOrDefault(Consts.KEY_CONFIG_PORT, configPort + ""));
                break;
        }
        String discoverAddress = String.format("%s:%d", host, discoverPort);
        discoverAddresses.add(discoverAddress);
        String configAddress = String.format("%s:%d", host, configPort);
        configAddresses.add(configAddress);
    }

    public String getNamespace() {
        return namespace;
    }

    public List<String> getDiscoverAddresses() {
        return new ArrayList<>(discoverAddresses);
    }

    public List<String> getConfigAddresses() {
        return new ArrayList<>(configAddresses);
    }

    public String getToken() {
        return token;
    }

    public int getTtl() {
        return ttl;
    }

    public boolean isLosslessEnabled() {
        return losslessEnabled;
    }

    public boolean isNearbyEnabled() {
        return nearbyEnabled;
    }

    @Override
    public String toString() {
        return "PolarisConfig{" +
                "namespace='" + namespace + '\'' +
                ", token='" + token + '\'' +
                ", ttl=" + ttl +
                ", losslessEnabled=" + losslessEnabled +
                ", operatorType=" + operatorType +
                ", discoverAddresses=" + discoverAddresses +
                ", configAddresses=" + configAddresses +
                ", nearbyEnabled=" + nearbyEnabled +
                '}';
    }
}
