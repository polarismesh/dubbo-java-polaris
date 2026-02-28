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

package com.tencent.polaris.common.config;

import com.tencent.polaris.api.config.consumer.LoadBalanceConfig;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PolarisConfig 单元测试类
 *
 * @author Yuwei Fu
 */
public class PolarisConfigTest {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisConfigTest.class);

    private String originalTtlProperty;
    private String originalPolarisTtlProperty;

    @Before
    public void before() {
        // 保存原始系统属性，避免测试间干扰
        originalTtlProperty = System.getProperty(Consts.KEY_TTL);
        originalPolarisTtlProperty = System.getProperty(Consts.KEY_POLARIS_TTL);
    }

    @After
    public void after() {
        // 恢复原始系统属性
        if (originalTtlProperty != null) {
            System.setProperty(Consts.KEY_TTL, originalTtlProperty);
        } else {
            System.clearProperty(Consts.KEY_TTL);
        }
        if (originalPolarisTtlProperty != null) {
            System.setProperty(Consts.KEY_POLARIS_TTL, originalPolarisTtlProperty);
        } else {
            System.clearProperty(Consts.KEY_POLARIS_TTL);
        }
    }

    // ==================== GOVERNANCE 类型测试 ====================

    /**
     * 测试 GOVERNANCE 类型：默认参数场景
     */
    @Test
    public void testGovernanceType_withDefaultParameters() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        String host = "127.0.0.1";
        int port = 8091;

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE, host, port, parameters);

        // Assert
        Assert.assertEquals(Consts.DEFAULT_NAMESPACE, config.getNamespace());
        Assert.assertNull(config.getToken());
        Assert.assertEquals(Consts.DEFAULT_TTL, config.getTtl());
        Assert.assertEquals(LoadBalanceConfig.LOAD_BALANCE_ROUND_ROBIN, config.getLbPolicy());
        Assert.assertEquals(Long.valueOf(600000L), config.getServerSwitchInterval());

        // GOVERNANCE 类型：主端口是 discover 端口，config 端口默认 8093
        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含 127.0.0.1:8091",
                discoverAddresses.contains("127.0.0.1:8091"));

        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含 127.0.0.1:8093",
                configAddresses.contains("127.0.0.1:8093"));

        LOG.info("[Test] GOVERNANCE 默认参数测试通过: {}", config);
    }

    /**
     * 测试 GOVERNANCE 类型：自定义 namespace 和 token
     */
    @Test
    public void testGovernanceType_withCustomNamespaceAndToken() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "production");
        parameters.put(Consts.KEY_TOKEN, "my-secret-token");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "10.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals("production", config.getNamespace());
        Assert.assertEquals("my-secret-token", config.getToken());
    }

    /**
     * 测试 GOVERNANCE 类型：空 namespace 应使用默认值
     */
    @Test
    public void testGovernanceType_withEmptyNamespace_usesDefault() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(Consts.DEFAULT_NAMESPACE, config.getNamespace());
    }

    /**
     * 测试 GOVERNANCE 类型：自定义 config_port 参数
     */
    @Test
    public void testGovernanceType_withCustomConfigPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.CONFIG_PORT, "9093");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含自定义端口 9093",
                configAddresses.contains("127.0.0.1:9093"));

        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含主端口 8091",
                discoverAddresses.contains("127.0.0.1:8091"));
    }

    /**
     * 测试 GOVERNANCE 类型：other_addresses 参数
     */
    @Test
    public void testGovernanceType_withOtherAddresses() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_OTHER_ADDRESSES, "10.0.0.2:8091/10.0.0.3:8091");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "10.0.0.1", 8091, parameters);

        // Assert
        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含主地址",
                discoverAddresses.contains("10.0.0.1:8091"));
        Assert.assertTrue("discover 地址应包含额外地址 10.0.0.2:8091",
                discoverAddresses.contains("10.0.0.2:8091"));
        Assert.assertTrue("discover 地址应包含额外地址 10.0.0.3:8091",
                discoverAddresses.contains("10.0.0.3:8091"));
    }

    /**
     * 测试 GOVERNANCE 类型：自定义负载均衡策略
     */
    @Test
    public void testGovernanceType_withCustomLbPolicy() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_LB_POLICY, "weightedRandom");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals("weightedRandom", config.getLbPolicy());
    }

    /**
     * 测试 GOVERNANCE 类型：自定义 serverSwitchInterval
     */
    @Test
    public void testGovernanceType_withCustomServerSwitchInterval() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "300000");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(Long.valueOf(300000L), config.getServerSwitchInterval());
    }

    // ==================== CONFIG 类型测试 ====================

    /**
     * 测试 CONFIG 类型：默认参数场景
     */
    @Test
    public void testConfigType_withDefaultParameters() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        String host = "127.0.0.1";
        int port = 8093;

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.CONFIG, host, port, parameters);

        // Assert
        // CONFIG 类型：主端口是 config 端口，discover 端口默认 8091
        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含 127.0.0.1:8093",
                configAddresses.contains("127.0.0.1:8093"));

        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含默认端口 127.0.0.1:8091",
                discoverAddresses.contains("127.0.0.1:8091"));

        LOG.info("[Test] CONFIG 默认参数测试通过: {}", config);
    }

    /**
     * 测试 CONFIG 类型：自定义 discover_port 参数
     */
    @Test
    public void testConfigType_withCustomDiscoverPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.DISCOVER_PORT, "9091");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.CONFIG,
                "127.0.0.1", 8093, parameters);

        // Assert
        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含自定义端口 9091",
                discoverAddresses.contains("127.0.0.1:9091"));

        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含主端口 8093",
                configAddresses.contains("127.0.0.1:8093"));
    }

    // ==================== METADATA_REPORT 类型测试 ====================

    /**
     * 测试 METADATA_REPORT 类型：默认参数场景
     */
    @Test
    public void testMetadataReportType_withDefaultParameters() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        String host = "127.0.0.1";
        int port = 8091;

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.METADATA_REPORT,
                host, port, parameters);

        // Assert
        // METADATA_REPORT 类型：主端口是 discover 端口，config 端口默认 8093
        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue("discover 地址应包含 127.0.0.1:8091",
                discoverAddresses.contains("127.0.0.1:8091"));

        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含默认端口 127.0.0.1:8093",
                configAddresses.contains("127.0.0.1:8093"));

        LOG.info("[Test] METADATA_REPORT 默认参数测试通过: {}", config);
    }

    /**
     * 测试 METADATA_REPORT 类型：自定义 config_port 参数
     */
    @Test
    public void testMetadataReportType_withCustomConfigPort() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.CONFIG_PORT, "9093");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.METADATA_REPORT,
                "127.0.0.1", 8091, parameters);

        // Assert
        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue("config 地址应包含自定义端口 9093",
                configAddresses.contains("127.0.0.1:9093"));
    }

    // ==================== TTL 系统属性测试 ====================

    /**
     * 测试：通过系统属性设置自定义 TTL
     */
    @Test
    public void testTtl_withSystemProperty() {
        // Arrange
        System.setProperty(Consts.KEY_TTL, "10");
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(10, config.getTtl());
    }

    /**
     * 测试：系统属性 TTL 值无效时使用默认值
     */
    @Test
    public void testTtl_withInvalidSystemProperty_usesDefault() {
        // Arrange
        System.setProperty(Consts.KEY_TTL, "not_a_number");
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(Consts.DEFAULT_TTL, config.getTtl());
    }

    /**
     * 测试：未设置系统属性 TTL 时使用默认值
     */
    @Test
    public void testTtl_withoutSystemProperty_usesDefault() {
        // Arrange
        System.clearProperty(Consts.KEY_TTL);
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(Consts.DEFAULT_TTL, config.getTtl());
    }

    // ==================== polaris_namespace 覆盖优先级测试 ====================

    /**
     * 测试：polaris_namespace 参数覆盖 namespace 参数
     */
    @Test
    public void testPolarisNamespace_overridesNamespace() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "original-ns");
        parameters.put(Consts.KEY_POLARIS_NAMESPACE, "polaris-ns");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - polaris_namespace 应覆盖 namespace
        Assert.assertEquals("polaris-ns", config.getNamespace());
    }

    /**
     * 测试：polaris_namespace 为空时不覆盖 namespace
     */
    @Test
    public void testPolarisNamespace_emptyDoesNotOverride() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "original-ns");
        parameters.put(Consts.KEY_POLARIS_NAMESPACE, "");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - 空的 polaris_namespace 不覆盖，应使用原始 namespace
        Assert.assertEquals("original-ns", config.getNamespace());
    }

    /**
     * 测试：namespace 为空且 polaris_namespace 存在时，使用 polaris_namespace
     */
    @Test
    public void testPolarisNamespace_overridesDefaultNamespace() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_NAMESPACE, "polaris-ns");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - 即使 namespace 未设置，polaris_namespace 也应生效
        Assert.assertEquals("polaris-ns", config.getNamespace());
    }

    // ==================== polaris_token 覆盖优先级测试 ====================

    /**
     * 测试：polaris_token 参数覆盖 token 参数
     */
    @Test
    public void testPolarisToken_overridesToken() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TOKEN, "original-token");
        parameters.put(Consts.KEY_POLARIS_TOKEN, "polaris-token");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - polaris_token 应覆盖 token
        Assert.assertEquals("polaris-token", config.getToken());
    }

    /**
     * 测试：polaris_token 为空时不覆盖 token
     */
    @Test
    public void testPolarisToken_emptyDoesNotOverride() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_TOKEN, "original-token");
        parameters.put(Consts.KEY_POLARIS_TOKEN, "");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - 空的 polaris_token 不覆盖，应使用原始 token
        Assert.assertEquals("original-token", config.getToken());
    }

    /**
     * 测试：仅设置 polaris_token（无 token）
     */
    @Test
    public void testPolarisToken_onlyPolarisTokenSet() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_POLARIS_TOKEN, "polaris-only-token");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals("polaris-only-token", config.getToken());
    }

    // ==================== polaris_ttl 系统属性覆盖优先级测试 ====================

    /**
     * 测试：polaris_ttl 系统属性覆盖 ttl 系统属性
     */
    @Test
    public void testPolarisTtl_overridesTtl() {
        // Arrange
        System.setProperty(Consts.KEY_TTL, "10");
        System.setProperty(Consts.KEY_POLARIS_TTL, "20");
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - polaris_ttl 应覆盖 ttl
        Assert.assertEquals(20, config.getTtl());
    }

    /**
     * 测试：仅设置 polaris_ttl 系统属性
     */
    @Test
    public void testPolarisTtl_onlyPolarisTtlSet() {
        // Arrange
        System.clearProperty(Consts.KEY_TTL);
        System.setProperty(Consts.KEY_POLARIS_TTL, "15");
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals(15, config.getTtl());
    }

    /**
     * 测试：polaris_ttl 无效值时保持 ttl 的值
     */
    @Test
    public void testPolarisTtl_invalidValue_keepsTtlValue() {
        // Arrange
        System.setProperty(Consts.KEY_TTL, "10");
        System.setProperty(Consts.KEY_POLARIS_TTL, "not_a_number");
        Map<String, String> parameters = new HashMap<>();

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);

        // Assert - polaris_ttl 无效，应保持 ttl 的值
        Assert.assertEquals(10, config.getTtl());
    }

    // ==================== toString 测试 ====================

    /**
     * 测试：toString 方法包含关键信息
     */
    @Test
    public void testToString_containsKeyInfo() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "test-ns");
        parameters.put(Consts.KEY_TOKEN, "test-token");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "127.0.0.1", 8091, parameters);
        String result = config.toString();

        // Assert
        Assert.assertTrue("toString 应包含 namespace", result.contains("test-ns"));
        Assert.assertTrue("toString 应包含 token", result.contains("test-token"));
        Assert.assertTrue("toString 应包含 operatorType", result.contains("GOVERNANCE"));
        Assert.assertTrue("toString 应包含 discoverAddresses", result.contains("discoverAddresses"));
        Assert.assertTrue("toString 应包含 configAddresses", result.contains("configAddresses"));
        Assert.assertTrue("toString 应包含 lbPolicy", result.contains("lbPolicy"));
        Assert.assertTrue("toString 应包含 serverSwitchInterval", result.contains("serverSwitchInterval"));
    }

    // ==================== 综合场景测试 ====================

    /**
     * 测试 GOVERNANCE 类型：所有参数同时配置
     */
    @Test
    public void testGovernanceType_withAllParameters() {
        // Arrange
        System.setProperty(Consts.KEY_TTL, "15");
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_NAMESPACE, "custom-ns");
        parameters.put(Consts.KEY_TOKEN, "custom-token");
        parameters.put(Consts.CONFIG_PORT, "9093");
        parameters.put(Consts.KEY_OTHER_ADDRESSES, "10.0.0.2:8091");
        parameters.put(Consts.KEY_LB_POLICY, "ringHash");
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "120000");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.GOVERNANCE,
                "10.0.0.1", 8091, parameters);

        // Assert
        Assert.assertEquals("custom-ns", config.getNamespace());
        Assert.assertEquals("custom-token", config.getToken());
        Assert.assertEquals(15, config.getTtl());
        Assert.assertEquals("ringHash", config.getLbPolicy());
        Assert.assertEquals(Long.valueOf(120000L), config.getServerSwitchInterval());

        List<String> discoverAddresses = config.getDiscoverAddresses();
        Assert.assertTrue(discoverAddresses.contains("10.0.0.1:8091"));
        Assert.assertTrue(discoverAddresses.contains("10.0.0.2:8091"));

        List<String> configAddresses = config.getConfigAddresses();
        Assert.assertTrue(configAddresses.contains("10.0.0.1:9093"));

        LOG.info("[Test] GOVERNANCE 全参数测试通过: {}", config);
    }

    /**
     * 测试：不同 OperatorType 对 lbPolicy 和 serverSwitchInterval 的影响
     * 只有 GOVERNANCE 类型才会解析 lb_policy 和 server_switch_interval 参数
     */
    @Test
    public void testConfigType_lbPolicyAndSwitchInterval_notParsed() {
        // Arrange
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Consts.KEY_LB_POLICY, "weightedRandom");
        parameters.put(Consts.KEY_SERVER_SWITCH_INTERVAL, "300000");

        // Act
        PolarisConfig config = new PolarisConfig(PolarisOperators.OperatorType.CONFIG,
                "127.0.0.1", 8093, parameters);

        // Assert - CONFIG 类型不解析 lb_policy 和 server_switch_interval，应保持默认值
        Assert.assertEquals(LoadBalanceConfig.LOAD_BALANCE_ROUND_ROBIN, config.getLbPolicy());
        Assert.assertEquals(Long.valueOf(600000L), config.getServerSwitchInterval());
    }
}
