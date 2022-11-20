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

package com.tencent.polaris.dubbox.router.front;

import com.alibaba.dubbo.rpc.RpcContext;
import com.tencent.polaris.dubbox.router.example.api.FooRequest;
import com.tencent.polaris.dubbox.router.example.api.FooResponse;
import com.tencent.polaris.dubbox.router.example.api.MiddleService;

public class FrontService {

    private MiddleService middleService;

    public void setMiddleService(MiddleService middleService) {
        this.middleService = middleService;
    }

    public void start() {
        for (int i = 0; i < 10; i++) {
            foo("archer");
        }
        for (int i = 0; i < 10; i++) {
            foo("tommy");
        }
    }

    private void foo(String userName) {
        RpcContext.getContext().setAttachment("user", userName);
        FooRequest fooRequest = new FooRequest();
        fooRequest.setUser(userName);
        fooRequest.setPwd("123");
        FooResponse message = middleService.foo(fooRequest);
        System.out.printf("frontService->%s%n", message.getMessage());
    }
}
