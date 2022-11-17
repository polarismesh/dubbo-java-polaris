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

package com.tencent.polaris.dubbo.ratelimit.consumer;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.demo.ExampleConsts;
import org.apache.dubbo.demo.RatelimitDemoService;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

public class Application {

    public static void main(String[] args) {
        ReferenceConfig<RatelimitDemoService> reference = new ReferenceConfig<>();
        reference.setRegistry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS));
        reference.setInterface(RatelimitDemoService.class);
        reference.setVersion(CommonConstants.ANY_VALUE);
        reference.setRetries(0);
        DubboBootstrap bootstrap = DubboBootstrap.getInstance()
                .application(new ApplicationConfig("dubbo-ratelimit-service"))
                .registry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS));
        bootstrap.reference(reference).start();
        RatelimitDemoService service = ReferenceConfigCache.getCache().get(reference);

        System.out.println("start to invoke archer");
        int pass = 0;
        int block = 0;
        for (int i = 0; i < 600; i++) {
            if (sayHello(service, "archer")) {
                if (block > 0) {
                    System.out.printf("archer blocked %d%n", block);
                    block = 0;
                }
                pass++;
            } else {
                if (pass > 0) {
                    System.out.printf("archer passed %d%n", pass);
                    pass = 0;
                }
                block++;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("start to invoke tommy");
        pass = 0;
        block = 0;
        for (int i = 0; i < 600; i++) {
            if (sayHello(service, "tommy")) {
                pass++;
            } else {
                block++;
            }
        }
        System.out.printf("tommy passed %d, blocked %d%n", pass, block);
    }

    private static boolean sayHello(RatelimitDemoService service, String userName) {
        RpcContext.getContext().setAttachment("user", userName);
        try {
            service.sayHello("hello");
        } catch (Exception e) {
            if (e instanceof RpcException) {
                return false;
            }
            throw e;
        }
        return true;
    }
}
