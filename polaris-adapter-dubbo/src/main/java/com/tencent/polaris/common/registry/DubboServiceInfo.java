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

package com.tencent.polaris.common.registry;

import org.apache.dubbo.common.utils.StringUtils;

public class DubboServiceInfo {

    private String service;

    private String interfaceName;

    private String methodName;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getDubboInterface() {
        if (StringUtils.isNotBlank(interfaceName)) {
            return interfaceName;
        }
        return methodName;
    }

    public String getReportMethodName() {
        return this.interfaceName + "#" + this.methodName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String service;
        private String interfaceName;
        private String methodName;

        private Builder() {
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder interfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public DubboServiceInfo build() {
            DubboServiceInfo dubboServiceInfo = new DubboServiceInfo();
            dubboServiceInfo.setService(service);
            dubboServiceInfo.setInterfaceName(interfaceName);
            dubboServiceInfo.setMethodName(methodName);
            return dubboServiceInfo;
        }
    }

    @Override
    public String toString() {
        return "DubboServiceInfo{" +
                "service='" + service + '\'' +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                '}';
    }
}
