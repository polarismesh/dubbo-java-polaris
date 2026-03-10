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

package com.tencent.polaris.common.registry.lossless;

import org.apache.dubbo.common.URL;
import org.junit.Assert;
import org.junit.Test;

public class DubboBaseInstanceTest {

    @Test
    public void testBasicMapping() {
        URL url = URL.valueOf("dubbo://192.168.1.1:20880/com.example.FooService?namespace=default");
        DubboBaseInstance instance = new DubboBaseInstance("default", url);

        Assert.assertEquals("default", instance.getNamespace());
        Assert.assertEquals("com.example.FooService", instance.getService());
        Assert.assertEquals("192.168.1.1", instance.getHost());
        Assert.assertEquals(20880, instance.getPort());
    }

    @Test
    public void testCustomNamespace() {
        URL url = URL.valueOf("dubbo://10.0.0.1:20881/com.example.BarService");
        DubboBaseInstance instance = new DubboBaseInstance("production", url);

        Assert.assertEquals("production", instance.getNamespace());
        Assert.assertEquals("com.example.BarService", instance.getService());
        Assert.assertEquals("10.0.0.1", instance.getHost());
        Assert.assertEquals(20881, instance.getPort());
    }

    @Test
    public void testEqualsAndHashCode() {
        URL url1 = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");
        URL url2 = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService?version=1.0");

        DubboBaseInstance instance1 = new DubboBaseInstance("default", url1);
        DubboBaseInstance instance2 = new DubboBaseInstance("default", url2);

        Assert.assertEquals(instance1, instance2);
        Assert.assertEquals(instance1.hashCode(), instance2.hashCode());
    }

    @Test
    public void testNotEquals_differentService() {
        URL url1 = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");
        URL url2 = URL.valueOf("dubbo://10.0.0.1:20880/com.example.BarService");

        DubboBaseInstance instance1 = new DubboBaseInstance("default", url1);
        DubboBaseInstance instance2 = new DubboBaseInstance("default", url2);

        Assert.assertNotEquals(instance1, instance2);
    }

    @Test
    public void testNotEquals_differentPort() {
        URL url1 = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");
        URL url2 = URL.valueOf("dubbo://10.0.0.1:20881/com.example.FooService");

        DubboBaseInstance instance1 = new DubboBaseInstance("default", url1);
        DubboBaseInstance instance2 = new DubboBaseInstance("default", url2);

        Assert.assertNotEquals(instance1, instance2);
    }

    @Test
    public void testNotEquals_differentNamespace() {
        URL url = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");

        DubboBaseInstance instance1 = new DubboBaseInstance("default", url);
        DubboBaseInstance instance2 = new DubboBaseInstance("production", url);

        Assert.assertNotEquals(instance1, instance2);
    }

    @Test
    public void testToString() {
        URL url = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");
        DubboBaseInstance instance = new DubboBaseInstance("default", url);
        String str = instance.toString();

        Assert.assertTrue(str.contains("default"));
        Assert.assertTrue(str.contains("com.example.FooService"));
        Assert.assertTrue(str.contains("10.0.0.1"));
        Assert.assertTrue(str.contains("20880"));
    }

    @Test
    public void testDefaultRegistry() {
        URL url = URL.valueOf("dubbo://10.0.0.1:20880/com.example.FooService");
        DubboBaseInstance instance = new DubboBaseInstance("default", url);

        Assert.assertEquals("polaris", instance.getRegistry());
    }
}
