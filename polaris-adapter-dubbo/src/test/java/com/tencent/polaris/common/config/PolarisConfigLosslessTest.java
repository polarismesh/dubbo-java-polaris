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

package com.tencent.polaris.common.config;

import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class PolarisConfigLosslessTest {

    @Test
    public void testLosslessEnabled_defaultFalse() {
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        Assert.assertFalse("lossless 应默认禁用", config.isLosslessEnabled());
    }

    @Test
    public void testLosslessEnabled_explicitTrue() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "true");

        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        Assert.assertTrue("lossless 应启用", config.isLosslessEnabled());
    }

    @Test
    public void testLosslessEnabled_explicitFalse() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "false");

        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        Assert.assertFalse("lossless 应禁用", config.isLosslessEnabled());
    }

    @Test
    public void testLosslessEnabled_invalidValue_defaultFalse() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "invalid");

        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Boolean.parseBoolean("invalid") returns false
        Assert.assertFalse("无效值 Boolean.parseBoolean 返回 false", config.isLosslessEnabled());
    }

    @Test
    public void testToString_containsLosslessEnabled() {
        Map<String, String> parameters = new HashMap<>();
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        String str = config.toString();
        Assert.assertTrue("toString 应包含 losslessEnabled", str.contains("losslessEnabled"));
    }
}
