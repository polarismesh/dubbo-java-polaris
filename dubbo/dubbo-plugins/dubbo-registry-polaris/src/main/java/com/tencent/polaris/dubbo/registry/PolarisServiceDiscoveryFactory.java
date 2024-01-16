/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.polaris.dubbo.registry;

import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.utils.Consts;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.client.AbstractServiceDiscoveryFactory;
import org.apache.dubbo.registry.client.ServiceDiscovery;

public class PolarisServiceDiscoveryFactory extends AbstractServiceDiscoveryFactory {

    @Override
    protected String createRegistryCacheKey(URL url) {
        String namespace = url.getParameter(Consts.KEY_NAMESPACE);
        url = URL.valueOf(url.toServiceStringWithoutResolving());
        if (StringUtils.isNotEmpty(namespace)) {
            url = url.addParameter(Consts.KEY_NAMESPACE, namespace);
        }
        return url.toFullString();
    }

    @Override
    protected ServiceDiscovery createDiscovery(URL registryURL) {
        return new PolarisServiceDiscovery(applicationModel, registryURL);
    }
}
