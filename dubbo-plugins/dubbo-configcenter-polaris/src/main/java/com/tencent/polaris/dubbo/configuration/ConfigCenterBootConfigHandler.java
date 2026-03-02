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

package com.tencent.polaris.dubbo.configuration;

import com.tencent.polaris.common.config.BootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import java.util.Collections;
import java.util.Map;

public class ConfigCenterBootConfigHandler implements BootConfigHandler {

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        // 设置配置中心连接地址
        configuration.getConfigFile().getServerConnector()
                .setAddresses(polarisConfig.getConfigAddresses());
        // 禁用配置推空保护
        configuration.getConfigFile().getServerConnector().setEmptyProtectionEnable(false);
        configuration.getConfigFile().getConfigFilterConfig()
                .setEnable(Boolean.parseBoolean(parameters.getOrDefault(Consts.KEY_CONFIG_ENCRYPT_ENABLED, "true")));
        configuration.getConfigFile().getConfigFilterConfig().getChain().add("crypto");
        configuration.getConfigFile().getConfigFilterConfig().getPlugin()
                .put("crypto", Collections.singletonMap("type", "AES"));
    }
}
