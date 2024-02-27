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

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.registry.client.InstanceAddressURL;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.protocol.ReferenceCountInvokerWrapper;
import org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker;

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

    public static List<DubboServiceInfo> analyzeLocalDubboServiceInfo(ScopeModel model, Invoker<?> invoker, Invocation invocation) {
        URL url = invoker.getUrl();
        String registerMode = getRegisterMode(model);

        switch (registerMode) {
            case RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE:
                return Collections.singletonList(DubboServiceInfo.builder()
                        .service(url.getApplication())
                        .interfaceName(invocation.getAttachment(Constants.INTERFACE))
                        .methodName(invocation.getMethodName())
                        .build());
            case RegistryConstants.DEFAULT_REGISTER_MODE_ALL:
                DubboServiceInfo instanceInfo = new DubboServiceInfo();
                instanceInfo.setService(url.getApplication());
                instanceInfo.setInterfaceName(invocation.getAttachment(Constants.INTERFACE));
                instanceInfo.setMethodName(invocation.getMethodName());

                DubboServiceInfo interfaceInfo = new DubboServiceInfo();
                interfaceInfo.setService(invocation.getServiceName());
                interfaceInfo.setMethodName(invocation.getMethodName());

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

    public static <T> List<DubboServiceInfo> analyzeRemoteDubboServiceInfo(Invoker<T> invoker, Invocation invocation) {
        List<DubboServiceInfo> serviceInfos = new ArrayList<>(2);

        URL providerUrl = invoker.getUrl();
        if (providerUrl instanceof InstanceAddressURL) {
            serviceInfos.add(DubboServiceInfo.builder()
                    .service(providerUrl.getRemoteApplication())
                    .interfaceName(providerUrl.getServiceInterface())
                    .methodName(invocation.getMethodName())
                    .build());
        }

        URL url = invoker.getUrl();
        serviceInfos.add(DubboServiceInfo.builder()
                .service(url.getServiceInterface())
                .methodName(invocation.getMethodName())
                .build());
        return serviceInfos;
    }

}
