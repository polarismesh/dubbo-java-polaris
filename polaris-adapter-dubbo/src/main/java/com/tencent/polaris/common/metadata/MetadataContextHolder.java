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

package com.tencent.polaris.common.metadata;

import java.util.Map;

import com.tencent.polaris.api.utils.CollectionUtils;
import com.tencent.polaris.metadata.core.MetadataContainer;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-local holder for {@link MetadataContext}.
 * Delegates to polaris-java's MetadataContextHolder for thread-local management.
 */
public final class MetadataContextHolder {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataContextHolder.class);

    static {
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.setInitializer(MetadataContextHolder::createMetadataContext);
    }

    private MetadataContextHolder() {
    }

    /**
     * Get current thread's MetadataContext (create if absent).
     */
    public static MetadataContext get() {
        return (MetadataContext) com.tencent.polaris.metadata.core.manager.MetadataContextHolder.getOrCreate();
    }

    /**
     * Set metadata context for current thread.
     */
    public static void set(MetadataContext metadataContext) {
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.set(metadataContext);
    }

    /**
     * Initialize the metadata context with upstream dynamic metadata.
     * Called by the provider-side filter when receiving a request.
     *
     * @param dynamicTransitiveMetadata transitive metadata from upstream
     * @param dynamicDisposableMetadata disposable metadata from upstream
     * @param dynamicApplicationMetadata application metadata from upstream
     */
    public static void init(Map<String, String> dynamicTransitiveMetadata,
                            Map<String, String> dynamicDisposableMetadata,
                            Map<String, String> dynamicApplicationMetadata) {
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.refresh(metadataManager -> {
            // caller transitive metadata -> callee custom transitive metadata
            MetadataContainer calleeCustomContainer = metadataManager.getMetadataContainer(MetadataType.CUSTOM, false);
            if (CollectionUtils.isNotEmpty(dynamicTransitiveMetadata)) {
                for (Map.Entry<String, String> entry : dynamicTransitiveMetadata.entrySet()) {
                    calleeCustomContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.PASS_THROUGH);
                }
            }
            // caller disposable metadata -> caller custom disposable metadata
            MetadataContainer callerCustomContainer = metadataManager.getMetadataContainer(MetadataType.CUSTOM, true);
            if (CollectionUtils.isNotEmpty(dynamicDisposableMetadata)) {
                for (Map.Entry<String, String> entry : dynamicDisposableMetadata.entrySet()) {
                    callerCustomContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.DISPOSABLE);
                }
            }
            // caller application metadata -> caller application disposable metadata
            MetadataContainer callerAppContainer = metadataManager.getMetadataContainer(MetadataType.APPLICATION, true);
            if (CollectionUtils.isNotEmpty(dynamicApplicationMetadata)) {
                for (Map.Entry<String, String> entry : dynamicApplicationMetadata.entrySet()) {
                    callerAppContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.DISPOSABLE);
                }
            }
        });
    }

    /**
     * Remove metadata context from current thread.
     */
    public static void remove() {
        com.tencent.polaris.metadata.core.manager.MetadataContextHolder.remove();
    }

    private static MetadataContext createMetadataContext() {
        MetadataContext metadataContext = new MetadataContext();
        StaticMetadataManager staticManager = StaticMetadataManager.getInstance();
        if (staticManager == null) {
            return metadataContext;
        }

        // local custom metadata (NONE transitive type)
        MetadataContainer customContainer = metadataContext.getMetadataContainer(MetadataType.CUSTOM, false);
        Map<String, String> mergedStaticMetadata = staticManager.getMergedStaticMetadata();
        for (Map.Entry<String, String> entry : mergedStaticMetadata.entrySet()) {
            customContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.NONE);
        }

        // local custom transitive metadata
        Map<String, String> mergedTransitive = staticManager.getMergedStaticTransitiveMetadata();
        for (Map.Entry<String, String> entry : mergedTransitive.entrySet()) {
            customContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.PASS_THROUGH);
        }

        // local custom disposable metadata
        Map<String, String> mergedDisposable = staticManager.getMergedStaticDisposableMetadata();
        for (Map.Entry<String, String> entry : mergedDisposable.entrySet()) {
            customContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.DISPOSABLE);
        }

        // local application disposable metadata
        MetadataContainer appContainer = metadataContext.getMetadataContainer(MetadataType.APPLICATION, false);
        for (Map.Entry<String, String> entry : mergedStaticMetadata.entrySet()) {
            appContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.DISPOSABLE);
        }

        return metadataContext;
    }
}
