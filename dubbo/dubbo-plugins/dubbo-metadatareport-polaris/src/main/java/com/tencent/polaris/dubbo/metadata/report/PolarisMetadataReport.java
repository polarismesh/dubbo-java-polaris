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
import com.tencent.polaris.api.plugin.server.InterfaceDescriptor;
import com.tencent.polaris.api.plugin.server.ReportServiceContractRequest;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.rpc.GetServiceContractRequest;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.specification.api.v1.service.manage.ServiceContractProto;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.metadata.MetadataConstants.REPORT_CONSUMER_URL_KEY;

public class PolarisMetadataReport extends AbstractMetadataReport {

    protected final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final PolarisOperator operator;

    private final PolarisConfig config;

    private final ProviderAPI providerAPI;

    private final ConsumerAPI consumerAPI;

    private final Map<String, ServiceContractProto.ServiceContract> mappingSubscribes = new ConcurrentHashMap<>();

    private final Map<String, Set<MappingListener>> mappingListeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService fetchMappingExecutor = Executors.newScheduledThreadPool(4, new NamedThreadFactory("polaris-metadata-report"));

    private final String polarisToken;

    PolarisMetadataReport(URL url) {
        super(url);
        this.polarisToken = url.getParameter(Consts.KEY_TOKEN);
        this.operator = PolarisOperators.loadOrStoreForMetaReport(url.getHost(), url.getPort(), url.getParameters());
        this.config = operator.getPolarisConfig();
        this.providerAPI = operator.getProviderAPI();
        this.consumerAPI = operator.getConsumerAPI();
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
    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(formatMetadataIdentifier(metadataIdentifier));
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
        request.setName(formatAppMetaName(identifier));
        request.setService(identifier.getApplication());
        request.setRevision(identifier.getRevision());
        request.setContent(metadataInfo.getContent());
        List<InterfaceDescriptor> descriptors = new ArrayList<>(metadataInfo.getServices().size());
        metadataInfo.getServices().forEach((s, serviceInfo) -> {
            InterfaceDescriptor descriptor = new InterfaceDescriptor();
            descriptor.setId(s);
            descriptor.setPath(serviceInfo.getPath());
            descriptor.setMethod("");
            descriptor.setName(serviceInfo.getName());
            descriptor.setContent(JsonUtils.toJson(serviceInfo));
            descriptors.add(descriptor);
        });
        request.setInterfaceDescriptors(descriptors);
        reportServiceContract(request);
    }

    @Override
    public MetadataInfo getAppMetadata(SubscriberMetadataIdentifier identifier, Map<String, String> instanceMetadata) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(formatAppMetaName(identifier));
        request.setService(identifier.getApplication());
        request.setVersion("");

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

