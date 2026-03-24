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
import org.apache.dubbo.rpc.RpcException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class MetadataProviderFilterTest {

    private final MetadataProviderFilter filter = new MetadataProviderFilter();

    private static String getStringValue(MetadataContainer container, String key) {
        MetadataStringValue val = container.getMetadataValue(key);
        return val != null ? val.getStringValue() : null;
    }

    @After
    public void after() {
        MetadataContextHolder.remove();
    }

    @Test
    public void testContextClearedAfterSuccessfulInvocation() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(invocation)).thenReturn(result);

        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("trace-id", "aaa-111", TransitiveType.PASS_THROUGH);

        MetadataContext ctxB = MetadataContextHolder.get();
        Assert.assertSame("context must be the same before cleanup", ctxA, ctxB);

        Result ret = filter.invoke(invoker, invocation);
        Assert.assertSame(result, ret);

        MetadataContext ctxC = MetadataContextHolder.get();
        Assert.assertNotSame("context must be renewed after cleanup", ctxA, ctxC);
        Assert.assertNull("old metadata must not leak",
                getStringValue(ctxC.getMetadataContainer(MetadataType.CUSTOM, false), "trace-id"));
    }

    @Test
    public void testContextClearedAfterException() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Mockito.when(invoker.invoke(invocation)).thenThrow(new RpcException("boom"));

        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("trace-id", "err-222", TransitiveType.PASS_THROUGH);

        try {
            filter.invoke(invoker, invocation);
            Assert.fail("expected RpcException");
        } catch (RpcException ignored) {
        }

        MetadataContext ctxB = MetadataContextHolder.get();
        Assert.assertNotSame("context must be renewed after cleanup", ctxA, ctxB);
        Assert.assertNull("old metadata must not leak after exception",
                getStringValue(ctxB.getMetadataContainer(MetadataType.CUSTOM, false), "trace-id"));
    }

    @Test
    public void testThreadReuseNoPollution() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        // Request A
        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("user-id", "user-A", TransitiveType.PASS_THROUGH);
        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Request B (same thread)
        MetadataContext ctxB = MetadataContextHolder.get();
        ctxB.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("user-id", "user-B", TransitiveType.PASS_THROUGH);
        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Request C — should be completely clean
        MetadataContext ctxC = MetadataContextHolder.get();
        MetadataContainer containerC = ctxC.getMetadataContainer(MetadataType.CUSTOM, false);

        Assert.assertNotSame(ctxA, ctxC);
        Assert.assertNotSame(ctxB, ctxC);
        Assert.assertNull("no leftover from any previous request",
                getStringValue(containerC, "user-id"));
    }
}
