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

package com.tencent.polaris.dubbo.ratelimit;

import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.parser.QueryParser;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import com.tencent.polaris.common.router.RuleHandler;
import com.tencent.polaris.common.utils.DubboUtils;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResultCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.tencent.polaris.specification.api.v1.traffic.manage.RateLimitProto;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;
import org.slf4j.Logger;

@Activate(group = CommonConstants.PROVIDER)
public class RateLimitFilter extends PolarisOperatorDelegate implements Filter, ScopeModelAware {

    protected final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final RuleHandler ruleHandler;

    private final QueryParser parser;

    public RateLimitFilter() {
        logger.info("[POLARIS] init polaris ratelimit");
        this.ruleHandler = new RuleHandler();
        this.parser = QueryParser.load();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        PolarisOperator polarisOperator = getGovernancePolarisOperator();
        if (null == polarisOperator) {
            return invoker.invoke(invocation);
        }
        List<DubboServiceInfo> serviceInfos = DubboUtils.analyzeLocalDubboServiceInfo(applicationModel, invoker, invocation);
        for (DubboServiceInfo serviceInfo : serviceInfos) {
            checkRateLimit(polarisOperator, invoker, invocation, serviceInfo);
        }
        return invoker.invoke(invocation);
    }

    private void checkRateLimit(PolarisOperator operator, Invoker<?> invoker, Invocation invocation, DubboServiceInfo serviceInfo) {
        ServiceRule serviceRule = operator.getServiceRule(serviceInfo.getService(), EventType.RATE_LIMITING);
        Object ruleObject = serviceRule.getRule();
        if (Objects.isNull(ruleObject)) {
            return;
        }
        RateLimitProto.RateLimit rateLimit = (RateLimitProto.RateLimit) ruleObject;
        Set<RateLimitProto.MatchArgument> trafficLabels = ruleHandler.getRatelimitLabels(rateLimit);
        Set<Argument> arguments = new HashSet<>();
        for (RateLimitProto.MatchArgument matchArgument : trafficLabels) {
            switch (matchArgument.getType()) {
                case HEADER:
                    String attachmentValue = RpcContext.getContext().getAttachment(matchArgument.getKey());
                    if (!StringUtils.isBlank(attachmentValue)) {
                        arguments.add(Argument.buildHeader(matchArgument.getKey(), attachmentValue));
                    }
                    break;
                case QUERY:
                    Optional<String> queryValue = parser.parse(matchArgument.getKey(), invocation.getArguments());
                    queryValue.ifPresent(s -> arguments.add(Argument.buildQuery(matchArgument.getKey(), s)));
                    break;
                default:
                    break;
            }
        }
        if (StringUtils.isNotEmpty(serviceInfo.getMethodName())) {
            arguments.add(Argument.buildMethod(serviceInfo.getMethodName()));
        }
        QuotaResponse quotaResponse = null;
        try {
            quotaResponse = operator.getQuota(serviceInfo.getService(), serviceInfo.getDubboInterface(), arguments);
        } catch (PolarisException e) {
            Map<String, Object> externalParam = new HashMap<>();
            externalParam.put("serviceInfo", serviceInfo);
            externalParam.put("arguments", arguments);
            logger.error(formatCode(e.getCode()),
                    e.getMessage(),
                    externalParam.toString(),
                    "check rate_limit by polaris fail");
        }
        if (null != quotaResponse && quotaResponse.getCode() == QuotaResultCode.QuotaResultLimited) {
            // 请求被限流，则抛出异常
            throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION, new PolarisBlockException(
                    String.format("url=%s, info=%s", invoker.getUrl(), quotaResponse.getInfo())));
        }
    }

}