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

import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配置中心地址配置集成测试
 * <p>
 * 测试当配置 dubbo.config-center.address=polaris://HOST:PORT 时，
 * Polaris 动态配置是否能够正确生效。
 * </p>
 *
 * @author dubbo-polaris
 */
public class PolarisConfigCenterIntegrationTest {

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
     * 测试：通过 SPI 机制加载 PolarisDynamicConfigurationFactory
     */
    @Test
    public void testSPILoading_polarisDynamicConfigurationFactory() {
        // Given
        ExtensionLoader<DynamicConfigurationFactory> extensionLoader = 
                ExtensionLoader.getExtensionLoader(DynamicConfigurationFactory.class);

        // When
        DynamicConfigurationFactory factory = extensionLoader.getExtension("polaris");

        // Then
        assertNotNull("SPI 应能加载 polaris 扩展", factory);
        assertTrue("加载的工厂应为 PolarisDynamicConfigurationFactory 类型", 
                factory instanceof PolarisDynamicConfigurationFactory);
    }

    /**
     * 测试：配置 polaris://127.0.0.1:8091 地址时正确创建 DynamicConfiguration
     */
    @Test
    public void testConfigCenterAddress_createsDynamicConfiguration() {
        // Given
        String host = "127.0.0.1";
        int port = 8091;
        URL url = URL.valueOf(String.format("polaris://%s:%d", host, port));
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        assertNotNull("应创建 DynamicConfiguration 实例", configuration);
        assertTrue("实例应为 PolarisDynamicConfiguration 类型", 
                configuration instanceof PolarisDynamicConfiguration);
    }

    /**
     * 测试：创建配置实例时调用 PolarisOperators.loadOrStoreForConfig 方法
     */
    @Test
    public void testCreateConfiguration_callsLoadOrStoreForConfig() {
        // Given
        String host = "127.0.0.1";
        int port = 8091;
        URL url = URL.valueOf(String.format("polaris://%s:%d", host, port));
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq(host),
                eq(port),
                anyMap()
        ), times(1));
    }

    /**
     * 测试：URL 中的 host 和 port 正确传递给 PolarisOperators.loadOrStoreForConfig
     */
    @Test
    public void testConfigCenterAddress_hostAndPortCorrectlyPassed() {
        // Given
        String host = "192.168.1.100";
        int port = 9090;
        URL url = URL.valueOf(String.format("polaris://%s:%d", host, port));
        
        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                hostCaptor.capture(),
                portCaptor.capture(),
                anyMap()
        ));
        
        assertEquals("host 应正确传递", host, hostCaptor.getValue());
        assertEquals("port 应正确传递", Integer.valueOf(port), portCaptor.getValue());
    }

    /**
     * 测试：URL 中的额外参数正确传递给 PolarisOperators.loadOrStoreForConfig
     */
    @Test
    public void testConfigCenterAddress_extraParametersCorrectlyPassed() {
        // Given
        URL url = URL.valueOf("polaris://127.0.0.1:8091?namespace=production&token=secret&timeout=5000");
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                anyString(),
                anyInt(),
                paramsCaptor.capture()
        ));
        
        Map<String, String> capturedParams = paramsCaptor.getValue();
        assertEquals("namespace 参数应正确传递", "production", capturedParams.get("namespace"));
        assertEquals("token 参数应正确传递", "secret", capturedParams.get("token"));
        assertEquals("timeout 参数应正确传递", "5000", capturedParams.get("timeout"));
    }

    /**
     * 测试：正确获取 ConfigFileService 实例
     */
    @Test
    public void testConfigCenterAddress_configFileServiceCorrectlyObtained() {
        // Given
        URL url = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        verify(mockOperator).getConfigFileAPI();
    }

    /**
     * 测试：正确获取 ConfigFilePublishService 实例
     */
    @Test
    public void testConfigCenterAddress_configFilePublishServiceCorrectlyObtained() {
        // Given
        URL url = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        verify(mockOperator).getConfigFilePublishAPI();
    }

    /**
     * 测试：正确获取 PolarisConfig 实例
     */
    @Test
    public void testConfigCenterAddress_polarisConfigCorrectlyObtained() {
        // Given
        URL url = URL.valueOf("polaris://127.0.0.1:8091");
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        verify(mockOperator).getPolarisConfig();
    }

    /**
     * 测试：配置 namespace=dubbo 时参数被正确移除
     */
    @Test
    public void testConfigCenterAddress_dubboNamespaceRemoved() {
        // Given
        URL url = URL.valueOf("polaris://127.0.0.1:8091?namespace=dubbo&token=mytoken");
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        factory.getDynamicConfiguration(url);

        // Then
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                anyString(),
                anyInt(),
                paramsCaptor.capture()
        ));
        
        Map<String, String> capturedParams = paramsCaptor.getValue();
        assertFalse("namespace=dubbo 应被移除", capturedParams.containsKey("namespace"));
        assertEquals("其他参数应保留", "mytoken", capturedParams.get("token"));
    }

    /**
     * 测试：完整的配置中心地址配置流程
     */
    @Test
    public void testFullConfigCenterFlow() {
        // Given
        String host = "polaris-server.example.com";
        int port = 8091;
        String namespace = "production";
        String token = "auth-token";
        
        URL url = URL.valueOf(String.format(
                "polaris://%s:%d?namespace=%s&token=%s", 
                host, port, namespace, token));
        
        PolarisDynamicConfigurationFactory factory = new PolarisDynamicConfigurationFactory();

        // When
        DynamicConfiguration configuration = factory.getDynamicConfiguration(url);

        // Then
        // 1. 验证创建了 DynamicConfiguration 实例
        assertNotNull("应创建 DynamicConfiguration 实例", configuration);
        assertTrue("实例应为 PolarisDynamicConfiguration 类型", 
                configuration instanceof PolarisDynamicConfiguration);
        
        // 2. 验证 PolarisOperators.loadOrStoreForConfig 被调用且参数正确
        polarisOperatorsMock.verify(() -> PolarisOperators.loadOrStoreForConfig(
                eq(host),
                eq(port),
                argThat(params -> 
                        namespace.equals(params.get("namespace")) &&
                        token.equals(params.get("token"))
                )
        ));
        
        // 3. 验证获取了必要的服务实例
        verify(mockOperator).getPolarisConfig();
        verify(mockOperator).getConfigFileAPI();
        verify(mockOperator).getConfigFilePublishAPI();
    }
}
