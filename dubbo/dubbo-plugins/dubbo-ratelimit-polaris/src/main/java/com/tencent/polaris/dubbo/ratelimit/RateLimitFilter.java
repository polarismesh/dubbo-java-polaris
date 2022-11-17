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

import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.client.pb.ModelProto.MatchArgument;
import com.tencent.polaris.client.pb.RateLimitProto.RateLimit;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.router.ObjectParser;
import com.tencent.polaris.common.router.RuleHandler;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResultCode;
import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Activate(group = CommonConstants.PROVIDER)
public class RateLimitFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RuleHandler ruleHandler;

    private final PolarisOperator polarisOperator;

    public RateLimitFilter() {
        LOGGER.info("[POLARIS] init polaris ratelimit");
        ruleHandler = new RuleHandler();
        polarisOperator = PolarisOperators.INSTANCE.getFirstPolarisOperator();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String service = invoker.getInterface().getName();
        if (null == polarisOperator) {
            return invoker.invoke(invocation);
        }
        ServiceRule serviceRule = polarisOperator.getServiceRule(service, EventType.RATE_LIMITING);
        Object ruleObject = serviceRule.getRule();
        if (null == ruleObject) {
            return invoker.invoke(invocation);
        }
        RateLimit rateLimit = (RateLimit) ruleObject;
        Set<MatchArgument> ratelimitLabels = ruleHandler.getRatelimitLabels(rateLimit);
        String method = invocation.getMethodName();
        Set<Argument> arguments = new HashSet<>();
        for (MatchArgument matchArgument : ratelimitLabels) {
            switch (matchArgument.getType()) {
                case HEADER:
                    String attachmentValue = RpcContext.getContext().getAttachment(matchArgument.getKey());
                    if (!StringUtils.isBlank(attachmentValue)) {
                        arguments.add(Argument.buildHeader(matchArgument.getKey(), attachmentValue));
                    }
                    break;
                case QUERY:
                    Object queryValue = ObjectParser
                            .parseArgumentsByExpression(matchArgument.getKey(), invocation.getArguments());
                    if (null != queryValue) {
                        arguments.add(Argument
                                .buildQuery(matchArgument.getKey(), String.valueOf(queryValue)));
                    }
                    break;
                default:
                    break;
            }
        }
        QuotaResponse quotaResponse = null;
        try {
            quotaResponse = polarisOperator.getQuota(service, method, arguments);
        } catch (RuntimeException e) {
            LOGGER.error("[POLARIS] get quota fail, {}", e.getMessage());
        }
        if (null != quotaResponse && quotaResponse.getCode() == QuotaResultCode.QuotaResultLimited) {
            // 请求被限流，则抛出异常
            return new AppResponse(new PolarisBlockException(quotaResponse.getInfo()));
        }
        return invoker.invoke(invocation);
    }
}
