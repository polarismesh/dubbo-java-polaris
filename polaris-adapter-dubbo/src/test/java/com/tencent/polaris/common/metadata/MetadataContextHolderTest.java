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

import com.tencent.polaris.metadata.core.MetadataContainer;
import com.tencent.polaris.metadata.core.MetadataStringValue;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;
import com.tencent.polaris.metadata.core.manager.MetadataContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class MetadataContextHolderTest {

    private static String getStringValue(MetadataContainer container, String key) {
        MetadataStringValue metadataValue = container.getMetadataValue(key);
        return metadataValue != null ? metadataValue.getStringValue() : null;
    }

    @After
    public void after() {
        MetadataContextHolder.remove();
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
    public void testSetAndGet() {
        MetadataContext custom = new MetadataContext();
        MetadataContainer container = custom.getMetadataContainer(MetadataType.CUSTOM, false);
        container.putMetadataStringValue("test-key", "test-val", TransitiveType.PASS_THROUGH);

        MetadataContextHolder.set(custom);
        MetadataContext retrieved = MetadataContextHolder.get();

        String value = getStringValue(retrieved.getMetadataContainer(MetadataType.CUSTOM, false), "test-key");
        Assert.assertEquals("test-val", value);
    }

    @Test
    public void testRemoveClearsContext() {
        MetadataContext ctx1 = MetadataContextHolder.get();
        MetadataContainer container = ctx1.getMetadataContainer(MetadataType.CUSTOM, false);
        container.putMetadataStringValue("key", "val", TransitiveType.PASS_THROUGH);

        MetadataContextHolder.remove();

        MetadataContext ctx2 = MetadataContextHolder.get();
        // After remove, a fresh context is created
        Assert.assertNotSame(ctx1, ctx2);
    }
}
