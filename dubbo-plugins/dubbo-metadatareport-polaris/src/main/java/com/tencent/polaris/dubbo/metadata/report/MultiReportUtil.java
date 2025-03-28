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

package com.tencent.polaris.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReport;
import org.apache.dubbo.metadata.report.support.WrapAbstractMetadataReport;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MultiReportUtil {

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(MultiReportUtil.class);

    public static Optional<WrapAbstractMetadataReport> buildAnother(ApplicationModel application, URL url) {
        ExtensionLoader<MetadataReportFactory> loader = application.getExtensionLoader(MetadataReportFactory.class);
        Map<String, MetadataReportFactory> factoryMap = loader.getSupportedExtensionInstances().stream()
                .collect(HashMap::new, (m, v) -> m.put(loader.getExtensionName(v), v), HashMap::putAll);


        Optional<URL> ret = generate(url);
        if (!ret.isPresent()) {
            return Optional.empty();
        }
        URL copyUrl = ret.get();

        if (!factoryMap.containsKey(copyUrl.getProtocol())) {
            return Optional.empty();
        }
        MetadataReportFactory factory = factoryMap.get(copyUrl.getProtocol());

        Map<String, String> parameters = url.getParameters();
        parameters.remove("namespace");
        copyUrl.addParameters(parameters);

        MetadataReport report = factory.getMetadataReport(copyUrl);
        if (report instanceof AbstractMetadataReport) {
            return Optional.ofNullable(new WrapAbstractMetadataReport((AbstractMetadataReport) report));
        }
        return Optional.empty();
    }

    public static Optional<URL> generate(URL url) {
        String multiAddress = url.getParameter("multi_address", String.class);
        if (StringUtils.isBlank(multiAddress)) {
            return Optional.empty();
        }
        URL copyUrl = URL.valueOf(multiAddress);

        Map<String, String> parameters = url.getParameters();
        parameters.remove("namespace");
        parameters.remove("multi_address");
        copyUrl = copyUrl.addParameters(parameters);

        logger.info(String.format("another metadata-report connect url : %s", copyUrl.toString()));
        return Optional.of(copyUrl);
    }

}
