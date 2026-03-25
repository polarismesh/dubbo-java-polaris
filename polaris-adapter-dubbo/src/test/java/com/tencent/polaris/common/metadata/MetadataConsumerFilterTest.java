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
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class MetadataConsumerFilterTest {

    private final MetadataConsumerFilter filter = new MetadataConsumerFilter();

    private static String getStringValue(MetadataContainer container, String key) {
        MetadataStringValue val = container.getMetadataValue(key);
        return val != null ? val.getStringValue() : null;
    }

    @After
    public void after() {
        MetadataContextHolder.remove();
    }

    /**
     * Consumer filter should ensure MetadataContext is initialized (enriched)
     * before the invocation proceeds.
     */
    @Test
    public void testContextInitializedBeforeInvocation() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(invocation)).thenReturn(result);

        Result ret = filter.invoke(invoker, invocation);

        Assert.assertSame(result, ret);
        Mockito.verify(invoker).invoke(invocation);
    }

    /**
     * Consumer filter must NOT clean up context after invocation,
     * because the business thread may call multiple Dubbo services sharing the same context.
     */
    @Test
    public void testContextNotClearedAfterInvocation() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(invocation)).thenReturn(result);

        // Write metadata before invocation
        MetadataContext ctxBefore = MetadataContextHolder.get();
        ctxBefore.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("trace-id", "aaa-111", TransitiveType.PASS_THROUGH);

        filter.invoke(invoker, invocation);

        // After filter, context must still be the SAME instance with data intact
        MetadataContext ctxAfter = MetadataContextHolder.get();
        Assert.assertSame("consumer filter must not clear context", ctxBefore, ctxAfter);
        Assert.assertEquals("metadata must survive across consumer filter",
                "aaa-111",
                getStringValue(ctxAfter.getMetadataContainer(MetadataType.CUSTOM, false), "trace-id"));
    }

    /**
     * Simulates a business thread calling two Dubbo services sequentially.
     * The second call must still see the metadata from before the first call.
     */
    @Test
    public void testContextSharedAcrossMultipleConsumerCalls() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        // Business thread sets up metadata
        MetadataContext ctx = MetadataContextHolder.get();
        ctx.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("biz-key", "shared-val", TransitiveType.PASS_THROUGH);

        // Call service A
        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Call service B — should still see the metadata
        MetadataContext ctxB = MetadataContextHolder.get();
        Assert.assertSame("same context across calls", ctx, ctxB);
        Assert.assertEquals("metadata shared across calls",
                "shared-val",
                getStringValue(ctxB.getMetadataContainer(MetadataType.CUSTOM, false), "biz-key"));

        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Still intact
        MetadataContext ctxC = MetadataContextHolder.get();
        Assert.assertSame(ctx, ctxC);
        Assert.assertEquals("shared-val",
                getStringValue(ctxC.getMetadataContainer(MetadataType.CUSTOM, false), "biz-key"));
    }
}
