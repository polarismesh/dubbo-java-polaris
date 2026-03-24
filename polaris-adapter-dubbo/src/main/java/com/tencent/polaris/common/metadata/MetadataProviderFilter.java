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

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider-side filter that cleans up thread-local {@link com.tencent.polaris.metadata.core.manager.MetadataContext}
 * after each RPC invocation to prevent metadata pollution when threads are reused by thread pools.
 *
 * <p>Without this cleanup, metadata written during request A (such as transitive headers or
 * tracing data) would leak into request B if both run on the same pooled thread.</p>
 *
 * <p>Activated with {@code order = Integer.MIN_VALUE} so it wraps the entire provider filter chain,
 * ensuring cleanup happens after all other filters have completed.</p>
 */
@Activate(group = CommonConstants.PROVIDER, order = Integer.MIN_VALUE)
public class MetadataProviderFilter implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataProviderFilter.class);

	public MetadataProviderFilter() {
		LOGGER.info("[POLARIS] init polaris metadata provider filter");
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		try {
			return invoker.invoke(invocation);
		} finally {
			MetadataContextHolder.remove();
		}
	}
}
