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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tencent.polaris.api.exception.ErrorCode;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.exception.ServerCodes;
import com.tencent.polaris.api.plugin.common.ValueContext;
import com.tencent.polaris.api.plugin.compose.Extensions;
import com.tencent.polaris.api.plugin.configuration.ConfigFileResponse;
import com.tencent.polaris.client.api.SDKContext;
import com.tencent.polaris.client.flow.BaseFlow;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.configuration.api.core.ChangeType;
import com.tencent.polaris.configuration.api.core.ConfigFile;
import com.tencent.polaris.configuration.api.core.ConfigFileChangeEvent;
import com.tencent.polaris.configuration.api.core.ConfigFileChangeListener;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * PolarisDynamicConfiguration Test
 *
 * @author Yuwei Fu
 */
public class PolarisDynamicConfigurationTest {

    private MockedStatic<PolarisOperators> polarisOperatorsMock;
    private MockedStatic<BaseFlow> baseFlowMock;
    private PolarisOperator mockOperator;
    private PolarisConfig mockPolarisConfig;
    private ConfigFileService mockConfigFileService;
    private ConfigFilePublishService mockConfigFilePublishService;
    private ConfigFile mockConfigFile;
    private SDKContext mockSdkContext;
    private Extensions mockExtensions;
    private ValueContext mockValueContext;
    private PolarisDynamicConfiguration configuration;

    @Before
    public void setUp() {
        // 初始化 Mock 对象
        mockOperator = mock(PolarisOperator.class);
        mockPolarisConfig = mock(PolarisConfig.class);
        mockConfigFileService = mock(ConfigFileService.class);
        mockConfigFilePublishService = mock(ConfigFilePublishService.class);
        mockConfigFile = mock(ConfigFile.class);
        mockSdkContext = mock(SDKContext.class);
        mockExtensions = mock(Extensions.class);
        mockValueContext = mock(ValueContext.class);

        // 配置 PolarisOperator 的行为
        when(mockOperator.getPolarisConfig()).thenReturn(mockPolarisConfig);
        when(mockOperator.getConfigFileAPI()).thenReturn(mockConfigFileService);
        when(mockOperator.getConfigFilePublishAPI()).thenReturn(mockConfigFilePublishService);
        when(mockOperator.getSdkContext()).thenReturn(mockSdkContext);
        when(mockPolarisConfig.getNamespace()).thenReturn("default");

        // 配置 SDKContext 相关的 mock 链
        when(mockSdkContext.getExtensions()).thenReturn(mockExtensions);
        when(mockExtensions.getValueContext()).thenReturn(mockValueContext);
        when(mockValueContext.getClientId()).thenReturn("test-client-id");
        when(mockValueContext.getHost()).thenReturn("127.0.0.1");

        // 配置 ConfigFileService 的行为
        when(mockConfigFileService.getConfigFile(anyString(), anyString(), anyString()))
                .thenReturn(mockConfigFile);

        // Mock 静态方法
        polarisOperatorsMock = Mockito.mockStatic(PolarisOperators.class);
        polarisOperatorsMock.when(() -> PolarisOperators.loadOrStoreForConfig(anyString(), anyInt(), anyMap()))
                .thenReturn(mockOperator);

        // Mock BaseFlow.reportConfigEvent 静态方法
        baseFlowMock = Mockito.mockStatic(BaseFlow.class);
        baseFlowMock.when(() -> BaseFlow.reportConfigEvent(any(), any())).then(invocation -> null);

        // 创建被测对象
        URL url = URL.valueOf("polaris://127.0.0.1:8091");
        configuration = new PolarisDynamicConfiguration(url);
    }

    @After
    public void tearDown() {
        if (polarisOperatorsMock != null) {
            polarisOperatorsMock.close();
        }
        if (baseFlowMock != null) {
            baseFlowMock.close();
        }
    }

    // ==================== 配置监听功能测试 ====================

