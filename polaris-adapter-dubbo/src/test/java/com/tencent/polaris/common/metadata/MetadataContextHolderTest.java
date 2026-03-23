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

package com.tencent.polaris.common.metadata;

import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.manager.MetadataContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class MetadataContextHolderTest {

    @After
    public void after() {
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.remove();
    }

    @Test
    public void testGetReturnsNonNull() {
        Assert.assertNotNull(MetadataContextHolder.get());
    }

    @Test
    public void testGetReturnsSameInstanceInSameThread() {
        MetadataContext ctx1 = MetadataContextHolder.get();
        MetadataContext ctx2 = MetadataContextHolder.get();
        Assert.assertSame(ctx1, ctx2);
    }

    @Test
    public void testGetEnrichesWithStaticMetadata() {
        MetadataContext ctx = MetadataContextHolder.get();
        // get() should return a valid enriched context
        Assert.assertNotNull(ctx);
        Assert.assertNotNull(ctx.getMetadataContainer(MetadataType.CUSTOM, false));
    }
}
