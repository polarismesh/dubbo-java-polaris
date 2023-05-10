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
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import com.tencent.polaris.common.utils.ExampleConsts;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Activate(group = CommonConstants.CONSUMER)
public class ReportFilter extends PolarisOperatorDelegate implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportFilter.class);

    public ReportFilter() {
        LOGGER.info("[POLARIS] init polaris reporter");
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        long startTimeMilli = System.currentTimeMillis();
        Result result = null;
        Throwable exception = null;
        RpcException rpcException = null;
        try {
            result = invoker.invoke(invocation);
        } catch (RpcException e) {
            rpcException = e;
        }
        if (null != result && result.hasException()) {
            exception = result.getException();
        }
        PolarisOperator polarisOperator = getPolarisOperator();
        if (null == polarisOperator) {
            return result;
        }
        RetStatus retStatus = RetStatus.RetSuccess;
        int code = 0;
        if (null != exception) {
            retStatus = RetStatus.RetFail;
            code = -1;
        }
        if (null != rpcException) {
            code = rpcException.getCode();
            if (StringUtils.isNotBlank(rpcException.getMessage()) && rpcException.getMessage()
                    .contains(PolarisBlockException.PREFIX)) {
                // 限流异常不进行熔断
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
        polarisOperator.reportInvokeResult(url.getServiceInterface(), invocation.getMethodName(), url.getHost(),
                url.getPort(), delay, retStatus, code);
        if (null != rpcException) {
            throw rpcException;
        } else if (null != exception) {
            throw new RpcException(exception);
        }
        return result;
    }
}
