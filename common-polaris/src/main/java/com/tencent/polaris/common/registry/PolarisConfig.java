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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolarisConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisConfig.class);

    private final String namespace;

    private final String registryAddress;

    private final String configAddress;

    private final String token;

    private final int ttl;

    public PolarisConfig(String host, int port, Map<String, String> parameters) {
        registryAddress = String.format("%s:%d", host, port);
        configAddress = String.format("%s:%d", host, Consts.CONFIG_PORT);

        String namespaceStr = parameters.get(Consts.KEY_NAMESPACE);
        if (null == namespaceStr || namespaceStr.length() == 0) {
            namespaceStr = Consts.DEFAULT_NAMESPACE;
        }
        this.namespace = namespaceStr;
        this.token = parameters.get(Consts.KEY_TOKEN);
        int healthTTL = Consts.DEFAULT_TTL;
        String ttlStr = System.getProperty(Consts.KEY_TTL);
        if (null != ttlStr && ttlStr.length() > 0) {
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

    public String getRegistryAddress() {
        return registryAddress;
    }

    public String getToken() {
        return token;
    }

    public String getConfigAddress() {
        return configAddress;
    }

    public int getTtl() {
        return ttl;
    }

    @Override
    public String toString() {
        return "PolarisConfig{" +
                "namespace='" + namespace + '\'' +
                ", registryAddress='" + registryAddress + '\'' +
                ", configAddress='" + configAddress + '\'' +
                ", token='" + token + '\'' +
                ", ttl=" + ttl +
                '}';
    }
}
