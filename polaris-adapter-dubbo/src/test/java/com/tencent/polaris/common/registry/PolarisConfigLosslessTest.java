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
 */

package com.tencent.polaris.common.registry;

import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.utils.Consts;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PolarisConfigLosslessTest {

    @Test
    public void losslessEnabled_defaultFalse_whenKeyAbsent() {
        PolarisConfig cfg = new PolarisConfig(
                PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, new HashMap<>());
        Assert.assertFalse(cfg.isLosslessEnabled());
    }

    @Test
    public void losslessEnabled_parsedFromParameters() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "true");
        PolarisConfig cfg = new PolarisConfig(
                PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, params);
        Assert.assertTrue(cfg.isLosslessEnabled());
    }

    @Test
    public void losslessEnabled_falseLiteral_returnsFalse() {
        Map<String, String> params = new HashMap<>();
        params.put(Consts.KEY_POLARIS_LOSSLESS_ENABLED, "false");
        PolarisConfig cfg = new PolarisConfig(
                PolarisOperators.OperatorType.GOVERNANCE, "127.0.0.1", 8091, params);
        Assert.assertFalse(cfg.isLosslessEnabled());
    }
}
