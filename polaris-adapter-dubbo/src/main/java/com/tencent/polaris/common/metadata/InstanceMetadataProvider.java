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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.dubbo.common.extension.SPI;

/**
 * SPI interface for providing custom instance metadata.
 * Users can implement this interface to inject custom metadata into the Polaris service instance.
 */
@SPI
public interface InstanceMetadataProvider {

    /**
     * @return all metadata key-value pairs.
     */
    default Map<String, String> getMetadata() {
        return Collections.emptyMap();
    }

    /**
     * @return the keys of transitive metadata (pass-through across the full call chain).
     */
    default Set<String> getTransitiveMetadataKeys() {
        return Collections.emptySet();
    }

    /**
     * @return the keys of disposable metadata (one-hop only).
     */
    default Set<String> getDisposableMetadataKeys() {
        return Collections.emptySet();
    }

    /**
     * @return the region of current instance.
     */
    default String getRegion() {
        return "";
    }

    /**
     * @return the zone of current instance.
     */
    default String getZone() {
        return "";
    }

    /**
     * @return the campus/datacenter of current instance.
     */
    default String getCampus() {
        return "";
    }
}
