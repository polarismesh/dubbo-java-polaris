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

package com.tencent.polaris.dubbo.quickstart.example.consumer;

import com.tencent.polaris.dubbo.example.api.EchoService;
import com.tencent.polaris.dubbo.example.api.GreetingService;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.stereotype.Component;

@Component("annotatedConsumer")
public class GreetingServiceConsumer {

    @DubboReference
    private GreetingService greetingService;

    @DubboReference(version = "1.0.0", providedBy = "dubbo-quickstart-provider")
    private EchoService echoService;

    public String doSayHello(String name) {
        String tagValue = ConfigurationUtils.getProperty(CommonConstants.TAG_KEY);
        if (tagValue != null) {
            RpcContext.getContext().setAttachment(CommonConstants.TAG_KEY, tagValue);
        }
        return greetingService.sayHello(name);
    }

    public String doSayHi(String name) {
        String tagValue = ConfigurationUtils.getProperty(CommonConstants.TAG_KEY);
        if (tagValue != null) {
            RpcContext.getContext().setAttachment(CommonConstants.TAG_KEY, tagValue);
        }
        return greetingService.sayHi(name);
    }

    public String doEcho(String value) {
        String tagValue = ConfigurationUtils.getProperty(CommonConstants.TAG_KEY);
        if (tagValue != null) {
            RpcContext.getContext().setAttachment(CommonConstants.TAG_KEY, tagValue);
        }
        return echoService.echo(value);
    }
}