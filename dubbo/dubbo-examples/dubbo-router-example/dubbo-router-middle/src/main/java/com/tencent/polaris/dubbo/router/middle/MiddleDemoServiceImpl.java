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

package com.tencent.polaris.dubbo.router.middle;

import com.tencent.polaris.common.utils.ExampleConsts;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.demo.NextService;

public class MiddleDemoServiceImpl implements DemoService {

    private final String version;

    private NextService nextService;

    private final DubboBootstrap bootstrap;

    public MiddleDemoServiceImpl(String version, DubboBootstrap bootstrap) {
        this.version = version;
        this.bootstrap = bootstrap;
    }

    private NextService getNextService() {
        if (null != nextService) {
            return nextService;
        }
        synchronized (this) {
            if (null != nextService) {
                return nextService;
            }
            ReferenceConfig<NextService> reference = new ReferenceConfig<>();
            reference.setRegistry(new RegistryConfig(ExampleConsts.POLARIS_ADDRESS));
            reference.setInterface(NextService.class);
            reference.setVersion(CommonConstants.ANY_VALUE);
            bootstrap.reference(reference).start();
            nextService = ReferenceConfigCache.getCache().get(reference);
            return nextService;
        }
    }

    @Override
    public String sayHello(String name) {
        String responseText = getNextService().sayHello(name);
        return String.format("middle.%s[%s]->%s", name, version, responseText);
    }
}
