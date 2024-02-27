/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.polaris.dubbo.metadata.report;

import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.exception.ServerCodes;
import com.tencent.polaris.api.plugin.configuration.ConfigFileResponse;
import com.tencent.polaris.api.plugin.server.InterfaceDescriptor;
import com.tencent.polaris.api.plugin.server.ReportServiceContractRequest;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.rpc.GetServiceContractRequest;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.configuration.api.core.ConfigFile;
import com.tencent.polaris.configuration.api.core.ConfigFileChangeEvent;
import com.tencent.polaris.configuration.api.core.ConfigFileChangeListener;
import com.tencent.polaris.configuration.api.core.ConfigFilePublishService;
import com.tencent.polaris.configuration.api.core.ConfigFileService;
import com.tencent.polaris.configuration.api.rpc.ConfigPublishRequest;
import com.tencent.polaris.specification.api.v1.service.manage.ServiceContractProto;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigItem;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static org.apache.dubbo.metadata.MetadataConstants.REPORT_CONSUMER_URL_KEY;
import static org.apache.dubbo.metadata.ServiceNameMapping.DEFAULT_MAPPING_GROUP;

public class PolarisMetadataReport extends AbstractMetadataReport {

    protected final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final PolarisOperator operator;

    private final PolarisConfig config;

    private final ProviderAPI providerAPI;

    private final ConsumerAPI consumerAPI;

    private final ConfigFileService fileQuerier;

    private final ConfigFilePublishService filePubilsher;

    private final Map<String, Set<MappingListener>> mappingListeners = new ConcurrentHashMap<>();

    private final Map<String, ConfigFileChangeListener> sourceMappingListeners = new ConcurrentHashMap<>();

    PolarisMetadataReport(URL url) {
        super(url);
        this.operator = PolarisOperators.loadOrStoreForMetadataReport(url.getHost(), url.getPort(), url.getParameters());
        this.config = operator.getPolarisConfig();
        this.providerAPI = operator.getProviderAPI();
        this.consumerAPI = operator.getConsumerAPI();
        this.fileQuerier = operator.getConfigFileAPI();
        this.filePubilsher = operator.getConfigFilePublishAPI();
    }

