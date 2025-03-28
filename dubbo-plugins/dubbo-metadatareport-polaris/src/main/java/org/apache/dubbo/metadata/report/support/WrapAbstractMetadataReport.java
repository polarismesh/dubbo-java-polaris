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

package org.apache.dubbo.metadata.report.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigItem;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import java.util.List;

public class WrapAbstractMetadataReport {

    private final AbstractMetadataReport metadataReport;

    public WrapAbstractMetadataReport(AbstractMetadataReport metadataReport) {
        this.metadataReport = metadataReport;
    }

    public void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
        metadataReport.doStoreProviderMetadata(providerMetadataIdentifier, serviceDefinitions);
    }

    public void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
        metadataReport.doStoreConsumerMetadata(consumerMetadataIdentifier, serviceParameterString);
    }

    public void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        metadataReport.doSaveMetadata(metadataIdentifier, url);
    }

    public void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        metadataReport.doRemoveMetadata(metadataIdentifier);
    }

    public List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        return metadataReport.doGetExportedURLs(metadataIdentifier);
    }

    public void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urlListStr) {
        metadataReport.doSaveSubscriberData(subscriberMetadataIdentifier, urlListStr);
    }

    public String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        return metadataReport.doGetSubscribedURLs(subscriberMetadataIdentifier);
    }

    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        return metadataReport.getServiceDefinition(metadataIdentifier);
    }

    public ConfigItem getConfigItem(String key, String group) {
        return metadataReport.getConfigItem(key, group);
    }

    public AbstractMetadataReport getMetadataReport() {
        return metadataReport;
    }
}
