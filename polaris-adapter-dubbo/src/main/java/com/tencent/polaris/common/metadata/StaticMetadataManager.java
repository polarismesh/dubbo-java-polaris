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

import com.tencent.polaris.api.utils.CollectionUtils;
import com.tencent.polaris.api.utils.StringUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages static metadata collected from environment variables, URL parameters, and SPI extensions.
 * Merge priority: SPI > Environment Variables > URL Parameters.
 */
public class StaticMetadataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticMetadataManager.class);

    private static volatile StaticMetadataManager instance;

    private Map<String, String> envMetadata;
    private Map<String, String> envTransitiveMetadata;
    private Map<String, String> envDisposableMetadata;

    private Map<String, String> urlMetadata;
    private Map<String, String> urlTransitiveMetadata;
    private Map<String, String> urlDisposableMetadata;

    private Map<String, String> customSPIMetadata;
    private Map<String, String> customSPITransitiveMetadata;
    private Map<String, String> customSPIDisposableMetadata;

    private Map<String, String> mergedStaticMetadata;
    private Map<String, String> mergedStaticTransitiveMetadata;
    private Map<String, String> mergedStaticDisposableMetadata;

    private String region;
    private String zone;
    private String campus;

    private StaticMetadataManager(URL url) {
        parseUrlMetadata(url);
        parseEnvMetadata();
        parseCustomMetadata();
        parseLocationMetadata();
        merge();
        LOGGER.info("[POLARIS] Loaded static metadata info. {}", this);
    }

    public static StaticMetadataManager getInstance() {
        return instance;
    }

    public static StaticMetadataManager getOrCreate(URL url) {
        if (instance == null) {
            synchronized (StaticMetadataManager.class) {
                if (instance == null) {
                    instance = new StaticMetadataManager(url);
                }
            }
        }
        return instance;
    }

    private void parseUrlMetadata(URL url) {
        urlMetadata = new HashMap<>();
        if (url == null) {
            urlMetadata = Collections.unmodifiableMap(urlMetadata);
            return;
        }
        Map<String, String> parameters = url.getParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(key) && key.startsWith(MetadataConstants.URL_METADATA_PREFIX)) {
                String sourceKey = key.substring(MetadataConstants.URL_METADATA_PREFIX_LENGTH);
                urlMetadata.put(sourceKey, entry.getValue());
                LOGGER.debug("[POLARIS] resolve metadata from URL. key = {}, value = {}", sourceKey, entry.getValue());
            }
        }
        urlMetadata = Collections.unmodifiableMap(urlMetadata);
        urlTransitiveMetadata = new HashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(key) && key.startsWith(MetadataConstants.URL_METADATA_TRANSITIVE_PREFIX)) {
                String sourceKey = key.substring(MetadataConstants.URL_METADATA_TRANSITIVE_PREFIX_LENGTH);
                urlTransitiveMetadata.put(sourceKey, entry.getValue());
                LOGGER.debug("[POLARIS] resolve transitive metadata from URL. key = {}, value = {}", sourceKey,
                        entry.getValue());
            }
        }
        urlTransitiveMetadata = Collections.unmodifiableMap(urlTransitiveMetadata);
        urlDisposableMetadata = new HashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(key) && key.startsWith(MetadataConstants.URL_METADATA_DISPOSABLE_PREFIX)) {
                String sourceKey = key.substring(MetadataConstants.URL_METADATA_DISPOSABLE_PREFIX_LENGTH);
                urlDisposableMetadata.put(sourceKey, entry.getValue());
                LOGGER.debug("[POLARIS] resolve disposable metadata from URL. key = {}, value = {}", sourceKey,
                        entry.getValue());
            }
        }
        urlDisposableMetadata = Collections.unmodifiableMap(urlDisposableMetadata);
    }

    private void parseEnvMetadata() {
        Map<String, String> allEnvs = System.getenv();

        envMetadata = new HashMap<>();
        for (Map.Entry<String, String> entry : allEnvs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StringUtils.isNotBlank(key)
                    && key.startsWith(MetadataConstants.ENV_METADATA_PREFIX)
                    && !key.equals(MetadataConstants.ENV_METADATA_CONTENT_TRANSITIVE)
                    && !key.equals(MetadataConstants.ENV_METADATA_CONTENT_DISPOSABLE)) {
                String sourceKey = key.substring(MetadataConstants.ENV_METADATA_PREFIX_LENGTH);
                envMetadata.put(sourceKey, value);
                LOGGER.info("[POLARIS] resolve metadata from env. key = {}, value = {}", sourceKey, value);
            }
        }
        envMetadata = Collections.unmodifiableMap(envMetadata);

        // parse transitive metadata keys
        envTransitiveMetadata = new HashMap<>();
        String transitiveKeys = allEnvs.get(MetadataConstants.ENV_METADATA_CONTENT_TRANSITIVE);
        if (StringUtils.isNotBlank(transitiveKeys)) {
            String[] keyArr = StringUtils.split(transitiveKeys, ",");
            if (keyArr != null) {
                for (String k : keyArr) {
                    String v = envMetadata.get(k);
                    if (StringUtils.isNotBlank(v)) {
                        envTransitiveMetadata.put(k, v);
                    }
                }
            }
        }
        envTransitiveMetadata = Collections.unmodifiableMap(envTransitiveMetadata);

        // parse disposable metadata keys
        envDisposableMetadata = new HashMap<>();
        String disposableKeys = allEnvs.get(MetadataConstants.ENV_METADATA_CONTENT_DISPOSABLE);
        if (StringUtils.isNotBlank(disposableKeys)) {
            String[] keyArr = StringUtils.split(disposableKeys, ",");
            if (keyArr != null) {
                for (String k : keyArr) {
                    String v = envMetadata.get(k);
                    if (StringUtils.isNotBlank(v)) {
                        envDisposableMetadata.put(k, v);
                    }
                }
            }
        }
        envDisposableMetadata = Collections.unmodifiableMap(envDisposableMetadata);
    }

    private void parseCustomMetadata() {
        customSPIMetadata = new HashMap<>();
        customSPITransitiveMetadata = new HashMap<>();
        customSPIDisposableMetadata = new HashMap<>();

        try {
            ExtensionLoader<InstanceMetadataProvider> loader = ExtensionLoader.getExtensionLoader(
                    InstanceMetadataProvider.class);
            Set<String> extensions = loader.getSupportedExtensions();
            if (!CollectionUtils.isEmpty(extensions)) {
                for (String name : extensions) {
                    InstanceMetadataProvider provider = loader.getExtension(name);
                    parseCustomMetadata(provider);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[POLARIS] No InstanceMetadataProvider SPI found: {}", e.getMessage());
        }

        customSPIMetadata = Collections.unmodifiableMap(customSPIMetadata);
        customSPITransitiveMetadata = Collections.unmodifiableMap(customSPITransitiveMetadata);
        customSPIDisposableMetadata = Collections.unmodifiableMap(customSPIDisposableMetadata);
    }

    private void parseCustomMetadata(InstanceMetadataProvider provider) {
        Map<String, String> allMetadata = provider.getMetadata();
        if (!CollectionUtils.isEmpty(allMetadata)) {
            customSPIMetadata.putAll(allMetadata);
        }

        Set<String> transitiveKeys = provider.getTransitiveMetadataKeys();
        if (!CollectionUtils.isEmpty(transitiveKeys)) {
            for (String key : transitiveKeys) {
                if (customSPIMetadata.containsKey(key)) {
                    customSPITransitiveMetadata.put(key, customSPIMetadata.get(key));
                }
            }
        }

        Set<String> disposableKeys = provider.getDisposableMetadataKeys();
        if (!CollectionUtils.isEmpty(disposableKeys)) {
            for (String key : disposableKeys) {
                if (customSPIMetadata.containsKey(key)) {
                    customSPIDisposableMetadata.put(key, customSPIMetadata.get(key));
                }
            }
        }
    }

    private void parseLocationMetadata() {
        region = urlMetadata.get(MetadataConstants.LOCATION_KEY_REGION);
        if (StringUtils.isBlank(region)) {
            region = resolveLocationFromSPI(InstanceMetadataProvider::getRegion);
        }
        if (StringUtils.isBlank(region)) {
            region = System.getenv(MetadataConstants.ENV_METADATA_REGION);
        }

        zone = urlMetadata.get(MetadataConstants.LOCATION_KEY_ZONE);
        if (StringUtils.isBlank(zone)) {
            zone = resolveLocationFromSPI(InstanceMetadataProvider::getZone);
        }
        if (StringUtils.isBlank(zone)) {
            zone = System.getenv(MetadataConstants.ENV_METADATA_ZONE);
        }

        campus = urlMetadata.get(MetadataConstants.LOCATION_KEY_CAMPUS);
        if (StringUtils.isBlank(campus)) {
            campus = resolveLocationFromSPI(InstanceMetadataProvider::getCampus);
        }
        if (StringUtils.isBlank(campus)) {
            campus = System.getenv(MetadataConstants.ENV_METADATA_CAMPUS);
        }
    }

    private String resolveLocationFromSPI(java.util.function.Function<InstanceMetadataProvider, String> extractor) {
        try {
            ExtensionLoader<InstanceMetadataProvider> loader = ExtensionLoader.getExtensionLoader(
                    InstanceMetadataProvider.class);
            Set<String> extensions = loader.getSupportedExtensions();
            if (!CollectionUtils.isEmpty(extensions)) {
                for (String name : extensions) {
                    InstanceMetadataProvider provider = loader.getExtension(name);
                    String value = extractor.apply(provider);
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private void merge() {
        // priority: SPI > env > URL
        Map<String, String> merged = new HashMap<>();
        merged.putAll(urlMetadata);
        merged.putAll(envMetadata);
        merged.putAll(customSPIMetadata);
        this.mergedStaticMetadata = Collections.unmodifiableMap(merged);

        Map<String, String> mergedTransitive = new HashMap<>();
        mergedTransitive.putAll(urlTransitiveMetadata);
        mergedTransitive.putAll(envTransitiveMetadata);
        mergedTransitive.putAll(customSPITransitiveMetadata);
        this.mergedStaticTransitiveMetadata = Collections.unmodifiableMap(mergedTransitive);

        Map<String, String> mergedDisposable = new HashMap<>();
        mergedDisposable.putAll(urlDisposableMetadata);
        mergedDisposable.putAll(envDisposableMetadata);
        mergedDisposable.putAll(customSPIDisposableMetadata);
        this.mergedStaticDisposableMetadata = Collections.unmodifiableMap(mergedDisposable);
    }

    public Map<String, String> getMergedStaticMetadata() {
        return mergedStaticMetadata;
    }

    public Map<String, String> getMergedStaticTransitiveMetadata() {
        return mergedStaticTransitiveMetadata;
    }

    public Map<String, String> getMergedStaticDisposableMetadata() {
        return mergedStaticDisposableMetadata;
    }

    public String getRegion() {
        return region;
    }

    public String getZone() {
        return zone;
    }

    public String getCampus() {
        return campus;
    }

    public Map<String, String> getLocationMetadata() {
        Map<String, String> locationMetadata = new HashMap<>();
        if (StringUtils.isNotBlank(region)) {
            locationMetadata.put(MetadataConstants.LOCATION_KEY_REGION, region);
        }
        if (StringUtils.isNotBlank(zone)) {
            locationMetadata.put(MetadataConstants.LOCATION_KEY_ZONE, zone);
        }
        if (StringUtils.isNotBlank(campus)) {
            locationMetadata.put(MetadataConstants.LOCATION_KEY_CAMPUS, campus);
        }
        return locationMetadata;
    }

    @Override
    public String toString() {
        return "StaticMetadataManager{" +
                "envMetadata=" + envMetadata +
                ", envTransitiveMetadata=" + envTransitiveMetadata +
                ", envDisposableMetadata=" + envDisposableMetadata +
                ", urlMetadata=" + urlMetadata +
                ", customSPIMetadata=" + customSPIMetadata +
                ", customSPITransitiveMetadata=" + customSPITransitiveMetadata +
                ", customSPIDisposableMetadata=" + customSPIDisposableMetadata +
                ", mergedStaticMetadata=" + mergedStaticMetadata +
                ", mergedStaticTransitiveMetadata=" + mergedStaticTransitiveMetadata +
                ", mergedStaticDisposableMetadata=" + mergedStaticDisposableMetadata +
                ", region='" + region + '\'' +
                ", zone='" + zone + '\'' +
                ", campus='" + campus + '\'' +
                '}';
    }
}
