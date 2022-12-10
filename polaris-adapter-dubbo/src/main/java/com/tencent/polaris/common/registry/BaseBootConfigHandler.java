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

import com.tencent.polaris.factory.config.ConfigurationImpl;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseBootConfigHandler implements BootConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseBootConfigHandler.class);

    @Override
    public void handle(Map<String, String> parameters, ConfigurationImpl configuration) {
        int timeout = 0;
        String timeoutStr = parameters.get(Consts.KEY_TIMEOUT);
        if (null != timeoutStr && timeoutStr.length() > 0) {
            try {
                timeout = Integer.parseInt(timeoutStr);
            } catch (Exception e) {
                LOGGER.info("[Common] fail to convert ttlStr {}", timeoutStr, e);
            }
        }
        if (timeout > 0) {
            configuration.getGlobal().getAPI().setTimeout(timeout);
        }
        Boolean persistEnable = null;
        String persistEnableStr = parameters.get(Consts.KEY_PERSIST_ENABLE);
        if (null != persistEnableStr && persistEnableStr.length() > 0) {
            persistEnable = Boolean.parseBoolean(persistEnableStr);
        }
        if (null != persistEnable) {
            configuration.getConsumer().getLocalCache().setPersistEnable(persistEnable);
        }
    }
}
