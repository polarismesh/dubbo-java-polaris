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
 * Consumer-side filter that initializes thread-local {@link com.tencent.polaris.metadata.core.manager.MetadataContext}
 * with Dubbo-side static metadata before the RPC invocation.
 *
 * <p>Activated with {@code order = Integer.MIN_VALUE} so it runs first in the consumer filter chain.
 * Does NOT clean up the context after invocation, because the consumer thread (business thread)
 * may invoke multiple Dubbo services within the same request and needs to share the same context.</p>
 */
@Activate(group = CommonConstants.CONSUMER, order = Integer.MIN_VALUE)
public class MetadataConsumerFilter implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConsumerFilter.class);

	public MetadataConsumerFilter() {
		LOGGER.info("[POLARIS] init polaris metadata consumer filter");
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		MetadataContextHolder.get();
		return invoker.invoke(invocation);
	}
}
