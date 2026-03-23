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

import com.tencent.polaris.metadata.core.MetadataContainer;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;
import com.tencent.polaris.metadata.core.manager.MetadataContext;
import java.util.Map;

/**
 * Thread-local holder for {@link MetadataContext}.
 * Delegates to polaris-java's MetadataContextHolder for thread-local management.
 *
 * <p>Uses the polaris-java base {@link MetadataContext} directly instead of a custom subclass,
 * so that it stays compatible with Spring Cloud Tencent's MetadataContext when both
 * frameworks coexist in the same classloader.</p>
 *
 * <p>Does NOT register a global initializer via {@code setInitializer}, so it won't conflict
 * with Spring Cloud Tencent's initializer. Instead, it lazily enriches the context with
 * Dubbo-side static metadata on every {@link #get()} call.</p>
 */
public final class MetadataContextHolder {

    private static final ThreadLocal<MetadataContext> ENRICHED = new ThreadLocal<>();

    private MetadataContextHolder() {
    }

    /**
     * Get current thread's MetadataContext (create if absent).
     * Lazily enriches the context with Dubbo-side static metadata.
     */
    public static MetadataContext get() {
        MetadataContext ctx = com.tencent.polaris.metadata.core.manager.MetadataContextHolder.getOrCreate();
        if (ENRICHED.get() != ctx) {
            enrichWithDubboStaticMetadata(ctx);
            ENRICHED.set(ctx);
        }
        return ctx;
    }

    /**
     * Enrich the given MetadataContext with Dubbo-side static metadata.
     * This is idempotent — calling it multiple times with the same context is safe
     * because putMetadataStringValue overwrites with the same value.
     */
    private static void enrichWithDubboStaticMetadata(MetadataContext metadataContext) {
        StaticMetadataManager staticManager = StaticMetadataManager.getInstance();
        if (staticManager == null) {
            return;
        }

        // local custom metadata (NONE transitive type)
        MetadataContainer customContainer = metadataContext.getMetadataContainer(MetadataType.CUSTOM, false);
        Map<String, String> mergedStaticMetadata = staticManager.getMergedStaticMetadata();
        for (Map.Entry<String, String> entry : mergedStaticMetadata.entrySet()) {
            customContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.NONE);
        }

        // local application disposable metadata
        MetadataContainer appContainer = metadataContext.getMetadataContainer(MetadataType.APPLICATION, false);
        for (Map.Entry<String, String> entry : mergedStaticMetadata.entrySet()) {
            appContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), TransitiveType.DISPOSABLE);
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
    }
}
