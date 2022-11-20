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

package com.tencent.polaris.dubbox.ratelimit.consumer;

import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.tencent.polaris.dubbox.ratelimit.example.api.User;
import com.tencent.polaris.dubbox.ratelimit.example.api.UserService;

public class RatelimitAction {

    private UserService userService;

    public UserService getUserService() {
        return userService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void start() {
        System.out.println("start to invoke archer");
        int pass = 0;
        int block = 0;
        for (int i = 0; i < 1000; i++) {
            if (register("jason")) {
                pass++;
            } else {
                block++;
            }
        }
        System.out.printf("register archer passed %d, blocked %d%n", pass, block);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("start to invoke tommy");
        pass = 0;
        block = 0;
        for (int i = 0; i < 1000; i++) {
            if (register("tommy")) {
                pass++;
            } else {
                block++;
            }
        }
        System.out.printf("register tommy passed %d, blocked %d%n", pass, block);
    }

    private boolean register(String name) {
        User user = new User();
        user.setName(name);
        RpcContext.getContext().setAttachment("user", name);
        try {
            userService.registerUser(user);
        } catch (Exception e) {
            if (e instanceof RpcException) {
                return false;
            }
            throw new RuntimeException(e);
        }
        return true;
    }
}
