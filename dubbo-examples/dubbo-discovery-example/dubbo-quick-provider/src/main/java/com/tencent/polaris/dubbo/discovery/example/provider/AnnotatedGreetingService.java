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

import com.tencent.polaris.dubbo.example.api.GreetingService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class AnnotatedGreetingService implements GreetingService {

    public String sayHello(String name) {
        String port = System.getProperty("dubbo.protocol.port");
        return "hello, " + name + ", port: " + port;
    }

    @Override
    public String sayHi(String name) {
        String port = System.getProperty("dubbo.protocol.port");
        return "[provider by polaris] hi, " + name + ", port: " + port;
    }

}
