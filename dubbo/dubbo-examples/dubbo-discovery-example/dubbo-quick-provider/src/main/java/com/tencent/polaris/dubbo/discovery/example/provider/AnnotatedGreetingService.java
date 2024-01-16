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

<<<<<<<< HEAD:dubbo/dubbo-examples/dubbo-governance-example/dubbo-governance-provider/src/main/java/com/tencent/polaris/dubbo/governance/example/provider/AnnotatePrintService.java
package com.tencent.polaris.dubbo.governance.example.provider;

import com.tencent.polaris.dubbo.example.api.PrintService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0")
public class AnnotatePrintService implements PrintService {

    @Override
    public String echo(String name) {
        return "echo, " + name + ", source from " + System.getenv("POD_IP");    }
}
========
package com.tencent.polaris.dubbo.discovery.example.provider;

import com.tencent.polaris.dubbo.example.api.GreetingService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0")
public class AnnotatedGreetingService implements GreetingService {

    public String sayHello(String name) {
        return "hello, " + name;
    }

    @Override
    public String sayHi(String name) {
        return "[provider by polaris] hi, " + name;
    }

}
>>>>>>>> 84880ef (feat:support dubbo3.2.x (#37)):dubbo/dubbo-examples/dubbo-discovery-example/dubbo-quick-provider/src/main/java/com/tencent/polaris/dubbo/discovery/example/provider/AnnotatedGreetingService.java
