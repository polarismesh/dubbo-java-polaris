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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.dubbo.common.URL;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StaticMetadataManagerTest {

    @Before
    public void before() throws Exception {
        resetInstance();
    }

    @After
    public void after() throws Exception {
        resetInstance();
    }

    private void resetInstance() throws Exception {
        Field instanceField = StaticMetadataManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    public void testGetInstanceReturnsNullBeforeCreate() {
        Assert.assertNull(StaticMetadataManager.getInstance());
    }

    @Test
    public void testGetOrCreateWithEmptyUrl() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/test");
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);
        Assert.assertNotNull(mgr);
        Assert.assertNotNull(mgr.getMergedStaticMetadata());
    }

    @Test
    public void testGetOrCreateSingleton() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/test");
        StaticMetadataManager mgr1 = StaticMetadataManager.getOrCreate(url);
        StaticMetadataManager mgr2 = StaticMetadataManager.getOrCreate(url);
        Assert.assertSame(mgr1, mgr2);
    }

    @Test
    public void testGetInstanceAfterCreate() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/test");
        StaticMetadataManager created = StaticMetadataManager.getOrCreate(url);
        Assert.assertSame(created, StaticMetadataManager.getInstance());
    }

    @Test
    public void testUrlMetadataParsing() {
        Map<String, String> params = new HashMap<>();
        params.put("polaris_metadata_content_env", "prod");
        params.put("polaris_metadata_content_region", "us-east");
        params.put("other_param", "ignored");

        URL url = new URL("dubbo", "127.0.0.1", 20880, "test", params);
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);

        Map<String, String> merged = mgr.getMergedStaticMetadata();
        Assert.assertEquals("prod", merged.get("env"));
        Assert.assertEquals("us-east", merged.get("region"));
        Assert.assertFalse(merged.containsKey("other_param"));
    }

    @Test
    public void testUrlTransitiveMetadataParsing() {
        Map<String, String> params = new HashMap<>();
        params.put("polaris_metadata_transitive_trace-id", "abc123");

        URL url = new URL("dubbo", "127.0.0.1", 20880, "test", params);
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);

        Map<String, String> transitive = mgr.getMergedStaticTransitiveMetadata();
        Assert.assertEquals("abc123", transitive.get("trace-id"));
    }

    @Test
    public void testUrlDisposableMetadataParsing() {
        Map<String, String> params = new HashMap<>();
        params.put("polaris_metadata_disposable_req-id", "req-001");

        URL url = new URL("dubbo", "127.0.0.1", 20880, "test", params);
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);

        Map<String, String> disposable = mgr.getMergedStaticDisposableMetadata();
        Assert.assertEquals("req-001", disposable.get("req-id"));
    }

    @Test
    public void testLocationMetadataFromUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("polaris_metadata_content_region", "cn-north");
        params.put("polaris_metadata_content_zone", "zone-a");
        params.put("polaris_metadata_content_campus", "campus-1");

        URL url = new URL("dubbo", "127.0.0.1", 20880, "test", params);
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);

        Assert.assertEquals("cn-north", mgr.getRegion());
        Assert.assertEquals("zone-a", mgr.getZone());
        Assert.assertEquals("campus-1", mgr.getCampus());

        Map<String, String> locationMeta = mgr.getLocationMetadata();
        Assert.assertEquals("cn-north", locationMeta.get(MetadataConstants.LOCATION_KEY_REGION));
        Assert.assertEquals("zone-a", locationMeta.get(MetadataConstants.LOCATION_KEY_ZONE));
        Assert.assertEquals("campus-1", locationMeta.get(MetadataConstants.LOCATION_KEY_CAMPUS));
    }

    @Test
    public void testLocationMetadataEmptyWhenNotSet() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/test");
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);

        Map<String, String> locationMeta = mgr.getLocationMetadata();
        // location might be empty if no env vars are set either
        Assert.assertNotNull(locationMeta);
    }

    @Test
    public void testToString() {
        URL url = URL.valueOf("dubbo://127.0.0.1:20880/test");
        StaticMetadataManager mgr = StaticMetadataManager.getOrCreate(url);
        String str = mgr.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("StaticMetadataManager"));
    }
}
