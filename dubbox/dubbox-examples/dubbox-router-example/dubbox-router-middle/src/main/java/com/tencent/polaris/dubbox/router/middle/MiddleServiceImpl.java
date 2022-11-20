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

package com.tencent.polaris.dubbox.router.middle;

import com.tencent.polaris.common.utils.ExampleConsts;
import com.tencent.polaris.dubbox.router.example.api.BackService;
import com.tencent.polaris.dubbox.router.example.api.FooRequest;
import com.tencent.polaris.dubbox.router.example.api.FooResponse;
import com.tencent.polaris.dubbox.router.example.api.MiddleService;

public class MiddleServiceImpl implements MiddleService {

    private BackService backService;

    private final String version;

    public MiddleServiceImpl() {
        version = System.getenv(ExampleConsts.ENV_KEY_VERSION);
    }

    public void setBackService(BackService backService) {
        this.backService = backService;
    }

    @Override
    public FooResponse foo(FooRequest request) {
        FooResponse response = backService.foo(request);
        FooResponse finalResponse = new FooResponse();
        finalResponse.setMessage(String.format("middle.%s->%s", version, response.getMessage()));
        return finalResponse;
    }
}
