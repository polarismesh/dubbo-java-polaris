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

package com.tencent.polaris.dubbo.discovery.example.provider;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_LABELS;

import com.tencent.polaris.dubbo.example.api.GreetingService;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;

@DubboService
public class AnnotatedGreetingService implements GreetingService {

    public String sayHello(String name) {
        return "hello, " + name;
    }

    @Override
    public String sayHi(String name) {
        String dubboLabel = ConfigurationUtils.getProperty(DUBBO_LABELS);
        String host = RpcContext.getContext().getLocalHost();
        String port = String.valueOf(RpcContext.getContext().getLocalPort());
        return "[provider by polaris] hi, " + name + " (labels: " + dubboLabel + ", provider host: " + host + ", port: " + port + ")";
    }

}
