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

package com.tencent.polaris.dubbo.circuitbreak.provider;

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.utils.ExampleConsts;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.dubbo.demo.CircuitbreakDemoService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoServiceImpl implements CircuitbreakDemoService {

    private static final Logger logger = LoggerFactory.getLogger(DemoServiceImpl.class);

    private final boolean hasException;

    private final AtomicInteger count = new AtomicInteger(0);

    public DemoServiceImpl() {
        String exception = System.getenv(ExampleConsts.ENV_KEY_EXCEPTION);
        hasException = StringUtils.isNotBlank(exception);
    }

    @Override
    public String sayHello(String name) {
        System.out.printf("invoke count %d, hasException %s%n", count.incrementAndGet(), hasException);
        if (hasException) {
            throw new RuntimeException("exception caught by testing");
        }
        logger.info("[CB] Hello " + name + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());
        return "[CB] Hello " + name + ", response from provider: " + RpcContext.getContext().getLocalAddress();
    }
}
