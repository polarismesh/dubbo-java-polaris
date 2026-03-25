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

package com.tencent.polaris.dubbo.router;

import static com.tencent.polaris.common.utils.Consts.KEY_NEARBY_MATCH_LEVEL;

import com.tencent.polaris.api.config.consumer.ServiceRouterConfig;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.config.BootConfigHandler;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.factory.config.ConfigurationImpl;
import com.tencent.polaris.plugins.router.nearby.NearbyRouterConfig;
import com.tencent.polaris.specification.api.v1.traffic.manage.RoutingProto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterBootConfigHandler implements BootConfigHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RouterBootConfigHandler.class);

    @Override
    public void handle(PolarisConfig polarisConfig, Map<String, String> parameters, ConfigurationImpl configuration) {
        if (parameters.containsKey(KEY_NEARBY_MATCH_LEVEL)) {
            String matchLevel = parameters.get(KEY_NEARBY_MATCH_LEVEL);
            try {
                RoutingProto.NearbyRoutingConfig.LocationLevel locationLevel =
                        RoutingProto.NearbyRoutingConfig.LocationLevel.valueOf(StringUtils.upperCase(matchLevel));
                NearbyRouterConfig nearbyRouterConfig = configuration.getConsumer().getServiceRouter().getPluginConfig(
                        ServiceRouterConfig.DEFAULT_ROUTER_NEARBY, NearbyRouterConfig.class);
                nearbyRouterConfig.setMatchLevel(locationLevel);
                configuration.getConsumer().getServiceRouter()
                        .setPluginConfig(ServiceRouterConfig.DEFAULT_ROUTER_NEARBY, nearbyRouterConfig);
            } catch (Exception e) {
                LOG.warn("failed to parse nearby match level: {}, use default match level.", matchLevel);
            }
        }
    }
}
