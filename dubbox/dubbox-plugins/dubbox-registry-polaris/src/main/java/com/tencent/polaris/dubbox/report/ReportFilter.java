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
import com.alibaba.dubbo.rpc.RpcException;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.utils.StringUtils;
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
        Result result = null;
        RpcException rpcException = null;
        Throwable exception = null;
        try {
            result = invoker.invoke(invocation);
        } catch (Throwable e) {
            exception = e;
        }
        if (null != result && result.hasException()) {
            exception = result.getException();
        }
        if (exception instanceof RpcException) {
            rpcException = (RpcException) exception;
        }
        PolarisOperator polarisOperator = getPolarisOperator();
        if (null == polarisOperator) {
            return result;
        }
        RetStatus retStatus = RetStatus.RetSuccess;
        int code = 0;
        if (null != exception) {
            retStatus = RetStatus.RetFail;
            if (null != rpcException) {
                code = rpcException.getCode();
                if (StringUtils.isNotBlank(rpcException.getMessage()) && rpcException.getMessage()
                        .contains(PolarisBlockException.PREFIX)) {
                    // 限流异常不进行熔断
                    retStatus = RetStatus.RetSuccess;
                }
            } else {
                code = -1;
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
