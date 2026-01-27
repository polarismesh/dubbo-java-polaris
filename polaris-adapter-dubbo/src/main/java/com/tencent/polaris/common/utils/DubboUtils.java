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

package com.tencent.polaris.common.utils;

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.registry.client.InstanceAddressURL;
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

    public static List<DubboServiceInfo> analyzeLocalDubboServiceInfo(ScopeModel model, Invoker<?> invoker,
            Invocation invocation) {
        URL url = invoker.getUrl();
        String registerMode = getRegisterMode(model);

        switch (registerMode) {
            case RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE:
                DubboServiceInfo dubboServiceInfo = new DubboServiceInfo();
                dubboServiceInfo.setService(url.getApplication());
                dubboServiceInfo.setInterfaceName(url.getServiceInterface());
                dubboServiceInfo.setMethodName(invocation.getMethodName());
                dubboServiceInfo.setParametersType(invocation.getParameterTypes());
                return Collections.singletonList(dubboServiceInfo);
            case RegistryConstants.DEFAULT_REGISTER_MODE_ALL:
                DubboServiceInfo instanceInfo = new DubboServiceInfo();
                instanceInfo.setService(url.getApplication());
                instanceInfo.setInterfaceName(url.getServiceInterface());
                instanceInfo.setMethodName(invocation.getMethodName());
                instanceInfo.setParametersType(invocation.getParameterTypes());
                DubboServiceInfo interfaceInfo = new DubboServiceInfo();
                interfaceInfo.setService(url.getServiceInterface());
                interfaceInfo.setMethodName(invocation.getMethodName());

                List<DubboServiceInfo> serviceInfos = new ArrayList<>(2);
                serviceInfos.add(instanceInfo);
                serviceInfos.add(interfaceInfo);
                return serviceInfos;
            case RegistryConstants.DEFAULT_REGISTER_MODE_INTERFACE:
                return Collections.singletonList(DubboServiceInfo.builder()
                        .service(url.getServiceInterface())
                        .methodName(invocation.getMethodName())
                        .parametersType(invocation.getParameterTypes())
                        .build());
            default:
                throw new IllegalStateException("invalid dubbo register mode: " + registerMode);
        }
    }

    public static <T> List<DubboServiceInfo> analyzeRemoteDubboServiceInfo(Invoker<T> invoker, Invocation invocation) {
        List<DubboServiceInfo> serviceInfos = new ArrayList<>(2);

        URL providerUrl = invoker.getUrl();
        String service = providerUrl.getRemoteApplication();
        if (StringUtils.isBlank(service)) {
            service = providerUrl.getHost();
        }
        if (checkIsApplicationMode(invoker)) {
            serviceInfos.add(DubboServiceInfo.builder()
                    .service(service)
                    .interfaceName(providerUrl.getServiceInterface())
                    .methodName(invocation.getMethodName())
                    .parametersType(invocation.getParameterTypes())
                    .build());
        }

        serviceInfos.add(DubboServiceInfo.builder()
                .service(providerUrl.getServiceInterface())
                .methodName(invocation.getMethodName())
                .parametersType(invocation.getParameterTypes())
                .build());
        return serviceInfos;
    }

    static <T> boolean checkIsApplicationMode(Invoker<T> invoker) {
        URL providerUrl = invoker.getUrl();
        if (providerUrl instanceof InstanceAddressURL) {
            return true;
        }
        if (providerUrl instanceof ServiceConfigURL) {
            ServiceConfigURL url = (ServiceConfigURL) providerUrl;
            String registerMode = url.getParameter(RegistryConstants.REGISTER_MODE_KEY,
                    RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE);
            switch (registerMode) {
                case RegistryConstants.DEFAULT_REGISTER_MODE_ALL:
                case RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE:
                    return true;
            }
        }
        return false;
    }

}