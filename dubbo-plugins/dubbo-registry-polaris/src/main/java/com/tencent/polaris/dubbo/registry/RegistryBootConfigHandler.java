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

package com.tencent.polaris.dubbo.registry;

import com.tencent.polaris.common.config.BootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import java.util.Map;

public class RegistryBootConfigHandler implements BootConfigHandler {

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        if (parameters.containsKey(Consts.KEY_LB_POLICY)) {
            configuration.getGlobal().getServerConnector().setLbPolicy(parameters.get(Consts.KEY_LB_POLICY));
        }
        if (parameters.containsKey(Consts.KEY_SERVER_SWITCH_INTERVAL)) {
            long serverSwitchInterval = Long.parseLong(parameters.get(Consts.KEY_SERVER_SWITCH_INTERVAL));
            configuration.getGlobal().getServerConnector().setServerSwitchInterval(serverSwitchInterval);
        }
    }
}
