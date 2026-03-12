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

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class MetadataContextHolderTest {

    @After
    public void after() {
        MetadataContextHolder.remove();
    }

    @Test
    public void testGetReturnsNonNull() {
        MetadataContext ctx = MetadataContextHolder.get();
        Assert.assertNotNull(ctx);
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
        Map<String, String> data = new HashMap<>();
        data.put("test-key", "test-val");
        custom.putFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE, data);

        MetadataContextHolder.set(custom);
        MetadataContext retrieved = MetadataContextHolder.get();

        Assert.assertEquals("test-val", retrieved.getTransitiveMetadata().get("test-key"));
    }

    @Test
    public void testRemoveClearsContext() {
        MetadataContext ctx1 = MetadataContextHolder.get();
        Map<String, String> data = new HashMap<>();
        data.put("key", "val");
        ctx1.putFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE, data);

        MetadataContextHolder.remove();

        MetadataContext ctx2 = MetadataContextHolder.get();
        // After remove, a fresh context is created
        Assert.assertNotSame(ctx1, ctx2);
    }

    @Test
    public void testInitInjectsUpstreamMetadata() {
        Map<String, String> transitiveData = new HashMap<>();
        transitiveData.put("t-key", "t-val");

        Map<String, String> disposableData = new HashMap<>();
        disposableData.put("d-key", "d-val");

        Map<String, String> appData = new HashMap<>();
        appData.put("a-key", "a-val");

        MetadataContextHolder.init(transitiveData, disposableData, appData);

        MetadataContext ctx = MetadataContextHolder.get();
        Assert.assertEquals("t-val", ctx.getTransitiveMetadata().get("t-key"));
        // disposable data is injected to upstream (caller) container
        Map<String, String> upstreamDisposable = ctx.getFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_DISPOSABLE);
        Assert.assertEquals("d-val", upstreamDisposable.get("d-key"));
        // application data is injected to upstream (caller) container
        Map<String, String> upstreamApp = ctx.getFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_APPLICATION);
        Assert.assertEquals("a-val", upstreamApp.get("a-key"));
    }

    @Test
    public void testInitWithNullMaps() {
        // Should not throw
        MetadataContextHolder.init(null, null, null);
        MetadataContext ctx = MetadataContextHolder.get();
        Assert.assertNotNull(ctx);
    }

    @Test
    public void testInitWithEmptyMaps() {
        MetadataContextHolder.init(new HashMap<>(), new HashMap<>(), new HashMap<>());
        MetadataContext ctx = MetadataContextHolder.get();
        Assert.assertNotNull(ctx);
        Assert.assertTrue(ctx.getTransitiveMetadata().isEmpty());
    }
}
