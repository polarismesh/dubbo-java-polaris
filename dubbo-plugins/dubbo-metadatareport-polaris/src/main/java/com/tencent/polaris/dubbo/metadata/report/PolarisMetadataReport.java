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

package com.tencent.polaris.dubbo.metadata.report;

import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.core.ProviderAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.plugin.server.InterfaceDescriptor;
import com.tencent.polaris.api.plugin.server.ReportServiceContractRequest;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.rpc.GetServiceContractRequest;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.api.utils.CollectionUtils;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.context.Context;
import com.tencent.polaris.common.registry.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.utils.Consts;
import com.tencent.polaris.specification.api.v1.service.manage.ServiceContractProto;
import java.util.stream.Collectors;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigItem;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReport;
import org.apache.dubbo.metadata.report.support.WrapAbstractMetadataReport;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.metadata.MetadataConstants.REPORT_CONSUMER_URL_KEY;

public class PolarisMetadataReport extends AbstractMetadataReport {

    private final PolarisOperator operator;

    private final PolarisConfig config;

    private final ProviderAPI providerAPI;

    private final ConsumerAPI consumerAPI;

    private final Map<String, ServiceContractProto.ServiceContract> mappingSubscribes = new ConcurrentHashMap<>();

    private final Map<String, Set<MappingListener>> mappingListeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService fetchMappingExecutor = Executors.newScheduledThreadPool(4, new NamedThreadFactory("polaris-metadata-report"));

    private Optional<WrapAbstractMetadataReport> another;

    PolarisMetadataReport(URL url) {
        super(url);
        this.operator = PolarisOperators.loadOrStoreForMetaReport(url.getHost(), url.getPort(), url.getParameters());
        this.config = operator.getPolarisConfig();
        this.providerAPI = operator.getProviderAPI();
        this.consumerAPI = operator.getConsumerAPI();
        this.another = MultiReportUtil.buildAnother(applicationModel, url);
    }

