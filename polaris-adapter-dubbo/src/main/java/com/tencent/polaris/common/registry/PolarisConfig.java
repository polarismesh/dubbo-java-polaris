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

package com.tencent.polaris.common.registry;

import com.tencent.polaris.api.config.consumer.LoadBalanceConfig;
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

    private final PolarisOperators.OperatorType operatorType;

    private long serverSwitchInterval = 600000;

    private String lbPolicy = LoadBalanceConfig.LOAD_BALANCE_ROUND_ROBIN;

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
        LOG.info("[Common] construct polarisConfig {}", this);
    }

    private void initAddress(String host, int port, Map<String, String> parameters) {
        int discoverPort = 8091;
        int configPort = 8093;
        switch (operatorType) {
            case CONFIG:
                configPort = port;
                String discoverPortStr = parameters.getOrDefault(Consts.DISCOVER_PORT, discoverPort + "");
                discoverPort = Integer.parseInt(discoverPortStr);
                break;
            case GOVERNANCE:
                discoverPort = port;
                configPort = Integer.parseInt(parameters.getOrDefault(Consts.CONFIG_PORT, configPort + ""));
                if (parameters.containsKey(Consts.KEY_OTHER_ADDRESSES)) {
                    // e.g. dubbo.registry.parameters=[{other_addresses:127.0.0.1:8091,127.0.0.1:8091}]
                    String discoverAddressesStr = parameters.get(Consts.KEY_OTHER_ADDRESSES);
                    Collections.addAll(this.discoverAddresses, discoverAddressesStr.split(Consts.ADDRESSES_SEPARATOR));
                }
                if (parameters.containsKey(Consts.KEY_LB_POLICY)) {
                    this.lbPolicy = parameters.get(Consts.KEY_LB_POLICY);
                }
                if (parameters.containsKey(Consts.KEY_SERVER_SWITCH_INTERVAL)) {
                    this.serverSwitchInterval = Long.parseLong(parameters.get(Consts.KEY_SERVER_SWITCH_INTERVAL));
                }
                break;
            case METADATA_REPORT:
                discoverPort = port;
                configPort = Integer.parseInt(parameters.getOrDefault(Consts.CONFIG_PORT, configPort + ""));
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

    public String getLbPolicy() {
        return lbPolicy;
    }


    public Long getServerSwitchInterval() {
        return serverSwitchInterval;
    }

    @Override
    public String toString() {
        return "PolarisConfig{" +
                "namespace='" + namespace + '\'' +
                ", token='" + token + '\'' +
                ", ttl=" + ttl +
                ", operatorType=" + operatorType +
                ", discoverAddresses=" + discoverAddresses +
                ", configAddresses=" + configAddresses +
                ", serverSwitchInterval=" + serverSwitchInterval +
                ", lbPolicy='" + lbPolicy + '\'' +
                '}';
    }
}
