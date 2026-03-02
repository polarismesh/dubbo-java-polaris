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

package com.tencent.polaris.dubbo.configuration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * PolarisDynamicConfigurationFactory 单元测试类
 *
 * @author Yuwei Fu
 */
public class PolarisDynamicConfigurationFactoryTest {

    private MockedStatic<PolarisOperators> polarisOperatorsMock;
    private PolarisOperator mockOperator;
    private PolarisConfig mockPolarisConfig;
    private ConfigFileService mockConfigFileService;
    private ConfigFilePublishService mockConfigFilePublishService;

    @Before
    public void setUp() {
        // 初始化 Mock 对象
        mockOperator = mock(PolarisOperator.class);
        mockPolarisConfig = mock(PolarisConfig.class);
        mockConfigFileService = mock(ConfigFileService.class);
        mockConfigFilePublishService = mock(ConfigFilePublishService.class);

        // 配置 PolarisOperator 的行为
        when(mockOperator.getPolarisConfig()).thenReturn(mockPolarisConfig);
        when(mockOperator.getConfigFileAPI()).thenReturn(mockConfigFileService);
        when(mockOperator.getConfigFilePublishAPI()).thenReturn(mockConfigFilePublishService);
        when(mockPolarisConfig.getNamespace()).thenReturn("default");

        // Mock 静态方法
        polarisOperatorsMock = Mockito.mockStatic(PolarisOperators.class);
        polarisOperatorsMock.when(() -> PolarisOperators.loadOrStoreForConfig(anyString(), anyInt(), anyMap()))
                .thenReturn(mockOperator);
    }

    @After
    public void tearDown() {
        if (polarisOperatorsMock != null) {
            polarisOperatorsMock.close();
        }
    }

    /**
     * 测试：调用 createDynamicConfiguration 方法传入正常 URL 时返回 PolarisDynamicConfiguration 实例
     */
    @Test
    public void testCreateDynamicConfiguration_withNormalUrl_returnsPolarisDynamicConfiguration() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        URL url = URL.valueOf("polaris://127.0.0.1:8091");

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);
        assertTrue("返回的实例应为 PolarisDynamicConfiguration 类型",
                configuration instanceof PolarisDynamicConfiguration);
    }

    /**
     * 测试：URL 中 namespace 参数为 "dubbo" 时被正确移除
     */
    @Test
    public void testCreateDynamicConfiguration_withDubboNamespace_removesNamespaceParameter() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        URL url = URL.valueOf("polaris://127.0.0.1:8091?namespace=dubbo&otherParam=value");

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);

        // 验证调用 PolarisOperators.loadOrStoreForConfig 时 namespace 参数已被移除
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq("127.0.0.1"),
                eq(8091),
                argThat(params -> !params.containsKey("namespace") && "value".equals(params.get("otherParam")))
        ));
    }

    /**
     * 测试：URL 中 namespace 参数不为 "dubbo" 时被正确保留
     */
    @Test
    public void testCreateDynamicConfiguration_withNonDubboNamespace_preservesNamespaceParameter() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        URL url = URL.valueOf("polaris://127.0.0.1:8091?namespace=custom-namespace");

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);

        // 验证调用 PolarisOperators.loadOrStoreForConfig 时 namespace 参数被保留
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq("127.0.0.1"),
                eq(8091),
                argThat(params -> "custom-namespace".equals(params.get("namespace")))
        ));
    }

    /**
     * 测试：host 和 port 参数被正确解析和传递
     */
    @Test
    public void testCreateDynamicConfiguration_hostAndPort_correctlyParsed() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        String host = "192.168.1.100";
        int port = 9090;
        URL url = URL.valueOf(String.format("polaris://%s:%d", host, port));

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);

        // 验证 host 和 port 被正确传递
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq(host),
                eq(port),
                anyMap()
        ));
    }

    /**
     * 测试：URL 中包含多个参数时，参数被正确传递
     */
    @Test
    public void testCreateDynamicConfiguration_withMultipleParameters_allParametersPassed() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        URL url = URL.valueOf("polaris://127.0.0.1:8091?namespace=production&token=secret&timeout=3000");

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);

        // 验证所有参数被正确传递
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq("127.0.0.1"),
                eq(8091),
                argThat(params ->
                        "production".equals(params.get("namespace")) &&
                                "secret".equals(params.get("token")) &&
                                "3000".equals(params.get("timeout"))
                )
        ));
    }

    /**
     * 测试：URL 中没有 namespace 参数时正常工作
     */
    @Test
    public void testCreateDynamicConfiguration_withoutNamespace_worksNormally() {
        // Given
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();
        URL url = URL.valueOf("polaris://127.0.0.1:8091?token=mytoken");

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("返回的 DynamicConfiguration 不应为 null", configuration);
        assertTrue("返回的实例应为 PolarisDynamicConfiguration 类型",
                configuration instanceof PolarisDynamicConfiguration);
    }
}
