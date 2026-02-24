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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.AbstractDynamicConfigurationFactory;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.constants.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 北极星动态配置工厂类
 * <p>
 * 实现 Dubbo 的 DynamicConfigurationFactory SPI 扩展点，
 * 用于创建 PolarisDynamicConfiguration 实例。
 * </p>
 *
 * @author dubbo-polaris
 */
public class PolarisDynamicConfigurationFactory extends AbstractDynamicConfigurationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisDynamicConfigurationFactory.class);

    @Override
    protected DynamicConfiguration createDynamicConfiguration(URL url) {
        URL polarisUrl = url;
        if (CommonConstants.DUBBO.equals(url.getParameter("namespace"))) {
            polarisUrl = url.removeParameter("namespace");
        }
        return new PolarisDynamicConfiguration(polarisUrl);
    }
}
