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
import com.tencent.polaris.api.pojo.CircuitBreakerStatus;
import com.tencent.polaris.api.pojo.RetStatus;
import com.tencent.polaris.api.pojo.ServiceKey;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.circuitbreak.api.CircuitBreakAPI;
import com.tencent.polaris.circuitbreak.api.InvokeHandler;
import com.tencent.polaris.circuitbreak.api.pojo.InvokeContext;
import com.tencent.polaris.circuitbreak.api.pojo.ResultToErrorCode;
import com.tencent.polaris.circuitbreak.client.exception.CallAbortedException;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import com.tencent.polaris.common.utils.DubboUtils;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

@Activate(group = CommonConstants.CONSUMER)
public class CircuitBreakerFilter extends PolarisOperatorDelegate implements Filter, ResultToErrorCode {

    protected final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final PolarisOperator operator;

    private final CircuitBreakAPI circuitBreakAPI;

    private CircuitBreakerCallback circuitBreakerCallback;

    public CircuitBreakerFilter() {
        logger.info("[POLARIS] init polaris circuitbreaker");
        this.operator = getGovernancePolarisOperator();
        this.circuitBreakAPI = operator.getCircuitBreakAPI();
        ServiceLoader<CircuitBreakerCallback> loader = ServiceLoader.load(CircuitBreakerCallback.class);
        if (loader.iterator().hasNext()) {
            circuitBreakerCallback = loader.iterator().next();
        }
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        List<DubboServiceInfo> serviceInfos = DubboUtils.analyzeRemoteDubboServiceInfo(invoker, invocation);
        if (serviceInfos.isEmpty() || Objects.isNull(operator)) {
            return invoker.invoke(invocation);
        }
        // 如果是熔断，只处理第一个的请求
        DubboServiceInfo firstService = serviceInfos.get(0);

        InvokeContext.RequestContext context = new InvokeContext.RequestContext(createCalleeService(firstService), firstService.getDubboInterface());
        context.setSourceService(new ServiceKey());
        context.setResultToErrorCode(this);
        InvokeHandler handler = circuitBreakAPI.makeInvokeHandler(context);
        try {
            long startTimeMilli = System.currentTimeMillis();
            InvokeContext.ResponseContext responseContext = new InvokeContext.ResponseContext();
            responseContext.setDurationUnit(TimeUnit.MILLISECONDS);
            Result result = null;
            RpcException exception = null;
            handler.acquirePermission();
            try {
                result = invoker.invoke(invocation);
                responseContext.setDuration(System.currentTimeMillis() - startTimeMilli);
                if (result.hasException()) {
                    responseContext.setError(result.getException());
                    handler.onError(responseContext);
                } else {
                    responseContext.setResult(result);
                    handler.onSuccess(responseContext);
                }
            } catch (RpcException e) {
                exception = e;
                responseContext.setError(e);
                responseContext.setDuration(System.currentTimeMillis() - startTimeMilli);
                handler.onError(responseContext);
            }
            ResourceStat resourceStat = createInstanceResourceStat(invoker, invocation, firstService, responseContext, responseContext.getDuration());
            circuitBreakAPI.report(resourceStat);
            if (result != null) {
                return result;
            }
            throw exception;
        } catch (CallAbortedException abortedException) {
            CircuitBreakerStatus.FallbackInfo fallbackInfo = abortedException.getFallbackInfo();
            if (Objects.nonNull(fallbackInfo)) {
                AppResponse response = new AppResponse();
                response.setAttachments(fallbackInfo.getHeaders());
                response.setValue(fallbackInfo.getBody());
                return AsyncRpcResult.newDefaultAsyncResult(response, invocation);
            }
            if (Objects.nonNull(circuitBreakerCallback)) {
                return circuitBreakerCallback.onCircuitBreaker(invoker, invocation, abortedException);
            }
            throw new RpcException(abortedException);
        }
    }

    private ResourceStat createInstanceResourceStat(Invoker<?> invoker, Invocation invocation, DubboServiceInfo serviceInfo,
                                                    InvokeContext.ResponseContext context, long delay) {
        URL url = invoker.getUrl();
        Throwable exception = context.getError();
        RetStatus retStatus = RetStatus.RetSuccess;
        int code = 0;
        if (null != exception) {
            retStatus = RetStatus.RetFail;
            if (exception instanceof RpcException) {
                RpcException rpcException = (RpcException) exception;
                code = rpcException.getCode();
                if (StringUtils.isNotBlank(rpcException.getMessage()) && rpcException.getMessage()
                        .contains(PolarisBlockException.PREFIX)) {
                    // 限流异常不进行熔断
                    retStatus = RetStatus.RetFlowControl;
                }
                if (rpcException.isTimeout()) {
                    retStatus = RetStatus.RetTimeout;
                }
            } else {
                code = -1;
            }
        }

        ServiceKey calleeServiceKey = createCalleeService(serviceInfo);
        Resource resource = new InstanceResource(
                calleeServiceKey,
                url.getHost(),
                url.getPort(),
                new ServiceKey()
        );
        return new ResourceStat(resource, code, delay, retStatus);
    }

    private ServiceKey createCalleeService(DubboServiceInfo serviceInfo) {
        return new ServiceKey(operator.getPolarisConfig().getNamespace(), serviceInfo.getService());
    }

    @Override
    public int onSuccess(Object value) {
        return 0;
    }

    @Override
    public int onError(Throwable throwable) {
        int code = 0;
        if (throwable instanceof RpcException) {
            RpcException rpcException = (RpcException) throwable;
            code = rpcException.getCode();
        } else {
            code = -1;
        }
        return code;
    }
}
