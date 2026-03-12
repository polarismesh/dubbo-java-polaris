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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MetadataContextTest {

    private MetadataContext metadataContext;

    @Before
    public void before() {
        metadataContext = new MetadataContext();
    }

    @Test
    public void testPutAndGetTransitiveFragment() {
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");

        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE, data);
        Map<String, String> result = metadataContext.getFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE);

        Assert.assertEquals("value1", result.get("key1"));
        Assert.assertEquals("value2", result.get("key2"));
    }

    @Test
    public void testPutAndGetDisposableFragment() {
        Map<String, String> data = new HashMap<>();
        data.put("dkey", "dval");

        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_DISPOSABLE, data);
        Map<String, String> result = metadataContext.getFragmentContext(MetadataContext.FRAGMENT_DISPOSABLE);

        Assert.assertEquals("dval", result.get("dkey"));
    }

    @Test
    public void testPutAndGetApplicationFragment() {
        Map<String, String> data = new HashMap<>();
        data.put("app-key", "app-val");

        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_APPLICATION, data);
        Map<String, String> result = metadataContext.getFragmentContext(MetadataContext.FRAGMENT_APPLICATION);

        Assert.assertEquals("app-val", result.get("app-key"));
    }

    @Test
    public void testPutAndGetUpstreamDisposableFragment() {
        Map<String, String> data = new HashMap<>();
        data.put("ud-key", "ud-val");

        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_DISPOSABLE, data);
        Map<String, String> result = metadataContext.getFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_DISPOSABLE);

        Assert.assertEquals("ud-val", result.get("ud-key"));
    }

    @Test
    public void testPutAndGetUpstreamApplicationFragment() {
        Map<String, String> data = new HashMap<>();
        data.put("ua-key", "ua-val");

        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_APPLICATION, data);
        Map<String, String> result = metadataContext.getFragmentContext(MetadataContext.FRAGMENT_UPSTREAM_APPLICATION);

        Assert.assertEquals("ua-val", result.get("ua-key"));
    }

    @Test
    public void testUnknownFragmentReturnsEmpty() {
        Map<String, String> result = metadataContext.getFragmentContext("unknown-fragment");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testPutUnknownFragmentNoOp() {
        Map<String, String> data = new HashMap<>();
        data.put("k", "v");
        metadataContext.putFragmentContext("unknown-fragment", data);
        // no exception thrown
    }

    @Test
    public void testGetTransitiveMetadataConvenience() {
        Map<String, String> data = new HashMap<>();
        data.put("t-key", "t-val");
        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE, data);

        Map<String, String> result = metadataContext.getTransitiveMetadata();
        Assert.assertEquals("t-val", result.get("t-key"));
    }

    @Test
    public void testGetDisposableMetadataConvenience() {
        Map<String, String> data = new HashMap<>();
        data.put("d-key", "d-val");
        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_DISPOSABLE, data);

        Map<String, String> result = metadataContext.getDisposableMetadata();
        Assert.assertEquals("d-val", result.get("d-key"));
    }

    @Test
    public void testGetApplicationMetadataConvenience() {
        Map<String, String> data = new HashMap<>();
        data.put("a-key", "a-val");
        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_APPLICATION, data);

        Map<String, String> result = metadataContext.getApplicationMetadata();
        Assert.assertEquals("a-val", result.get("a-key"));
    }

    @Test
    public void testFragmentsAreIsolatedWithDifferentKeys() {
        Map<String, String> transitive = new HashMap<>();
        transitive.put("transitive-key", "transitive-val");
        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_TRANSITIVE, transitive);

        Map<String, String> disposable = new HashMap<>();
        disposable.put("disposable-key", "disposable-val");
        metadataContext.putFragmentContext(MetadataContext.FRAGMENT_DISPOSABLE, disposable);

        Assert.assertEquals("transitive-val", metadataContext.getTransitiveMetadata().get("transitive-key"));
        Assert.assertNull(metadataContext.getTransitiveMetadata().get("disposable-key"));
        Assert.assertEquals("disposable-val", metadataContext.getDisposableMetadata().get("disposable-key"));
        Assert.assertNull(metadataContext.getDisposableMetadata().get("transitive-key"));
    }
}
