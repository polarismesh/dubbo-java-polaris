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

package com.tencent.polaris.dubbo.router.back;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.ExampleConsts;
import org.apache.dubbo.demo.NextService;

public class Application {

    public static void main(String[] args) {
        startWithExport();
    }

    private static void startWithExport() {
        String version = System.getenv(ExampleConsts.ENV_KEY_VERSION);
        ServiceConfig<BackDemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(NextService.class);
        service.setRef(new BackDemoServiceImpl(version));
        service.setParameters(CollectionUtils.toStringMap(CommonConstants.VERSION_KEY, version));

        System.out.println("dubbo back service started");
        RegistryConfig registryConfig = new RegistryConfig(ExampleConsts.POLARIS_ADDRESS);
        ProtocolConfig protocolConfig = new ProtocolConfig();
        String portStr = System.getenv(ExampleConsts.ENV_KEY_PORT);
        int port;
        if (StringUtils.isBlank(portStr)) {
            port = 10060;
        } else {
            port = Integer.parseInt(portStr);
        }
        protocolConfig.setPort(port);
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-router-back-service"))
                .registry(registryConfig)
                .service(service)
                .protocol(protocolConfig)
                .start()
                .await();

    }
}
