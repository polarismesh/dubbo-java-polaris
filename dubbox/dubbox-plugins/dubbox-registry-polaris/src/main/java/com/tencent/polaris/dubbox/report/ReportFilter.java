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

package com.tencent.polaris.dubbox.report;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.circuitbreak.client.exception.CallAbortedException;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Activate(group = Constants.CONSUMER)
public class ReportFilter extends PolarisOperatorDelegate implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportFilter.class);

	public ReportFilter() {
		LOGGER.info("[POLARIS] init polaris reporter");
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		long startTimeMilli = System.currentTimeMillis();
		try {
			Result result = invoker.invoke(invocation);
			long delay = System.currentTimeMillis() - startTimeMilli;
			if (result.hasException()) {
				onError(invoker, invocation, result.getException(), delay);
			} else {
				onSuccess(invoker, invocation, result, delay);
			}
			return result;
		}
		catch (RpcException e) {
			long delay = System.currentTimeMillis() - startTimeMilli;
			onError(invoker, invocation, e, delay);
			throw e;
		}
	}

	private void onSuccess(Invoker<?> invoker, Invocation invocation, Result result, long costMill) {
		PolarisOperator polarisOperator = getPolarisOperator();
		if (null == polarisOperator) {
			return;
		}
		RetStatus retStatus = RetStatus.RetSuccess;
		URL url = invoker.getUrl();
		polarisOperator.reportInvokeResult(url.getServiceInterface(), invocation.getMethodName(), url.getHost(),
				url.getPort(), RpcContext.getContext().getLocalHost(), costMill, retStatus, 0);
	}

	private void onError(Invoker<?> invoker, Invocation invocation, Throwable exception, long costMill) {
		PolarisOperator polarisOperator = getPolarisOperator();
		if (null == polarisOperator) {
			return;
		}
		RetStatus retStatus = RetStatus.RetFail;
		URL url = invoker.getUrl();
		int code = -1;
		if (exception instanceof RpcException) {
			RpcException rpcException = (RpcException) exception;
			code = rpcException.getCode();
			if (StringUtils.isNotBlank(exception.getMessage()) && exception.getMessage()
					.contains(PolarisBlockException.PREFIX)) {
				// 限流异常不进行熔断
				retStatus = RetStatus.RetFlowControl;
			}
			if (rpcException.getCause() instanceof CallAbortedException) {
				retStatus = RetStatus.RetReject;
			}
		}
		polarisOperator.reportInvokeResult(url.getServiceInterface(), invocation.getMethodName(), url.getHost(),
				url.getPort(), RpcContext.getContext().getLocalHost(), costMill, retStatus, code);
	}
}
