/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.tencent.polaris.dubbo.report;


import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.circuitbreak.client.exception.CallAbortedException;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import com.tencent.polaris.common.utils.DubboUtils;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Activate(group = CommonConstants.CONSUMER, order = Integer.MIN_VALUE)
public class ReportFilter extends PolarisOperatorDelegate implements Filter, Filter.Listener {

	private static final String LABEL_START_TIME = "reporter_filter_start_time";

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportFilter.class);

	private ApplicationModel applicationModel;

	public ReportFilter() {
		LOGGER.info("[POLARIS] init polaris reporter");
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		invocation.put(LABEL_START_TIME, System.currentTimeMillis());
        return invoker.invoke(invocation);
	}

	@Override
	public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
		PolarisOperator polarisOperator = getGovernancePolarisOperator();
		if (null == polarisOperator) {
			return;
		}
		Long startTimeMilli = (Long) invocation.get(LABEL_START_TIME);
		RetStatus retStatus = RetStatus.RetSuccess;
		int code = 0;
		if (appResponse.hasException()) {
			retStatus = RetStatus.RetFail;
			code = -1;
		}
		URL url = invoker.getUrl();
		long delay = System.currentTimeMillis() - startTimeMilli;
		List<DubboServiceInfo> serviceInfos = DubboUtils.analyzeRemoteDubboServiceInfo(invoker, invocation);
		for (DubboServiceInfo serviceInfo : serviceInfos) {
			polarisOperator.reportInvokeResult(serviceInfo.getService(), serviceInfo.getReportMethodName(), url.getHost(),
					url.getPort(), RpcContext.getServiceContext().getLocalHost(), delay, retStatus, code);
		}
	}

	@Override
	public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
		PolarisOperator polarisOperator = getGovernancePolarisOperator();
		if (null == polarisOperator) {
			return;
		}
		Long startTimeMilli = (Long) invocation.get(LABEL_START_TIME);
		RetStatus retStatus = RetStatus.RetFail;
		int code = -1;
		if (t instanceof RpcException) {
			RpcException rpcException = (RpcException) t;
			code = rpcException.getCode();
			if (isFlowControl(rpcException)) {
				retStatus = RetStatus.RetFlowControl;
			}
			if (rpcException.isTimeout()) {
				retStatus = RetStatus.RetTimeout;
			}
			if (rpcException.getCause() instanceof CallAbortedException) {
				retStatus = RetStatus.RetReject;
			}
		}
		URL url = invoker.getUrl();
		long delay = System.currentTimeMillis() - startTimeMilli;
		List<DubboServiceInfo> serviceInfos = DubboUtils.analyzeRemoteDubboServiceInfo(invoker, invocation);
		for (DubboServiceInfo serviceInfo : serviceInfos) {
			polarisOperator.reportInvokeResult(serviceInfo.getService(), serviceInfo.getReportMethodName(), url.getHost(),
					url.getPort(), RpcContext.getServiceContext().getLocalHost(), delay, retStatus, code);
		}
	}

    private boolean isFlowControl(RpcException rpcException) {
        boolean a = StringUtils.isNotBlank(rpcException.getMessage()) && rpcException.getMessage()
                .contains(PolarisBlockException.PREFIX);
        boolean b = rpcException.isLimitExceed();
        return a || b;
    }

}
