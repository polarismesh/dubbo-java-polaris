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

public interface MetadataConstants {

    /**
     * Prefix for environment variables that define metadata content.
     */
    String ENV_METADATA_PREFIX = "DUBBO_METADATA_CONTENT_";

    int ENV_METADATA_PREFIX_LENGTH = ENV_METADATA_PREFIX.length();

    /**
     * Prefix for Dubbo URL parameters that define metadata.
     */
    String URL_METADATA_PREFIX = "polaris_metadata_content_";

    int URL_METADATA_PREFIX_LENGTH = URL_METADATA_PREFIX.length();

    String URL_METADATA_TRANSITIVE_PREFIX = "polaris_metadata_transitive_";

    int URL_METADATA_TRANSITIVE_PREFIX_LENGTH = URL_METADATA_TRANSITIVE_PREFIX.length();

    String URL_METADATA_DISPOSABLE_PREFIX = "polaris_metadata_disposable_";

    int URL_METADATA_DISPOSABLE_PREFIX_LENGTH = URL_METADATA_DISPOSABLE_PREFIX.length();
    
    /**
     * Environment variable: comma-separated keys that should be transitive.
     */
    String ENV_METADATA_CONTENT_TRANSITIVE = "DUBBO_METADATA_CONTENT_TRANSITIVE";

    /**
     * Environment variable: comma-separated keys that should be disposable.
     */
    String ENV_METADATA_CONTENT_DISPOSABLE = "DUBBO_METADATA_CONTENT_DISPOSABLE";

    /**
     * Environment variable for zone.
     */
    String ENV_METADATA_ZONE = "DUBBO_METADATA_ZONE";

    /**
     * Environment variable for region.
     */
    String ENV_METADATA_REGION = "DUBBO_METADATA_REGION";

    /**
     * Environment variable for campus.
     */
    String ENV_METADATA_CAMPUS = "DUBBO_METADATA_CAMPUS";

    /**
     * Metadata key for region.
     */
    String LOCATION_KEY_REGION = "region";

    /**
     * Metadata key for zone.
     */
    String LOCATION_KEY_ZONE = "zone";

    /**
     * Metadata key for campus/datacenter.
     */
    String LOCATION_KEY_CAMPUS = "campus";
}