    @Override
    protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
        reportServiceContract(toDescriptor(providerMetadataIdentifier, serviceDefinitions));
    }

    @Override
    protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
        if (getUrl().getParameter(REPORT_CONSUMER_URL_KEY, false)) {
            reportServiceContract(toDescriptor(consumerMetadataIdentifier, serviceParameterString));
        }
    }

    @Override
    protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        reportServiceContract(toDescriptor(metadataIdentifier, url));
    }

    @Override
    protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        //
    }

    @Override
    protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        ServiceRuleResponse response = consumerAPI.getServiceContract(request);
        ServiceRule rule = response.getServiceRule();
        if (Objects.isNull(rule)) {
            return Collections.emptyList();
        }
        ServiceContractProto.ServiceContract contract = (ServiceContractProto.ServiceContract) rule.getRule();
        List<ServiceContractProto.InterfaceDescriptor> descriptors = contract.getInterfacesList();
        for (ServiceContractProto.InterfaceDescriptor descriptor : descriptors) {
            if (!Objects.equals(descriptor.getId(), metadataIdentifier.getIdentifierKey())) {
                continue;
            }
            String content = descriptor.getContent();
            return new ArrayList<>(Collections.singletonList(URL.decode(content)));
        }
        return Collections.emptyList();
    }

    @Override
    protected void doSaveSubscriberData(SubscriberMetadataIdentifier identifier, String urlListStr) {
    }

    @Override
    protected String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        return null;
    }

    @Override
    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(metadataIdentifier.getSide() + ":" + metadataIdentifier.getApplication());
        request.setService(metadataIdentifier.getApplication());
        request.setVersion(metadataIdentifier.getVersion());
        Optional<ServiceContractProto.ServiceContract> result = getServiceContract(request);
        if (!result.isPresent()) {
            return null;
        }

        List<ServiceContractProto.InterfaceDescriptor> descriptors = result.get().getInterfacesList();
        for (ServiceContractProto.InterfaceDescriptor descriptor : descriptors) {
            if (!Objects.equals(descriptor.getId(), metadataIdentifier.getIdentifierKey())) {
                continue;
            }
            return descriptor.getContent();
        }
        return null;
    }

    @Override
    public void publishAppMetadata(SubscriberMetadataIdentifier identifier, MetadataInfo metadataInfo) {
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(identifier.getApplication());
        request.setService(identifier.getApplication());
        request.setVersion(identifier.getRevision());
        request.setContent(metadataInfo.getContent());
        List<InterfaceDescriptor> descriptors = new ArrayList<>(metadataInfo.getServices().size());
        metadataInfo.getServices().forEach(new BiConsumer<String, MetadataInfo.ServiceInfo>() {
            @Override
            public void accept(String s, MetadataInfo.ServiceInfo serviceInfo) {
                InterfaceDescriptor descriptor = new InterfaceDescriptor();
                descriptor.setId(s);
                descriptor.setPath(serviceInfo.getPath());
                descriptor.setMethod("");
                descriptor.setName(serviceInfo.getName());
                descriptor.setContent(JsonUtils.toJson(serviceInfo));
                descriptors.add(descriptor);
            }
        });
        request.setInterfaceDescriptors(descriptors);
        reportServiceContract(request);
    }

    @Override
    public MetadataInfo getAppMetadata(SubscriberMetadataIdentifier identifier, Map<String, String> instanceMetadata) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(identifier.getApplication());
        request.setService(identifier.getApplication());
        request.setVersion(identifier.getRevision());

        Optional<ServiceContractProto.ServiceContract> result = getServiceContract(request);
        if (!result.isPresent()) {
            return new MetadataInfo();
        }

        Map<String, MetadataInfo.ServiceInfo> serviceInfos = new HashMap<>();
        for (ServiceContractProto.InterfaceDescriptor descriptor : result.get().getInterfacesList()) {
            MetadataInfo.ServiceInfo serviceInfo = JsonUtils.toJavaObject(descriptor.getContent(), MetadataInfo.ServiceInfo.class);
            serviceInfos.put(serviceInfo.getMatchKey(), serviceInfo);
        }
        return new MetadataInfo(identifier.getApplication(), identifier.getRevision(), serviceInfos);
    }

    private ReportServiceContractRequest toDescriptor(ServiceMetadataIdentifier identifier, URL url) {
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(url.getSide() + ":" + url.getApplication());
        request.setService(url.getApplication());
        request.setVersion(url.getVersion());

        InterfaceDescriptor descriptor = new InterfaceDescriptor();
        descriptor.setId(identifier.getIdentifierKey());
        descriptor.setName(String.format("%s:%s", url.getSide(), url.getGroup()));
        descriptor.setPath(url.getServiceInterface());
        descriptor.setMethod("");
        descriptor.setContent(URL.encode(url.toFullString()));

        List<InterfaceDescriptor> descriptors = new ArrayList<>(1);
        descriptors.add(descriptor);
        request.setInterfaceDescriptors(descriptors);

        return request;
    }

    private ReportServiceContractRequest toDescriptor(MetadataIdentifier identifier, String serviceDefinitions) {
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(identifier.getSide() + ":" + identifier.getApplication());
        request.setService(identifier.getApplication());
        request.setVersion(identifier.getVersion());

        InterfaceDescriptor descriptor = new InterfaceDescriptor();
        descriptor.setId(identifier.getIdentifierKey());
        descriptor.setName(String.format("%s:%s", identifier.getSide(), identifier.getGroup()));
        descriptor.setPath(identifier.getServiceInterface());
        descriptor.setMethod("");
        descriptor.setContent(serviceDefinitions);

        List<InterfaceDescriptor> descriptors = new ArrayList<>(1);
        descriptors.add(descriptor);
        request.setInterfaceDescriptors(descriptors);

        return request;
    }

    private Optional<ServiceContractProto.ServiceContract> getServiceContract(GetServiceContractRequest req) {
        req.setNamespace(config.getNamespace());
        req.setProtocol(Consts.DUBBO_PROTOCOL);
        try {
            ServiceRuleResponse response = consumerAPI.getServiceContract(req);
            ServiceRule rule = response.getServiceRule();
            if (Objects.isNull(rule)) {
                return Optional.empty();
            }
            ServiceContractProto.ServiceContract contract = (ServiceContractProto.ServiceContract) rule.getRule();
            return Optional.ofNullable(contract);
        } catch (PolarisException e) {
            logger.error(
                    formatCode(e.getCode()),
                    e.getMessage(),
                    "",
                    "report service_contract fail"
            );
        }
        return Optional.empty();
    }


    private void reportServiceContract(ReportServiceContractRequest req) {
        req.setNamespace(config.getNamespace());
        req.setProtocol(Consts.DUBBO_PROTOCOL);
        try {
            providerAPI.reportServiceContract(req);
        } catch (PolarisException e) {
            logger.error(
                    formatCode(e.getCode()),
                    e.getMessage(),
                    "",
                    "report service_contract fail"
            );
        }
    }

    // ------- 和 Dubbo3 应用级注册发现有关的操作 --------
    // ------- 这里必须实现，否则就需要用户指定 providerBy ------

    @Override
    public ConfigItem getConfigItem(String key, String group) {
        ConfigFile file = fileQuerier.getConfigFile(config.getNamespace(), group, key);
        String content = file.getContent();
        return new ConfigItem(content, file.getMd5());
    }

    @Override
    public boolean registerServiceAppMapping(String serviceInterface, String defaultMappingGroup, String newConfigContent, Object ticket) {
        ConfigPublishRequest request = new ConfigPublishRequest();
        request.setNamespace(config.getNamespace());
        request.setGroup(defaultMappingGroup);
        request.setFilename(serviceInterface);
        request.setContent(newConfigContent);
        request.setCasMd5((String) ticket);
        ConfigFileResponse response = filePubilsher.upsertAndPublish(request);
        if (response.getCode() == ServerCodes.EXECUTE_SUCCESS) {
            return true;
        }
        logger.error(
                formatCode(response.getCode()),
                response.getMessage(),
                String.format("key(%s) group(%s) md5(%s)", serviceInterface, defaultMappingGroup, ticket),
                "registerServiceAppMapping fail"
        );
        return false;
    }

    @Override
    public void removeServiceAppMappingListener(String serviceKey, MappingListener listener) {
        Set<MappingListener> listeners = mappingListeners.getOrDefault(serviceKey, Collections.emptySet());
        listeners.remove(listener);
    }

    /**
     * dubbo 根据 serviceKey（接口名称）查找提供该接口的 application 到底有哪些
     *
     * @param serviceKey dubbo 的 interface name
     * @param listener   {@link MappingListener}
     * @param url        {@link URL}
     * @return {@link Set<String>} 提供此接口的所用应用列表名称
     */
    @Override
    public Set<String> getServiceAppMapping(String serviceKey, MappingListener listener, URL url) {
        String group = DEFAULT_MAPPING_GROUP;
        ConfigFile file = fileQuerier.getConfigFile(config.getNamespace(), group, serviceKey);

        mappingListeners.compute(serviceKey, (s, mappingListeners) -> {
            sourceMappingListeners.compute(serviceKey, (s1, configFileChangeListener) -> {
                ConfigFileChangeListener changeListener = new MappingConfigFileChangeListener(serviceKey, this);
                file.addChangeListener(changeListener);
                return changeListener;
            });
            return new ConcurrentHashSet<>();
        });

        mappingListeners.get(serviceKey).add(listener);

        return ServiceNameMapping.getAppNames(file.getContent());
    }

    /**
     * dubbo 根据 serviceKey（接口名称）查找提供该接口的 application 到底有哪些
     *
     * @param serviceKey dubbo 的 interface name
     * @param url        {@link URL}
     * @return {@link Set<String>} 提供此接口的所用应用列表名称
     */
    @Override
    public Set<String> getServiceAppMapping(String serviceKey, URL url) {
        String group = DEFAULT_MAPPING_GROUP;
        ConfigFile file = fileQuerier.getConfigFile(config.getNamespace(), group, serviceKey);
        return ServiceNameMapping.getAppNames(file.getContent());
    }

    private String formatCode(Object val) {
        return "POLARIS:" + val;
    }

    private static class MappingConfigFileChangeListener implements ConfigFileChangeListener {

        private final String serviceKey;

        private final PolarisMetadataReport report;

        public MappingConfigFileChangeListener(String serviceKey, PolarisMetadataReport report) {
            this.serviceKey = serviceKey;
            this.report = report;
        }

        @Override
        public void onChange(ConfigFileChangeEvent event) {
            MappingChangedEvent mappingChangedEvent = new MappingChangedEvent(serviceKey, ServiceNameMapping.getAppNames(event.getNewValue()));

            Set<MappingListener> mappingListeners = report.mappingListeners.getOrDefault(serviceKey, Collections.emptySet());
            mappingListeners.forEach(mappingListener -> mappingListener.onEvent(mappingChangedEvent));
        }
    }

}
