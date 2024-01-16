/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.polaris.common.utils;

import com.tencent.polaris.common.registry.DubboServiceInfo;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DubboUtils {

    // 这里需要获取注册粒度

    public static String getRegisterMode(ScopeModel model) {
        return ConfigurationUtils.getCachedDynamicProperty(model,
                RegistryConstants.DUBBO_REGISTER_MODE_DEFAULT_KEY,
                RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE);
    }

    public static List<DubboServiceInfo> analyzeDubboServiceInfo(ScopeModel model, URL url, Invocation invocation) {
        String registerMode = getRegisterMode(model);

        switch (registerMode) {
            case RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE:
                return Collections.singletonList(DubboServiceInfo.builder()
                        .service(url.getApplication())
                        .interfaceName(invocation.getServiceName())
                        .methodName(invocation.getMethodName())
                        .build());
            case RegistryConstants.DEFAULT_REGISTER_MODE_ALL:
                DubboServiceInfo instanceInfo = new DubboServiceInfo();
                instanceInfo.setService(url.getApplication());
                instanceInfo.setInterfaceName(invocation.getServiceName());
                instanceInfo.setMethodName(invocation.getMethodName());

                DubboServiceInfo interfaceInfo = new DubboServiceInfo();
                instanceInfo.setService(invocation.getServiceName());
                instanceInfo.setMethodName(invocation.getMethodName());

                List<DubboServiceInfo> serviceInfos = new ArrayList<>(2);
                serviceInfos.add(instanceInfo);
                serviceInfos.add(interfaceInfo);
                return serviceInfos;
            case RegistryConstants.DEFAULT_REGISTER_MODE_INTERFACE:
                return Collections.singletonList(DubboServiceInfo.builder()
                        .service(invocation.getServiceName())
                        .methodName(invocation.getMethodName())
                        .build());
            default:
                throw new IllegalStateException("invalid dubbo register mode: " + registerMode);
        }
    }

    public static List<DubboServiceInfo> analyzeDubboServiceInfo(ScopeModel model, Invoker invoker, Invocation invocation) {
        return analyzeDubboServiceInfo(model, invoker.getUrl(), invocation);
    }

}