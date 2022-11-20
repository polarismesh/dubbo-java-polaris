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

package com.tencent.polaris.dubbo.router.front;

import com.tencent.polaris.common.utils.ExampleConsts;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.rpc.RpcContext;

public class Application {

    public static void main(String[] args) {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setRegistry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS));
        reference.setInterface(DemoService.class);
        reference.setVersion(CommonConstants.ANY_VALUE);
        DubboBootstrap bootstrap = DubboBootstrap.getInstance()
                .application(new ApplicationConfig("dubbo-router-front-service"))
                .registry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS));
        bootstrap.reference(reference).start();
        DemoService service = ReferenceConfigCache.getCache().get(reference);

        for (int i = 0; i < 10; i++) {
            sayHello(service, "archer");
        }
        for (int i = 0; i < 10; i++) {
            sayHello(service, "tommy");
        }
    }

    private static void sayHello(DemoService service, String userName) {
        RpcContext.getContext().setAttachment("user", userName);
        String message = service.sayHello("hello");
        System.out.printf("message from %s is %s%n", userName, message);
    }
}
