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

package com.tencent.polaris.common.registry.lossless;

import com.tencent.polaris.api.pojo.BaseInstance;
import java.util.Objects;
import org.apache.dubbo.common.URL;

public class DubboBaseInstance implements BaseInstance {

    private final String namespace;

    private final String service;

    private final String host;

    private final int port;

    public DubboBaseInstance(String namespace, URL url) {
        this.namespace = namespace;
        this.service = url.getServiceInterface();
        this.host = url.getHost();
        this.port = url.getPort();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getService() {
        return service;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DubboBaseInstance that = (DubboBaseInstance) o;
        return port == that.port &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(service, that.service) &&
                Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, service, host, port);
    }

    @Override
    public String toString() {
        return "DubboBaseInstance{" +
                "namespace='" + namespace + '\'' +
                ", service='" + service + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
