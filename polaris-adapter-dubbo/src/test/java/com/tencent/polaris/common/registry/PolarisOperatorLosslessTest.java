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

import com.tencent.polaris.api.core.LosslessAPI;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class PolarisOperatorLosslessTest {

    @Test
    public void getLosslessAPI_returnsNonNull() {
        // 选择 18091 避免与同 JVM 中其它测试可能用到的真实 8091 PolarisOperators 缓存冲突
        PolarisOperator op = PolarisOperators.loadOrStoreForGovernance(
                "127.0.0.1", 18091, new HashMap<>());
        try {
            LosslessAPI api = op.getLosslessAPI();
            Assert.assertNotNull(api);
        } finally {
            op.destroy();
        }
    }
}
