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

package com.tencent.polaris.dubbo.router.front.example;

import com.tencent.polaris.dubbo.example.api.MiddleService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.stereotype.Component;

@Component
public class MiddleConsumer {

    @DubboReference(version = "1.0.0")
    private MiddleService middleService;

    public String sayHello(String name) {
        RpcContext.getClientAttachment().setAttachment("name", name);
        String ret = middleService.sayHello(name);
        return "[FrontService] sayHello, " + name + " -> " + ret;
    }

    public String sayHi(String name) {
        RpcContext.getClientAttachment().setAttachment("name", name);
        String ret = middleService.sayHello(name);
        return "[FrontService] sayHi, " + name + " -> " + ret;
    }

}
