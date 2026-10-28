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
import com.tencent.polaris.api.plugin.server.ReportServiceContractRequest;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.rpc.GetServiceContractRequest;
import com.tencent.polaris.api.rpc.ServiceRuleResponse;
import com.tencent.polaris.common.context.Context;
import com.tencent.polaris.common.config.PolarisConfig;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.specification.api.v1.service.manage.ServiceContractProto;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PolarisMetadataReportTest {

    private static final String SERVICE_KEY = "com.example.DemoService";

    private final URL url = URL.valueOf("polaris://127.0.0.1:8091?file-cache=false&cycle-report=false");

    private ConsumerAPI consumerAPI;
    private ProviderAPI providerAPI;
    private ScheduledExecutorService executor;
    private PolarisMetadataReport report;

    @Before
    public void setUp() throws Exception {
        consumerAPI = Mockito.mock(ConsumerAPI.class);
        providerAPI = Mockito.mock(ProviderAPI.class);
        executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.doReturn(Mockito.mock(ScheduledFuture.class)).when(executor)
                .scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(5L), Mockito.eq(5L), Mockito.eq(TimeUnit.SECONDS));

        PolarisOperator operator = Mockito.mock(PolarisOperator.class);
        PolarisConfig config = Mockito.mock(PolarisConfig.class);
        Mockito.when(operator.getPolarisConfig()).thenReturn(config);
        Mockito.when(operator.getConsumerAPI()).thenReturn(consumerAPI);
        Mockito.when(operator.getProviderAPI()).thenReturn(providerAPI);
        Mockito.when(config.getNamespace()).thenReturn("default");

        try (MockedStatic<PolarisOperators> operators = Mockito.mockStatic(PolarisOperators.class);
             MockedStatic<MultiReportUtil> multiReport = Mockito.mockStatic(MultiReportUtil.class)) {
            operators.when(() -> PolarisOperators.loadOrStoreForMetaReport(
                    Mockito.anyString(), Mockito.anyInt(), Mockito.anyMap())).thenReturn(operator);
            multiReport.when(() -> MultiReportUtil.buildAnother(Mockito.any(), Mockito.any(URL.class)))
                    .thenReturn(Optional.empty());
            report = new PolarisMetadataReport(url);
        }

        // Drive polling explicitly so the tests do not depend on timer delays.
        Field field = PolarisMetadataReport.class.getDeclaredField("fetchMappingExecutor");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(report)).shutdownNow();
        field.set(report, executor);
    }

    @After
    public void tearDown() {
        if (report != null) {
            report.destroy();
        }
    }

    @Test
    public void publishAppMetadataUsesEmptyContractVersionAndPreservesInterfaceVersion() {
        MetadataInfo metadata = appMetadata("revision-1");
        SubscriberMetadataIdentifier identifier = new SubscriberMetadataIdentifier("demo-provider", "revision-1");

        report.publishAppMetadata(identifier, metadata);

        ArgumentCaptor<ReportServiceContractRequest> request = ArgumentCaptor.forClass(ReportServiceContractRequest.class);
        Mockito.verify(providerAPI).reportServiceContract(request.capture());
        ReportServiceContractRequest captured = request.getValue();
        Assert.assertEquals("", captured.getVersion());
        Assert.assertEquals("default", captured.getNamespace());
        Assert.assertEquals("demo-provider", captured.getService());
        Assert.assertEquals("dubbo", captured.getProtocol());
        Assert.assertEquals("dubbo::metadata::demo-provider::revision-1", captured.getName());
        Assert.assertEquals(metadata.getContent(), captured.getContent());
        Assert.assertEquals(1, captured.getInterfaceDescriptors().size());
        MetadataInfo.ServiceInfo service = JsonUtils.toJavaObject(
                captured.getInterfaceDescriptors().get(0).getContent(), MetadataInfo.ServiceInfo.class);
        Assert.assertEquals(SERVICE_KEY, service.getName());
        Assert.assertEquals("interface-v2", service.getVersion());
    }

    @Test
    public void appMetadataRevisionsRemainDistinctRegardlessOfLocalVersion() {
        try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
            context.when(() -> Context.getFromGlobal(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn("deployment-v3");
            report.publishAppMetadata(new SubscriberMetadataIdentifier("demo-provider", "revision-1"),
                    appMetadata("revision-1"));
            report.publishAppMetadata(new SubscriberMetadataIdentifier("demo-provider", "revision-2"),
                    appMetadata("revision-2"));

            ArgumentCaptor<ReportServiceContractRequest> requests = ArgumentCaptor.forClass(ReportServiceContractRequest.class);
            Mockito.verify(providerAPI, Mockito.times(2)).reportServiceContract(requests.capture());
            Assert.assertEquals("dubbo::metadata::demo-provider::revision-1", requests.getAllValues().get(0).getName());
            Assert.assertEquals("dubbo::metadata::demo-provider::revision-2", requests.getAllValues().get(1).getName());
            requests.getAllValues().forEach(request -> Assert.assertEquals("", request.getVersion()));
        }
    }

    @Test
    public void getAppMetadataUsesEmptyContractVersionRegardlessOfConsumerAndProviderVersions() {
        MetadataInfo metadata = appMetadata("revision-1");
        MetadataInfo.ServiceInfo service = metadata.getServices().values().iterator().next();
        ServiceContractProto.ServiceContract contract = ServiceContractProto.ServiceContract.newBuilder()
                .addInterfaces(ServiceContractProto.InterfaceDescriptor.newBuilder()
                        .setPath(SERVICE_KEY).setContent(JsonUtils.toJson(service)))
                .build();
        ServiceRuleResponse response = responseWithContract(contract);
        Mockito.when(consumerAPI.getServiceContract(Mockito.any(GetServiceContractRequest.class)))
                .thenReturn(response);

        try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
            context.when(() -> Context.getFromGlobal(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn("consumer-v4");
            SubscriberMetadataIdentifier identifier = new SubscriberMetadataIdentifier("demo-provider", "revision-1");
            for (String providerVersion : new String[]{"", "provider-v2"}) {
                MetadataInfo fetched = report.getAppMetadata(identifier, Collections.singletonMap("version", providerVersion));
                Assert.assertNotNull(fetched);
                Assert.assertEquals("demo-provider", fetched.getApp());
                Assert.assertEquals("revision-1", fetched.getRevision());
                Assert.assertEquals("interface-v2", fetched.getServiceInfo(service.getMatchKey()).getVersion());
            }

            ArgumentCaptor<GetServiceContractRequest> requests = ArgumentCaptor.forClass(GetServiceContractRequest.class);
            Mockito.verify(consumerAPI, Mockito.times(2)).getServiceContract(requests.capture());
            requests.getAllValues().forEach(request -> {
                Assert.assertEquals("", request.getVersion());
                Assert.assertEquals("default", request.getNamespace());
                Assert.assertEquals("demo-provider", request.getService());
                Assert.assertEquals("dubbo", request.getProtocol());
                Assert.assertEquals("dubbo::metadata::demo-provider::revision-1", request.getName());
            });
        }
    }

    @Test
    public void emptyMappingKeepsPollingAndNotifiesWhenProviderAppears() {
        ServiceRuleResponse empty = Mockito.mock(ServiceRuleResponse.class);
        ServiceRuleResponse available = responseWithMapping("", "demo-provider");
        Mockito.when(consumerAPI.getServiceContract(Mockito.any(GetServiceContractRequest.class)))
                .thenReturn(empty, empty, available, available);
        MappingListener listener = Mockito.mock(MappingListener.class);

        Assert.assertTrue(report.getServiceAppMapping(SERVICE_KEY, listener, url).isEmpty());
        Assert.assertTrue(report.getServiceAppMapping(SERVICE_KEY, url).isEmpty());
        Runnable poll = pollingTask();
        poll.run();
        Mockito.verify(listener, Mockito.never()).onEvent(Mockito.any(MappingChangedEvent.class));

        poll.run();
        ArgumentCaptor<MappingChangedEvent> event = ArgumentCaptor.forClass(MappingChangedEvent.class);
        Mockito.verify(listener).onEvent(event.capture());
        Assert.assertEquals(SERVICE_KEY, event.getValue().getServiceKey());
        Assert.assertEquals(Collections.singleton("demo-provider"), event.getValue().getApps());
        Assert.assertEquals(Collections.singleton("demo-provider"), report.getServiceAppMapping(SERVICE_KEY, url));

        poll.run();
        Mockito.verify(listener, Mockito.times(1)).onEvent(Mockito.any(MappingChangedEvent.class));
        Mockito.verify(consumerAPI, Mockito.times(4)).getServiceContract(Mockito.any(GetServiceContractRequest.class));
        pollingTask();
    }

    @Test
    public void missingContractKeepsPolling() {
        ServiceRuleResponse empty = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(empty.getServiceRule()).thenReturn(Mockito.mock(ServiceRule.class));
        ServiceRuleResponse available = responseWithMapping("revision-1", "demo-provider");
        Mockito.when(consumerAPI.getServiceContract(Mockito.any(GetServiceContractRequest.class)))
                .thenReturn(empty, available);

        Assert.assertTrue(report.getServiceAppMapping(SERVICE_KEY, url).isEmpty());
        pollingTask().run();
        Assert.assertEquals(Collections.singleton("demo-provider"), report.getServiceAppMapping(SERVICE_KEY, url));
    }

    @Test
    public void existingMappingIsReturnedImmediatelyAndRevisionChangesNotify() {
        ServiceRuleResponse initial = responseWithMapping("revision-1", "first-provider");
        ServiceRuleResponse updated = responseWithMapping("revision-2", "second-provider");
        Mockito.when(consumerAPI.getServiceContract(Mockito.any(GetServiceContractRequest.class)))
                .thenReturn(initial, initial, updated);
        MappingListener listener = Mockito.mock(MappingListener.class);

        Assert.assertEquals(Collections.singleton("first-provider"), report.getServiceAppMapping(SERVICE_KEY, listener, url));
        Runnable poll = pollingTask();
        poll.run();
        Mockito.verify(listener, Mockito.never()).onEvent(Mockito.any(MappingChangedEvent.class));
        poll.run();

        ArgumentCaptor<MappingChangedEvent> event = ArgumentCaptor.forClass(MappingChangedEvent.class);
        Mockito.verify(listener).onEvent(event.capture());
        Assert.assertEquals(Collections.singleton("second-provider"), event.getValue().getApps());
    }

    @Test
    public void concurrentQueriesShareOnePollingTaskAndInitialResult() throws Exception {
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch allowFetch = new CountDownLatch(1);
        CountDownLatch secondQueryStarted = new CountDownLatch(1);
        ServiceRuleResponse available = responseWithMapping("revision-1", "demo-provider");
        Mockito.when(consumerAPI.getServiceContract(Mockito.any(GetServiceContractRequest.class))).thenAnswer(invocation -> {
            fetchStarted.countDown();
            Assert.assertTrue(allowFetch.await(5, TimeUnit.SECONDS));
            return available;
        });
        ExecutorService queries = Executors.newFixedThreadPool(2);
        try {
            Future<Set<String>> first = queries.submit(() -> report.getServiceAppMapping(SERVICE_KEY, url));
            Assert.assertTrue(fetchStarted.await(5, TimeUnit.SECONDS));
            Future<Set<String>> second = queries.submit(() -> {
                secondQueryStarted.countDown();
                return report.getServiceAppMapping(SERVICE_KEY, url);
            });
            Assert.assertTrue(secondQueryStarted.await(5, TimeUnit.SECONDS));
            allowFetch.countDown();

            Assert.assertEquals(Collections.singleton("demo-provider"), first.get(5, TimeUnit.SECONDS));
            Assert.assertEquals(Collections.singleton("demo-provider"), second.get(5, TimeUnit.SECONDS));
            Mockito.verify(consumerAPI).getServiceContract(Mockito.any(GetServiceContractRequest.class));
            pollingTask();
        } finally {
            allowFetch.countDown();
            queries.shutdownNow();
            Assert.assertTrue(queries.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Runnable pollingTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executor).scheduleAtFixedRate(task.capture(), Mockito.eq(5L), Mockito.eq(5L), Mockito.eq(TimeUnit.SECONDS));
        return task.getValue();
    }

    private ServiceRuleResponse responseWithMapping(String revision, String application) {
        ServiceContractProto.ServiceContract contract = ServiceContractProto.ServiceContract.newBuilder()
                .setRevision(revision)
                .addInterfaces(ServiceContractProto.InterfaceDescriptor.newBuilder().setPath(application))
                .build();
        return responseWithContract(contract);
    }

    private MetadataInfo appMetadata(String revision) {
        MetadataInfo.ServiceInfo service = new MetadataInfo.ServiceInfo(
                SERVICE_KEY, "demo-group", "interface-v2", "dubbo", 20880, SERVICE_KEY, Collections.emptyMap());
        return new MetadataInfo("demo-provider", revision, Collections.singletonMap(service.getMatchKey(), service));
    }

    private ServiceRuleResponse responseWithContract(ServiceContractProto.ServiceContract contract) {
        ServiceRule rule = Mockito.mock(ServiceRule.class);
        Mockito.when(rule.getRule()).thenReturn(contract);
        ServiceRuleResponse response = Mockito.mock(ServiceRuleResponse.class);
        Mockito.when(response.getServiceRule()).thenReturn(rule);
        return response;
    }
}
