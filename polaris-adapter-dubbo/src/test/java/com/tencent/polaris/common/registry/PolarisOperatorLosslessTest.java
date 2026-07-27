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
