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

package com.tencent.polaris.common.registry.lossless;

import com.tencent.polaris.api.plugin.compose.Extensions;
import com.tencent.polaris.api.plugin.lossless.InstanceProperties;
import com.tencent.polaris.api.plugin.lossless.LosslessActionProvider;
import com.tencent.polaris.api.pojo.BaseInstance;
import com.tencent.polaris.client.util.OkHttpUtil;
import com.tencent.polaris.plugin.lossless.common.LosslessUtils;
import com.tencent.polaris.specification.api.v1.traffic.manage.LosslessProto;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DubboLosslessActionProvider implements LosslessActionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DubboLosslessActionProvider.class);

    private final Runnable originalRegisterAction;

    private final Runnable originalDeregisterAction;

    private final BaseInstance instance;

    private final int port;

    private final Extensions extensions;

    public DubboLosslessActionProvider(Runnable originalRegisterAction, Runnable originalDeregisterAction,
            int port, BaseInstance instance, Extensions extensions) {
        this.originalRegisterAction = originalRegisterAction;
        this.originalDeregisterAction = originalDeregisterAction;
        this.port = port;
        this.instance = instance;
        this.extensions = extensions;
    }

    @Override
    public String getName() {
        return "dubbo";
    }

    @Override
    public void doRegister(InstanceProperties instanceProperties) {
        originalRegisterAction.run();
    }

    @Override
    public void doDeregister() {
        originalDeregisterAction.run();
    }

    @Override
    public boolean isEnableHealthCheck() {
        return LosslessProto.DelayRegister.DelayStrategy.DELAY_BY_HEALTH_CHECK.equals(resolveStrategy());
    }

    @Override
    public boolean doHealthCheck() {
        Map<String, String> headers = new HashMap<>(1);
        headers.put("User-Agent", "polaris");
        return OkHttpUtil.checkUrl("localhost", port, resolveHealthCheckPath(), headers);
    }

    private LosslessProto.DelayRegister.DelayStrategy resolveStrategy() {
        try {
            LosslessProto.LosslessRule losslessRule = LosslessUtils.getMatchLosslessRule(extensions, instance);
            return Optional.ofNullable(losslessRule)
                    .map(LosslessProto.LosslessRule::getLosslessOnline)
                    .map(LosslessProto.LosslessOnline::getDelayRegister)
                    .map(LosslessProto.DelayRegister::getStrategy)
                    .orElse(extensions.getConfiguration().getProvider().getLossless().getStrategy());
        } catch (Exception e) {
            LOG.debug("failed to get lossless rule from server, fallback to local config", e);
            return extensions.getConfiguration().getProvider().getLossless().getStrategy();
        }
    }

    private String resolveHealthCheckPath() {
        try {
            LosslessProto.LosslessRule losslessRule = LosslessUtils.getMatchLosslessRule(extensions, instance);
            return Optional.ofNullable(losslessRule)
                    .map(LosslessProto.LosslessRule::getLosslessOnline)
                    .map(LosslessProto.LosslessOnline::getDelayRegister)
                    .map(LosslessProto.DelayRegister::getHealthCheckPath)
                    .orElse(extensions.getConfiguration().getProvider().getLossless().getHealthCheckPath());
        } catch (Exception e) {
            LOG.debug("failed to get lossless rule from server, fallback to local config", e);
            return extensions.getConfiguration().getProvider().getLossless().getHealthCheckPath();
        }
    }
}
