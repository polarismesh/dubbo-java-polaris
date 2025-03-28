/*
 * Tencent is pleased to support the open source community by making dubbo-polaris-java available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.tencent.polaris.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

public class MultiReportUtilTest {

    @Test
    public void testMultiAddress() {
        String testUrl = "polaris://127.0.0.1:8091?namespace=dubbo-multi&multi_address=nacos://127.0.0.1:8848?namespace=xxxxx&username=xxx";
        URL dubboUrl = URL.valueOf(testUrl);

        Optional<URL> ret = MultiReportUtil.generate(dubboUrl);
        Assert.assertTrue(ret.isPresent());
        Assert.assertEquals("nacos://127.0.0.1:8848?namespace=xxxxx&username=xxx", ret.get().toString());
    }

}