    // 保存应用的元数据
    @Override
    protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
        reportServiceContract(toDescriptor(providerMetadataIdentifier, serviceDefinitions));
        another.ifPresent(proxyReport -> proxyReport.doStoreProviderMetadata(providerMetadataIdentifier, serviceDefinitions));
    }

    @Override
    protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
        if (getUrl().getParameter(REPORT_CONSUMER_URL_KEY, false)) {
            reportServiceContract(toDescriptor(consumerMetadataIdentifier, serviceParameterString));
        }
        another.ifPresent(proxyReport -> proxyReport.doStoreConsumerMetadata(consumerMetadataIdentifier, serviceParameterString));
    }

    @Override
    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(formatMetadataIdentifier(metadataIdentifier));
        request.setService(metadataIdentifier.getApplication());
        request.setVersion(metadataIdentifier.getVersion());
        Optional<ServiceContractProto.ServiceContract> result = getServiceContract(request);
        if (!result.isPresent()) {
            return another.map(proxyReport -> proxyReport.getServiceDefinition(metadataIdentifier)).orElse(null);
        }

        List<ServiceContractProto.InterfaceDescriptor> descriptors = result.get().getInterfacesList();
        for (ServiceContractProto.InterfaceDescriptor descriptor : descriptors) {
            if (!Objects.equals(descriptor.getName(), metadataIdentifier.getIdentifierKey())) {
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
        request.setContent(metadataInfo.getContent());
        // TODO 需要设置 version
        request.setVersion(Context.getFromGlobal(Consts.INSTANCE_VERSION, Consts.DEFAULT_VERSION));
        List<InterfaceDescriptor> descriptors = new ArrayList<>(metadataInfo.getServices().size());
        metadataInfo.getServices().forEach((s, serviceInfo) -> {
            InterfaceDescriptor descriptor = new InterfaceDescriptor();
            descriptor.setPath(serviceInfo.getName());
            descriptor.setMethod("");
            descriptor.setName(serviceInfo.getMatchKey());
            descriptor.setContent(JsonUtils.toJson(serviceInfo));
            descriptors.add(descriptor);
        });
        request.setInterfaceDescriptors(descriptors);
        reportServiceContract(request);

        another.ifPresent(proxyReport -> {
            proxyReport.getMetadataReport().publishAppMetadata(identifier, metadataInfo);
        });
    }

    @Override
    public MetadataInfo getAppMetadata(SubscriberMetadataIdentifier identifier, Map<String, String> instanceMetadata) {
        // 这里由于查询的应用的接口定义数据，这里不能设置 version，必须显示设置 version 为空
        GetServiceContractRequest request = new GetServiceContractRequest();
        request.setName(formatAppMetaName(identifier));
        request.setService(identifier.getApplication());
        request.setVersion(Context.getFromGlobal(Consts.INSTANCE_VERSION, Consts.DEFAULT_VERSION));

        Optional<ServiceContractProto.ServiceContract> result = getServiceContract(request);
        if (!result.isPresent()) {
            // 降级，由兜底的 MetadataReport 进行处理
            return another.map(proxyReport -> proxyReport.getMetadataReport().getAppMetadata(identifier, instanceMetadata)).orElse(null);
        }

        Map<String, MetadataInfo.ServiceInfo> serviceInfos = new HashMap<>();
        for (ServiceContractProto.InterfaceDescriptor descriptor : result.get().getInterfacesList()) {
            MetadataInfo.ServiceInfo serviceInfo = JsonUtils.toJavaObject(descriptor.getContent(), MetadataInfo.ServiceInfo.class);
            serviceInfos.put(serviceInfo.getMatchKey(), serviceInfo);
        }
        if (CollectionUtils.isEmpty(serviceInfos)) {
            String warnMsg = String.format("Get app metadata empty, service name is %s, revision is %s, param is %s",
                    identifier.getApplication(), identifier.getRevision(), instanceMetadata);
            logger.warn(warnMsg);
            return null;
        }
        return new MetadataInfo(identifier.getApplication(), identifier.getRevision(), serviceInfos);
    }

    // toDescriptor 该方法是将 dubbo 接口运维元数据转为北极星的服务契约定义进行存储
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

    /**
     * 获取服务契约的通用调用接口
     *
     * @param req {@link GetServiceContractRequest}
     * @return {@link Optional<ServiceContractProto.ServiceContract>}
     */
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
                    req.toString(),
                    "get service_contract fail"
            );
        }
        return Optional.empty();
    }

    /**
     * 上报服务契约定义通用接口
     *
     * @param req {@link ReportServiceContractRequest}
     */
    private boolean reportServiceContract(ReportServiceContractRequest req) {
        req.setNamespace(config.getNamespace());
        req.setProtocol(Consts.DUBBO_PROTOCOL);
        try {
            providerAPI.reportServiceContract(req);
            return true;
        } catch (PolarisException e) {
            logger.error(
                    formatCode(e.getCode()),
                    e.getMessage(),
                    "",
                    "report service_contract fail"
            );
            return false;
        }
    }

    // ------- 和 Dubbo3 应用级注册发现有关的操作 --------
    // ------- 这里必须实现，否则就需要用户指定 providerBy ------

    /**
     * 存储 dubbo 的接口-应用的 mapping 数据时，这里对接的北极星的服务契约时，服务、版本信息为空，必须显示设置
     * InterfaceDescriptor -> 作为记录 dubbo 应用数据
     *
     * @param serviceKey  dubbo 接口名称
     * @param application dubbo 应用名称
     * @param url         {@link URL}
     * @return 返回接口-应用 mapping 数据是否发布成功
     */
    @Override
    public boolean registerServiceAppMapping(String serviceKey, String application, URL url) {
        // 1. 先获取初始mapping，过滤掉当前serviceKey对应的mapping
        another.ifPresent(proxyReport -> proxyReport.getMetadataReport().registerServiceAppMapping(serviceKey, application, url));
        GetServiceContractRequest getServiceContractRequest = new GetServiceContractRequest();
        getServiceContractRequest.setName(formatMappingName(serviceKey));
        getServiceContractRequest.setService("");
        getServiceContractRequest.setVersion("");
        Optional<ServiceContractProto.ServiceContract> result = getServiceContract(getServiceContractRequest);
        List<InterfaceDescriptor> descriptors = result
                .map(ServiceContractProto.ServiceContract::getInterfacesList)
                .orElse(Collections.emptyList())
                .stream()
                .filter(descriptor -> !descriptor.getName().equals(application))
                .map(descriptor -> {
                    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
                    interfaceDescriptor.setName(descriptor.getName());
                    interfaceDescriptor.setPath(descriptor.getPath());
                    interfaceDescriptor.setMethod(descriptor.getMethod());
                    interfaceDescriptor.setContent(descriptor.getContent());
                    return interfaceDescriptor;
                })
                .collect(Collectors.toList());
        // 2. 再添加当前serviceKey对应的mapping
        ReportServiceContractRequest request = new ReportServiceContractRequest();
        request.setName(formatMappingName(serviceKey));
        request.setService("");
        request.setVersion("");
        InterfaceDescriptor descriptor = new InterfaceDescriptor();
        descriptor.setName(application);
        descriptor.setPath(application);
        descriptor.setContent(application);
        descriptor.setMethod("");
        descriptors.add(descriptor);
        request.setInterfaceDescriptors(descriptors);
        return reportServiceContract(request);
    }

    @Override
    public void removeServiceAppMappingListener(String serviceKey, MappingListener listener) {
        Set<MappingListener> listeners = mappingListeners.getOrDefault(serviceKey, Collections.emptySet());
        listeners.remove(listener);

        another.ifPresent(proxyReport -> proxyReport.getMetadataReport().removeServiceAppMappingListener(serviceKey, listener));
    }

    private static Set<String> getAppNames(ServiceContractProto.ServiceContract contract) {
        Set<String> applications = new HashSet<>();
        for (ServiceContractProto.InterfaceDescriptor descriptor : contract.getInterfacesList()) {
            applications.add(descriptor.getPath());
        }
        return applications;
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
        Set<String> result = commonGetServiceAppMapping(serviceKey, url);
        if (CollectionUtils.isEmpty(result)) {
            return another.map(proxyReport -> proxyReport.getMetadataReport().getServiceAppMapping(serviceKey, url)).orElse(Collections.emptySet());
        }
        return result;
    }

    private Set<String> commonGetServiceAppMapping(String serviceKey, URL url) {
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
        return getAppNames(contract);
    }

    @Override
    public void destroy() {
        super.destroy();
        another.ifPresent(proxyReport -> proxyReport.getMetadataReport().destroy());
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
                request.setService("");
                request.setVersion("");

                Optional<ServiceContractProto.ServiceContract> result = report.getServiceContract(request);
                result.ifPresent(serviceContract -> {
                    ServiceContractProto.ServiceContract saveData = report.mappingSubscribes.get(serviceKey);
                    boolean needNotify = false;
                    // 如果之前就不存在这个 mapping 数据，需要触发通知
                    if (Objects.isNull(saveData)) {
                        report.mappingSubscribes.put(serviceKey, serviceContract);
                        needNotify = true;
                    }
                    if (Objects.nonNull(saveData)) {
                        // 如果 revision 信息比较不一致，则表明出现更细，需要触发通知
                        if (!Objects.equals(saveData.getRevision(), serviceContract.getRevision())) {
                            report.mappingSubscribes.put(serviceKey, serviceContract);
                            needNotify = true;
                        }
                    }
                    if (needNotify) {
                        Set<String> newApplications = getAppNames(serviceContract);
                        report.logger.info(String.format("receive mapping change event, interface=%s applications=%s", serviceKey, newApplications));
                        Set<MappingListener> listeners = report.mappingListeners.getOrDefault(serviceKey, Collections.emptySet());
                        MappingChangedEvent event = new MappingChangedEvent(serviceKey, newApplications);
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

    // -------- 仅用于 multi-metadata-report 情况下使用
    @Override
    public ConfigItem getConfigItem(String key, String group) {
        return another.map(proxyReport -> proxyReport.getConfigItem(key, group)).orElse(new ConfigItem());
    }

    @Override
    public boolean registerServiceAppMapping(String serviceInterface, String defaultMappingGroup, String newConfigContent, Object ticket) {
        return another.map(proxyReport -> proxyReport.getMetadataReport().
                registerServiceAppMapping(serviceInterface, defaultMappingGroup, newConfigContent, ticket)).orElse(false);
    }

    // -------- dubbo metadata-report 定义了接口，但是实际以及没有任何调用 ---------
    @Override
    protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        another.ifPresent(proxyReport -> {
            proxyReport.doSaveMetadata(metadataIdentifier, url);
        });
    }

    @Override
    protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        another.ifPresent(proxyReport -> proxyReport.doRemoveMetadata(metadataIdentifier));
    }

    @Override
    protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        if (another.isPresent()) {
            return another.get().doGetExportedURLs(metadataIdentifier);
        }
        return Collections.emptyList();
    }

    @Override
    protected void doSaveSubscriberData(SubscriberMetadataIdentifier identifier, String urlListStr) {
        another.ifPresent(proxyReport -> proxyReport.doSaveSubscriberData(identifier, urlListStr));
    }

    @Override
    protected String doGetSubscribedURLs(SubscriberMetadataIdentifier identifier) {
        return another.map(proxyReport -> proxyReport.doGetSubscribedURLs(identifier)).orElse(null);
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
        MappingListener multiWatch = new MappingListener() {

            @Override
            public void onEvent(MappingChangedEvent event) {
                Set<String> result = commonGetServiceAppMapping(serviceKey, url);
                another.ifPresent(proxyReport -> result.addAll(proxyReport.getMetadataReport().getServiceAppMapping(serviceKey, url)));
                event = new MappingChangedEvent(serviceKey, result);
                listener.onEvent(event);
            }

            @Override
            public void stop() {
                listener.stop();
            }
        };

        mappingListeners.computeIfAbsent(serviceKey, s -> new ConcurrentHashSet<>());
        mappingListeners.get(serviceKey).add(multiWatch);
        Set<String> result = commonGetServiceAppMapping(serviceKey, url);
        if (CollectionUtils.isEmpty(result)) {
            return another.map(proxyReport -> proxyReport.getMetadataReport().getServiceAppMapping(serviceKey, multiWatch, url))
                    .orElse(Collections.emptySet());
        }
        return result;
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
        if (StringUtils.isNotBlank(identifier.getVersion())) {
            tmpl += identifier.getVersion() + "::";
        }
        if (StringUtils.isNotBlank(identifier.getGroup())) {
            tmpl += identifier.getGroup() + "::";
        }
        tmpl += identifier.getSide() + "::";
        tmpl += identifier.getApplication();
        return tmpl;
    }
}
