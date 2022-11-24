/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.polaris.dubbo.circuitbreak.provider;

import com.tencent.polaris.common.utils.ExampleConsts;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.CircuitbreakDemoService;

public class Application {

    public static void main(String[] args) {
        startWithBootstrap();
    }

    private static void startWithBootstrap() {
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(CircuitbreakDemoService.class);
        service.setRef(new DemoServiceImpl());
        String portStr = System.getenv(ExampleConsts.ENV_KEY_PORT);
        int port;
        if (StringUtils.isBlank(portStr)) {
            port = 10070;
        } else {
            port = Integer.parseInt(portStr);
        }
        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setPort(port);
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-circuitbreak-provider-service"))
                .registry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS))
                .service(service)
                .protocol(protocolConfig)
                .start()
                .await();
    }

}
