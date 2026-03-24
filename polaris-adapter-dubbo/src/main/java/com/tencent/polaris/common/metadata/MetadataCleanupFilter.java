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
 * Filter that cleans up thread-local {@link com.tencent.polaris.metadata.core.manager.MetadataContext}
 * after each RPC invocation to prevent metadata pollution when threads are reused by thread pools.
 *
 * <p>Without this cleanup, metadata written during request A (such as transitive headers or
 * tracing data) would leak into request B if both run on the same pooled thread.</p>
 *
 * <p>Activated on both consumer and provider side with {@code order = Integer.MAX_VALUE},
 * making it the last filter in the chain — so all other filters and the actual invocation
 * have finished before cleanup happens.</p>
 */
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = Integer.MAX_VALUE)
public class MetadataCleanupFilter implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataCleanupFilter.class);

	public MetadataCleanupFilter() {
		LOGGER.info("[POLARIS] init polaris metadata cleanup filter");
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