    private ReportServiceContractRequest toDescriptor(MetadataIdentifier identifier, String serviceDefinitions) {
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(formatMetadataIdentifier(identifier));
        request.setService(identifier.getApplication());
        request.setVersion(identifier.getVersion());
        request.setContent("");

        InterfaceDescriptor descriptor = new InterfaceDescriptor();
        descriptor.setName(identifier.getIdentifierKey());
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
        req.setToken(polarisToken);
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
        req.setToken(polarisToken);
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
    public boolean registerServiceAppMapping(String serviceKey, String application, URL url) {
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(formatMappingName(serviceKey));
        request.setService("");
        request.setVersion("");

        List<InterfaceDescriptor> descriptors = new ArrayList<>();
        InterfaceDescriptor descriptor = new InterfaceDescriptor();
        descriptor.setName(application);
        descriptor.setPath(application);
        descriptor.setContent(application);
        descriptor.setMethod("");
        descriptors.add(descriptor);

        request.setInterfaceDescriptors(descriptors);
        reportServiceContract(request);
        return true;
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
        mappingListeners.computeIfAbsent(serviceKey, s -> new ConcurrentHashSet<>());
        mappingListeners.get(serviceKey).add(listener);
        return getServiceAppMapping(serviceKey, url);
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
        if (!mappingSubscribes.containsKey(serviceKey)) {
            GetServiceContractRequest request = new GetServiceContractRequest();
            request.setName(formatMappingName(serviceKey));
            request.setService("");
            request.setVersion("");

            Optional<ServiceContractProto.ServiceContract> result = getServiceContract(request);
            if (!result.isPresent()) {
                return Collections.emptySet();
            }
            // 添加一个定时任务获取最新的 mapping 数据信息
            mappingSubscribes.computeIfAbsent(serviceKey, s -> {
                fetchMappingExecutor.scheduleAtFixedRate(new FetchMapping(serviceKey, this), 5, 5, TimeUnit.SECONDS);
                return result.get();
            });
        }
        ServiceContractProto.ServiceContract contract = mappingSubscribes.get(serviceKey);
        return  getAppNames(contract);
    }

    @Override
    public void destroy() {
        super.destroy();
        fetchMappingExecutor.shutdown();
    }

    private String formatCode(Object val) {
        return "POLARIS:" + val;
    }

    private static class FetchMapping implements Runnable {

        private final String serviceKey;

        private final PolarisMetadataReport report;

        private FetchMapping(String serviceKey, PolarisMetadataReport report) {
            this.serviceKey = serviceKey;
            this.report = report;
        }

        @Override
        public void run() {
            try {
                GetServiceContractRequest request = new GetServiceContractRequest();
                request.setName(formatMappingName(serviceKey));

                Optional<ServiceContractProto.ServiceContract> result = report.getServiceContract(request);
                result.ifPresent(serviceContract -> {
                    ServiceContractProto.ServiceContract saveData = report.mappingSubscribes.get(serviceKey);
                    boolean needNotify = false;
                    if (Objects.isNull(saveData)) {
                        report.mappingSubscribes.put(serviceKey, serviceContract);
                        needNotify = true;
                    }
                    if (Objects.nonNull(saveData)) {
                        if (!Objects.equals(saveData.getRevision(), serviceContract.getRevision())) {
                            report.mappingSubscribes.put(serviceKey, serviceContract);
                            needNotify = true;
                        }
                    }
                    if (needNotify) {
                        Set<MappingListener> listeners = report.mappingListeners.getOrDefault(serviceKey, Collections.emptySet());
                        MappingChangedEvent event = new MappingChangedEvent(serviceKey, getAppNames(serviceContract));
                        listeners.forEach(mappingListener -> mappingListener.onEvent(event));
                    }
                });
            } catch (Throwable e) {
                report.logger.error(
                        "-1",
                        e.getMessage(),
                        serviceKey,
                        "fetch dubbo mapping fail",
                        e
                );
            }
        }
    }

    // -------- dubbo metadata-report 定义了接口，但是实际以及没有任何调用 ---------
    @Override
    protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
    }

    @Override
    protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
    }

    @Override
    protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        return Collections.emptyList();
    }

    @Override
    protected void doSaveSubscriberData(SubscriberMetadataIdentifier identifier, String urlListStr) {
    }

    @Override
    protected String doGetSubscribedURLs(SubscriberMetadataIdentifier identifier) {
        return null;
    }

    private static Set<String> getAppNames(ServiceContractProto.ServiceContract contract) {
        Set<String> applications = new HashSet<>();
        for (ServiceContractProto.InterfaceDescriptor descriptor : contract.getInterfacesList()) {
            applications.add(descriptor.getName());
        }
        return applications;
    }

    private static String formatMappingName(String key) {
        String tmpl = "dubbo::mapping::%s";
        return String.format(tmpl, key);
    }

    private static String formatAppMetaName(SubscriberMetadataIdentifier identifier) {
        String tmpl = "dubbo::metadata::%s::%s";
        return String.format(tmpl, identifier.getApplication(), identifier.getRevision());
    }

    private static String formatMetadataIdentifier(MetadataIdentifier identifier) {
        String tmpl = "dubbo::metadata::";
        if (StringUtils.isNotEmpty(identifier.getVersion())) {
            tmpl += identifier.getVersion() + "::";
        }
        if (StringUtils.isNotEmpty(identifier.getGroup())) {
            tmpl += identifier.getGroup() + "::";
        }
        tmpl += identifier.getSide() + "::";
        tmpl += identifier.getApplication();
        return tmpl;
    }
}
