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

package com.tencent.polaris.common.registry;

import com.tencent.polaris.common.utils.Consts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PolarisConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisConfig.class);

    private final String namespace;

    private final String discoverAddress;

    private final String configAddress;

    private final String token;

    private final int ttl;

    public PolarisConfig(String host, int port, Map<String, String> parameters) {
        discoverAddress = String.format("%s:%d", host, port);
        configAddress = String.format("%s:%d", host, Consts.CONFIG_PORT);

        String namespaceStr = parameters.get(Consts.KEY_NAMESPACE);
        if (null == namespaceStr || namespaceStr.isEmpty()) {
            namespaceStr = Consts.DEFAULT_NAMESPACE;
        }
        this.namespace = namespaceStr;
        this.token = parameters.get(Consts.KEY_TOKEN);
        int healthTTL = Consts.DEFAULT_TTL;
        String ttlStr = System.getProperty(Consts.KEY_TTL);
        if (null != ttlStr && !ttlStr.isEmpty()) {
            try {
                healthTTL = Integer.parseInt(ttlStr);
            } catch (Exception e) {
                LOG.info("[Common] fail to convert ttlStr {}", ttlStr, e);
            }
        }
        this.ttl = healthTTL;
        LOG.info("[Common] construct polarisConfig {}", this);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDiscoverAddress() {
        return discoverAddress;
    }

    public String getConfigAddress() {
        return configAddress;
    }

    public String getToken() {
        return token;
    }

    public int getTtl() {
        return ttl;
    }

    @Override
    public String toString() {
        return "PolarisConfig{" +
                "namespace='" + namespace + '\'' +
                ", discoverAddress='" + discoverAddress + '\'' +
                ", configAddress='" + configAddress + '\'' +
                ", token='" + token + '\'' +
                ", ttl=" + ttl +
                '}';
    }
}
