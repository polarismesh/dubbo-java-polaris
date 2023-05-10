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

package com.tencent.polaris.dubbo.circuitbreaker;

import com.tencent.polaris.api.plugin.circuitbreaker.ResourceStat;
import com.tencent.polaris.api.plugin.circuitbreaker.entity.InstanceResource;
import com.tencent.polaris.api.plugin.circuitbreaker.entity.Resource;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.api.InvokeHandler;
import com.tencent.polaris.circuitbreak.api.pojo.InvokeContext;
import com.tencent.polaris.circuitbreak.api.pojo.ResultToErrorCode;
import com.tencent.polaris.circuitbreak.client.exception.CallAbortedException;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;

@Activate(group = CommonConstants.CONSUMER)
public class CircuitBreakerFilter extends PolarisOperatorDelegate implements Filter, ResultToErrorCode {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        PolarisOperator polarisOperator = getPolarisOperator();
        if (null == polarisOperator) {
            return invoker.invoke(invocation);
        }

        CircuitBreakAPI circuitBreakAPI = getPolarisOperator().getCircuitBreakAPI();
        InvokeContext.RequestContext context = new InvokeContext.RequestContext(createCalleeService(invoker), invocation.getMethodName());
        InvokeHandler handler = circuitBreakAPI.makeInvokeHandler(context);
        Result result = null;
        Throwable exception = null;
        long startTimeMilli = System.currentTimeMillis();
        InvokeContext.ResponseContext responseContext = new InvokeContext.ResponseContext();
        try {
            handler.acquirePermission();
            try {
                result = invoker.invoke(invocation);
                responseContext.setResult(result);
                responseContext.setDuration(System.currentTimeMillis() - startTimeMilli);
                handler.onSuccess(responseContext);
            }
            catch (Throwable e) {
                exception = e;
                responseContext.setError(e);
                responseContext.setDuration(System.currentTimeMillis() - startTimeMilli);
                handler.onError(responseContext);
            }
            ResourceStat resourceStat = createInstanceResourceStat(invoker, invocation, result, exception, responseContext.getDuration());
            circuitBreakAPI.report(resourceStat);
            return result;
        }
        catch (CallAbortedException abortedException) {
            throw new com.alibaba.dubbo.rpc.RpcException(abortedException);
        }
    }

    private ResourceStat createInstanceResourceStat(Invoker<?> invoker, Invocation invocation,
            Result result, Throwable exception, long delay) {
        URL url = invoker.getUrl();
        RpcException rpcException = null;
        if (null != result && result.hasException()) {
            exception = result.getException();
        }
        if (exception instanceof RpcException) {
            rpcException = (RpcException) exception;
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
                    retStatus = RetStatus.RetFlowControl;
                }
                if (rpcException.isTimeout()) {
                    retStatus = RetStatus.RetTimeout;
                }
            }
            else {
                code = -1;
            }
        }

        ServiceKey calleeServiceKey = createCalleeService(invoker);
        Resource resource = new InstanceResource(
                calleeServiceKey,
                url.getHost(),
                url.getPort(),
                new ServiceKey()
        );
        return new ResourceStat(resource, code, delay, retStatus);
    }

    private ServiceKey createCalleeService(Invoker<?> invoker) {
        URL url = invoker.getUrl();
        return new ServiceKey(getPolarisOperator().getPolarisConfig().getNamespace(), url.getServiceInterface());
    }

    @Override
    public int onSuccess(Object value) {
        Result result = (Result) value;
        if (result.hasException()) {
            Throwable throwable = result.getException();
            return onError(throwable);
        }
        return 0;
    }

    @Override
    public int onError(Throwable throwable) {
        int code = 0;
        if (throwable instanceof RpcException) {
            RpcException rpcException = (RpcException) throwable;
            code = rpcException.getCode();
        }
        else {
            code = -1;
        }
        return code;
    }
}