    /**
     * 测试：addListener 方法正确注册监听器到 Polaris ConfigFile
     */
    @Test
    public void testAddListener_registersListenerToConfigFile() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        ConfigurationListener listener = mock(ConfigurationListener.class);

        // When
        configuration.addListener(key, group, listener);

        // Then
        // 验证 ConfigFileService.getConfigFile 被调用
        verify(mockConfigFileService).getConfigFile(eq("default"), eq(group), eq(key));
        // 验证 ConfigFile.addChangeListener 被调用
        verify(mockConfigFile).addChangeListener(any(ConfigFileChangeListener.class));
    }

    /**
     * 测试：配置文件发生变更时触发已注册的 ConfigurationListener 回调
     */
    @Test
    public void testAddListener_configFileChange_triggersCallback() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String newValue = "new-config-value";
        AtomicReference<ConfigChangedEvent> capturedEvent = new AtomicReference<>();

        ConfigurationListener listener = event -> capturedEvent.set(event);

        // 捕获 ConfigFileChangeListener
        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // When
        configuration.addListener(key, group, listener);

        // 获取注册的 ConfigFileChangeListener
        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());
        ConfigFileChangeListener polarisListener = listenerCaptor.getValue();

        // 模拟配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn(newValue);
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.MODIFIED);

        // 触发配置变更
        polarisListener.onChange(polarisEvent);

        // Then
        assertNotNull("监听器应该收到配置变更事件", capturedEvent.get());
        assertEquals("配置 key 应正确", key, capturedEvent.get().getKey());
        assertEquals("配置 group 应正确", group, capturedEvent.get().getGroup());
        assertEquals("配置新值应正确", newValue, capturedEvent.get().getContent());
    }

    /**
     * 测试：配置变更类型为 ADDED 时正确转换为 Dubbo 的 ConfigChangeType.ADDED
     */
    @Test
    public void testAddListener_changeTypeAdded_convertsToDubboAdded() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        AtomicReference<ConfigChangedEvent> capturedEvent = new AtomicReference<>();

        ConfigurationListener listener = event -> capturedEvent.set(event);

        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // When
        configuration.addListener(key, group, listener);
        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());

        // 模拟 ADDED 类型的配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn("added-value");
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.ADDED);

        listenerCaptor.getValue().onChange(polarisEvent);

        // Then
        assertNotNull("监听器应该收到配置变更事件", capturedEvent.get());
        assertEquals("变更类型应为 ADDED", ConfigChangeType.ADDED, capturedEvent.get().getChangeType());
    }

    /**
     * 测试：配置变更类型为 DELETED 时正确转换为 Dubbo 的 ConfigChangeType.DELETED
     */
    @Test
    public void testAddListener_changeTypeDeleted_convertsToDubboDeleted() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        AtomicReference<ConfigChangedEvent> capturedEvent = new AtomicReference<>();

        ConfigurationListener listener = event -> capturedEvent.set(event);

        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // When
        configuration.addListener(key, group, listener);
        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());

        // 模拟 DELETED 类型的配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn(null);
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.DELETED);

        listenerCaptor.getValue().onChange(polarisEvent);

        // Then
        assertNotNull("监听器应该收到配置变更事件", capturedEvent.get());
        assertEquals("变更类型应为 DELETED", ConfigChangeType.DELETED, capturedEvent.get().getChangeType());
    }

    /**
     * 测试：配置变更类型为 MODIFIED 时正确转换为 Dubbo 的 ConfigChangeType.MODIFIED
     */
    @Test
    public void testAddListener_changeTypeModified_convertsToDubboModified() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        AtomicReference<ConfigChangedEvent> capturedEvent = new AtomicReference<>();

        ConfigurationListener listener = event -> capturedEvent.set(event);

        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // When
        configuration.addListener(key, group, listener);
        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());

        // 模拟 MODIFIED 类型的配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn("modified-value");
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.MODIFIED);

        listenerCaptor.getValue().onChange(polarisEvent);

        // Then
        assertNotNull("监听器应该收到配置变更事件", capturedEvent.get());
        assertEquals("变更类型应为 MODIFIED", ConfigChangeType.MODIFIED, capturedEvent.get().getChangeType());
    }

    /**
     * 测试：removeListener 方法正确从监听器集合中移除监听器
     */
    @Test
    public void testRemoveListener_removesListenerFromSet() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        AtomicReference<ConfigChangedEvent> capturedEvent = new AtomicReference<>();

        ConfigurationListener listener = event -> capturedEvent.set(event);

        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // 先添加监听器
        configuration.addListener(key, group, listener);
        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());

        // When - 移除监听器
        configuration.removeListener(key, group, listener);

        // 模拟配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn("new-value");
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.MODIFIED);

        listenerCaptor.getValue().onChange(polarisEvent);

        // Then - 监听器已被移除，不应收到事件
        assertNull("已移除的监听器不应收到配置变更事件", capturedEvent.get());
    }

    /**
     * 测试：同一配置文件添加多个监听器时，所有监听器都能收到回调
     */
    @Test
    public void testAddListener_multipleListeners_allReceiveCallback() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        AtomicReference<ConfigChangedEvent> capturedEvent1 = new AtomicReference<>();
        AtomicReference<ConfigChangedEvent> capturedEvent2 = new AtomicReference<>();

        ConfigurationListener listener1 = event -> capturedEvent1.set(event);
        ConfigurationListener listener2 = event -> capturedEvent2.set(event);

        ArgumentCaptor<ConfigFileChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(ConfigFileChangeListener.class);

        // When
        configuration.addListener(key, group, listener1);
        configuration.addListener(key, group, listener2);

        verify(mockConfigFile).addChangeListener(listenerCaptor.capture());

        // 模拟配置变更事件
        ConfigFileChangeEvent polarisEvent = mock(ConfigFileChangeEvent.class);
        when(polarisEvent.getNewValue()).thenReturn("new-value");
        when(polarisEvent.getChangeType()).thenReturn(ChangeType.MODIFIED);

        listenerCaptor.getValue().onChange(polarisEvent);

        // Then
        assertNotNull("监听器1应该收到配置变更事件", capturedEvent1.get());
        assertNotNull("监听器2应该收到配置变更事件", capturedEvent2.get());
    }

    // ==================== 配置获取功能测试 ====================

    /**
     * 测试：getConfig 方法成功获取存在的配置
     */
    @Test
    public void testGetConfig_existingConfig_returnsContent() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String expectedContent = "config-content-value";

        when(mockConfigFile.getContent()).thenReturn(expectedContent);

        // When
        String result = configuration.getConfig(key, group, 3000L);

        // Then
        assertEquals("应返回配置内容", expectedContent, result);
        verify(mockConfigFileService).getConfigFile(eq("default"), eq(group), eq(key));
    }

    /**
     * 测试：getConfig 方法在抛出 PolarisException 时返回 null
     */
    @Test
    public void testGetConfig_polarisException_returnsNull() {
        // Given
        String key = "test.properties";
        String group = "test-group";

        when(mockConfigFileService.getConfigFile(anyString(), anyString(), anyString()))
                .thenThrow(new PolarisException(ErrorCode.INTERNAL_ERROR, "Internal Server Error"));

        // When
        String result = configuration.getConfig(key, group, 3000L);

        // Then
        assertNull("发生 PolarisException 时应返回 null", result);
    }

    /**
     * 测试：getInternalProperty 方法使用 DEFAULT_GROUP 获取配置
     */
    @Test
    public void testGetInternalProperty_usesDefaultGroup() {
        // Given
        String key = "internal.property";
        String expectedContent = "internal-value";

        when(mockConfigFile.getContent()).thenReturn(expectedContent);

        // When
        Object result = configuration.getInternalProperty(key);

        // Then
        assertEquals("应返回配置内容", expectedContent, result);
        // 验证使用了 DEFAULT_GROUP（即 "dubbo"）
        verify(mockConfigFileService).getConfigFile(eq("default"), eq("dubbo"), eq(key));
    }

    /**
     * 测试：getInternalProperty 方法出错时返回 null
     */
    @Test
    public void testGetInternalProperty_polarisException_returnsNull() {
        // Given
        String key = "internal.property";

        when(mockConfigFileService.getConfigFile(anyString(), anyString(), anyString()))
                .thenThrow(new PolarisException(ErrorCode.INTERNAL_ERROR, "Internal Server Error"));

        // When
        Object result = configuration.getInternalProperty(key);

        // Then
        assertNull("发生 PolarisException 时应返回 null", result);
    }

    // ==================== 配置发布功能测试 ====================

    /**
     * 测试：publishConfig 方法发布成功返回 true
     */
    @Test
    public void testPublishConfig_success_returnsTrue() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String content = "new-config-content";

        ConfigFileResponse mockResponse = mock(ConfigFileResponse.class);
        when(mockResponse.getCode()).thenReturn(ServerCodes.EXECUTE_SUCCESS);
        when(mockConfigFilePublishService.upsertAndPublish(any())).thenReturn(mockResponse);

        // When
        boolean result = configuration.publishConfig(key, group, content);

        // Then
        assertTrue("发布成功应返回 true", result);
    }

    /**
     * 测试：publishConfig 方法在 Polaris 返回非成功响应码时返回 false
     */
    @Test
    public void testPublishConfig_nonSuccessResponse_returnsFalse() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String content = "new-config-content";

        ConfigFileResponse mockResponse = mock(ConfigFileResponse.class);
        when(mockResponse.getCode()).thenReturn(500001); // 非成功响应码
        when(mockResponse.getMessage()).thenReturn("Internal error");
        when(mockConfigFilePublishService.upsertAndPublish(any())).thenReturn(mockResponse);

        // When
        boolean result = configuration.publishConfig(key, group, content);

        // Then
        assertFalse("发布失败应返回 false", result);
    }

    /**
     * 测试：publishConfig 方法在抛出 PolarisException 时返回 false
     */
    @Test
    public void testPublishConfig_polarisException_returnsFalse() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String content = "new-config-content";

        when(mockConfigFilePublishService.upsertAndPublish(any()))
                .thenThrow(new PolarisException(ErrorCode.INTERNAL_ERROR, "Internal Server Error"));

        // When
        boolean result = configuration.publishConfig(key, group, content);

        // Then
        assertFalse("发生 PolarisException 时应返回 false", result);
    }

    /**
     * 测试：发布配置时 namespace、group、filename、content 参数正确设置
     */
    @Test
    public void testPublishConfig_parametersCorrectlySet() {
        // Given
        String key = "test.properties";
        String group = "test-group";
        String content = "new-config-content";

        ConfigFileResponse mockResponse = mock(ConfigFileResponse.class);
        when(mockResponse.getCode()).thenReturn(ServerCodes.EXECUTE_SUCCESS);

        ArgumentCaptor<com.tencent.polaris.configuration.api.rpc.ConfigPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(com.tencent.polaris.configuration.api.rpc.ConfigPublishRequest.class);
        when(mockConfigFilePublishService.upsertAndPublish(requestCaptor.capture())).thenReturn(mockResponse);

        // When
        configuration.publishConfig(key, group, content);

        // Then
        com.tencent.polaris.configuration.api.rpc.ConfigPublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals("namespace 应正确设置", "default", capturedRequest.getNamespace());
        assertEquals("group 应正确设置", group, capturedRequest.getGroup());
        assertEquals("filename 应正确设置", key, capturedRequest.getFilename());
        assertEquals("content 应正确设置", content, capturedRequest.getContent());
    }
}
