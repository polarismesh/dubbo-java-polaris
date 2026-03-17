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

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.config.BootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.factory.config.provider.LosslessConfigImpl;
import com.tencent.polaris.specification.api.v1.traffic.manage.LosslessProto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LosslessBootConfigHandler implements BootConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LosslessBootConfigHandler.class);

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        LosslessConfigImpl losslessConfig = (LosslessConfigImpl) configuration.getProvider().getLossless();

        // polaris_lossless_enabled
        String enabledStr = parameters.get(Consts.KEY_POLARIS_LOSSLESS_ENABLED);
        if (StringUtils.isNotBlank(enabledStr)) {
            losslessConfig.setEnable(Boolean.parseBoolean(enabledStr));
        }

        // polaris_lossless_delay_register_interval (ms)
        String delayTimeStr = parameters.get(Consts.KEY_POLARIS_LOSSLESS_DELAY_REGISTER_INTERVAL);
        if (StringUtils.isNotBlank(delayTimeStr)) {
            try {
                long delaySeconds = Long.parseLong(delayTimeStr);
                losslessConfig.setDelayRegisterInterval(delaySeconds);
                losslessConfig.setStrategy(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_TIME);
            } catch (NumberFormatException e) {
                LOGGER.warn("[Lossless] fail to parse polaris_lossless_delay_time: {}", delayTimeStr);
            }
        }

        // polaris_lossless_health_check_path
        String healthCheckPath = parameters.get(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_PATH);
        if (StringUtils.isNotBlank(healthCheckPath)) {
            losslessConfig.setHealthCheckPath(healthCheckPath);
            losslessConfig.setStrategy(LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK);
            String healthCheckIntervalStr = parameters.get(Consts.KEY_POLARIS_LOSSLESS_HEALTH_CHECK_INTERVAL);
            if (StringUtils.isNotBlank(healthCheckIntervalStr)) {
                try {
                    long healthCheckInterval = Long.parseLong(healthCheckIntervalStr);
                    losslessConfig.setHealthCheckInterval(healthCheckInterval);
                } catch (NumberFormatException e) {
                    LOGGER.warn("[Lossless] fail to parse polaris_lossless_health_check_interval: {}", healthCheckIntervalStr);
                }
            }
        }

        // polaris_lossless_admin_port
        String adminPortStr = parameters.get(Consts.KEY_POLARIS_LOSSLESS_ADMIN_PORT);
        if (StringUtils.isNotBlank(adminPortStr)) {
            try {
                int adminPort = Integer.parseInt(adminPortStr);
                configuration.getGlobal().getAdmin().setPort(adminPort);
            } catch (NumberFormatException e) {
                LOGGER.warn("[Lossless] fail to parse polaris_lossless_admin_port: {}", adminPortStr);
            }
        }
    }
}
