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

import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;

public class PolarisOperatorDelegate implements ScopeModelAware {

    private PolarisOperator polarisOperator;

    private final Object lock = new Object();

    protected ApplicationModel applicationModel;

    @Override
    public void setApplicationModel(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
    }

    public PolarisOperator getGovernancePolarisOperator() {
        if (null != polarisOperator) {
            return polarisOperator;
        }
        synchronized (lock) {
            if (null != polarisOperator) {
                return polarisOperator;
            }
            polarisOperator = PolarisOperators.INSTANCE.getGovernancePolarisOperator();
            return polarisOperator;
        }
    }

    protected String formatCode(Object val) {
        return "POLARIS:" + val;
    }
}
