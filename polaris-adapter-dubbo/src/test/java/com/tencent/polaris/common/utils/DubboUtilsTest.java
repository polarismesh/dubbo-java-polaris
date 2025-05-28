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

package com.tencent.polaris.common.utils;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.registry.client.InstanceAddressURL;
import org.apache.dubbo.rpc.Invoker;
import org.junit.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test for {@link DubboUtils}.
 *
 * @author Haotian Zhang
 */
public class DubboUtilsTest {

    @Test
    public void testCheckIsApplicationModeWithInstanceAddressURL() {
        // 准备：创建InstanceAddressURL类型的mock Invoker
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        InstanceAddressURL url = Mockito.mock(InstanceAddressURL.class);
        when(invoker.getUrl()).thenReturn(url);

        // 执行 & 验证：InstanceAddressURL应该返回true
        assertThat(DubboUtils.checkIsApplicationMode(invoker)).isTrue();
    }

    @Test
    public void testCheckIsApplicationModeWithServiceConfigURL_RegisterModeAll() {
        // 准备：创建ServiceConfigURL类型的mock Invoker，设置registerMode为ALL
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        ServiceConfigURL url = Mockito.mock(ServiceConfigURL.class);
        when(invoker.getUrl()).thenReturn(url);
        when(url.getParameter(RegistryConstants.REGISTER_MODE_KEY, RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE))
                .thenReturn(RegistryConstants.DEFAULT_REGISTER_MODE_ALL);

        // 执行 & 验证：registerMode为ALL应该返回true
        assertThat(DubboUtils.checkIsApplicationMode(invoker)).isTrue();
    }

    @Test
    public void testCheckIsApplicationModeWithServiceConfigURL_RegisterModeInstance() {
        // 准备：创建ServiceConfigURL类型的mock Invoker，设置registerMode为INSTANCE
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        ServiceConfigURL url = Mockito.mock(ServiceConfigURL.class);
        when(invoker.getUrl()).thenReturn(url);
        when(url.getParameter(RegistryConstants.REGISTER_MODE_KEY, RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE))
                .thenReturn(RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE);

        // 执行 & 验证：registerMode为INSTANCE应该返回true
        assertThat(DubboUtils.checkIsApplicationMode(invoker)).isTrue();
    }

    @Test
    public void testCheckIsApplicationModeWithServiceConfigURL_RegisterModeInterface() {
        // 准备：创建ServiceConfigURL类型的mock Invoker，设置registerMode为INTERFACE
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        ServiceConfigURL url = Mockito.mock(ServiceConfigURL.class);
        when(invoker.getUrl()).thenReturn(url);
        when(url.getParameter(RegistryConstants.REGISTER_MODE_KEY, RegistryConstants.DEFAULT_REGISTER_MODE_INSTANCE))
                .thenReturn(RegistryConstants.DEFAULT_REGISTER_MODE_INTERFACE);

        // 执行 & 验证：registerMode为INTERFACE应该返回false
        assertThat(DubboUtils.checkIsApplicationMode(invoker)).isFalse();
    }

    @Test
    public void testCheckIsApplicationModeWithOtherURL() {
        // 准备：创建普通URL类型的mock Invoker
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        URL url = Mockito.mock(URL.class);
        when(invoker.getUrl()).thenReturn(url);

        // 执行 & 验证：普通URL类型应该返回false
        assertThat(DubboUtils.checkIsApplicationMode(invoker)).isFalse();
    }
}
