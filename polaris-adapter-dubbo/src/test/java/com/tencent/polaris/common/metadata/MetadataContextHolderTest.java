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
    public void testGetEnrichesWithStaticMetadata() {
        MetadataContext ctx = MetadataContextHolder.get();
        Assert.assertNotNull(ctx);
        Assert.assertNotNull(ctx.getMetadataContainer(MetadataType.CUSTOM, false));
    }

    @Test
    public void testRemoveClearsContext() {
        MetadataContext ctx1 = MetadataContextHolder.get();
        MetadataContainer container = ctx1.getMetadataContainer(MetadataType.CUSTOM, false);
        container.putMetadataStringValue("key", "val", TransitiveType.PASS_THROUGH);

        MetadataContextHolder.remove();

        MetadataContext ctx2 = MetadataContextHolder.get();
        // After remove, a fresh context is created — not the same instance
        Assert.assertNotSame(ctx1, ctx2);
    }

    /**
     * Verifies that without calling remove(), metadata written during "request A"
     * leaks into "request B" on the same thread (demonstrating the pollution problem).
     */
    @Test
    public void testWithoutRemoveMetadataLeaks() {
        // Simulate request A: write request-specific metadata
        MetadataContext ctxA = MetadataContextHolder.get();
        MetadataContainer containerA = ctxA.getMetadataContainer(MetadataType.CUSTOM, false);
        containerA.putMetadataStringValue("req-trace-id", "trace-A-123", TransitiveType.PASS_THROUGH);

        // NO remove() called — simulating missing cleanup

        // Simulate request B on the same thread: get context
        MetadataContext ctxB = MetadataContextHolder.get();
        MetadataContainer containerB = ctxB.getMetadataContainer(MetadataType.CUSTOM, false);

        // Without cleanup, request A's data leaks into request B
        Assert.assertSame("same thread without remove returns same instance", ctxA, ctxB);
        Assert.assertEquals("request A's trace-id leaks to request B",
                "trace-A-123", getStringValue(containerB, "req-trace-id"));
    }

    /**
     * Verifies that calling remove() between requests prevents metadata pollution.
     * This is the core test proving remove() is necessary for thread-pool safety.
     */
    @Test
    public void testRemovePreventsMetadataPollution() {
        // Simulate request A: write request-specific metadata
        MetadataContext ctxA = MetadataContextHolder.get();
        MetadataContainer containerA = ctxA.getMetadataContainer(MetadataType.CUSTOM, false);
        containerA.putMetadataStringValue("req-trace-id", "trace-A-123", TransitiveType.PASS_THROUGH);
        String valueInA = getStringValue(containerA, "req-trace-id");
        Assert.assertEquals("trace-A-123", valueInA);

        // Request A ends — cleanup
        MetadataContextHolder.remove();

        // Simulate request B on the same thread (thread pool reuse)
        MetadataContext ctxB = MetadataContextHolder.get();
        MetadataContainer containerB = ctxB.getMetadataContainer(MetadataType.CUSTOM, false);

        // Request B gets a fresh context — no pollution from request A
        Assert.assertNotSame("after remove, a new context instance is created", ctxA, ctxB);
        Assert.assertNull("request A's trace-id must NOT leak to request B",
                getStringValue(containerB, "req-trace-id"));
    }
}
