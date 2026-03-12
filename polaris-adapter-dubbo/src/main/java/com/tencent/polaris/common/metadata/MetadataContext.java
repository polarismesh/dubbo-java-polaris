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

import java.util.HashMap;
import java.util.Map;

import com.tencent.polaris.metadata.core.MetadataContainer;
import com.tencent.polaris.metadata.core.MetadataStringValue;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;

/**
 * Dubbo metadata context extending polaris-java MetadataContext.
 * Provides fragment-based access to different categories of metadata.
 */
public class MetadataContext extends com.tencent.polaris.metadata.core.manager.MetadataContext {

    public static final String FRAGMENT_TRANSITIVE = "transitive";
    public static final String FRAGMENT_DISPOSABLE = "disposable";
    public static final String FRAGMENT_UPSTREAM_DISPOSABLE = "upstream-disposable";
    public static final String FRAGMENT_APPLICATION = "application";
    public static final String FRAGMENT_UPSTREAM_APPLICATION = "upstream-application";

    public MetadataContext() {
        super(MetadataConstants.POLARIS_TRANSITIVE_HEADER_PREFIX);
    }

    public Map<String, String> getFragmentContext(String fragment) {
        switch (fragment) {
            case FRAGMENT_TRANSITIVE:
                return getMetadataAsMap(MetadataType.CUSTOM, TransitiveType.PASS_THROUGH, false);
            case FRAGMENT_DISPOSABLE:
                return getMetadataAsMap(MetadataType.CUSTOM, TransitiveType.DISPOSABLE, false);
            case FRAGMENT_UPSTREAM_DISPOSABLE:
                return getMetadataAsMap(MetadataType.CUSTOM, TransitiveType.DISPOSABLE, true);
            case FRAGMENT_APPLICATION:
                return getMetadataAsMap(MetadataType.APPLICATION, TransitiveType.DISPOSABLE, false);
            case FRAGMENT_UPSTREAM_APPLICATION:
                return getMetadataAsMap(MetadataType.APPLICATION, TransitiveType.DISPOSABLE, true);
            default:
                return new HashMap<>();
        }
    }

    public void putFragmentContext(String fragment, Map<String, String> context) {
        switch (fragment) {
            case FRAGMENT_TRANSITIVE:
                putMetadataAsMap(MetadataType.CUSTOM, TransitiveType.PASS_THROUGH, false, context);
                break;
            case FRAGMENT_DISPOSABLE:
                putMetadataAsMap(MetadataType.CUSTOM, TransitiveType.DISPOSABLE, false, context);
                break;
            case FRAGMENT_UPSTREAM_DISPOSABLE:
                putMetadataAsMap(MetadataType.CUSTOM, TransitiveType.DISPOSABLE, true, context);
                break;
            case FRAGMENT_APPLICATION:
                putMetadataAsMap(MetadataType.APPLICATION, TransitiveType.DISPOSABLE, false, context);
                break;
            case FRAGMENT_UPSTREAM_APPLICATION:
                putMetadataAsMap(MetadataType.APPLICATION, TransitiveType.DISPOSABLE, true, context);
                break;
            default:
                break;
        }
    }

    public Map<String, String> getTransitiveMetadata() {
        return getFragmentContext(FRAGMENT_TRANSITIVE);
    }

    public Map<String, String> getDisposableMetadata() {
        return getFragmentContext(FRAGMENT_DISPOSABLE);
    }

    public Map<String, String> getApplicationMetadata() {
        return getFragmentContext(FRAGMENT_APPLICATION);
    }

    private Map<String, String> getMetadataAsMap(MetadataType metadataType, TransitiveType transitiveType, boolean caller) {
        MetadataContainer metadataContainer = getMetadataContainer(metadataType, caller);
        Map<String, String> values = new HashMap<>();
        metadataContainer.iterateMetadataValues((key, metadataValue) -> {
            if (metadataValue instanceof MetadataStringValue) {
                MetadataStringValue stringValue = (MetadataStringValue) metadataValue;
                if (stringValue.getTransitiveType() == transitiveType) {
                    values.put(key, stringValue.getStringValue());
                }
            }
        });
        return values;
    }

    private void putMetadataAsMap(MetadataType metadataType, TransitiveType transitiveType, boolean caller, Map<String, String> values) {
        MetadataContainer metadataContainer = getMetadataContainer(metadataType, caller);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            metadataContainer.putMetadataStringValue(entry.getKey(), entry.getValue(), transitiveType);
        }
    }
}
