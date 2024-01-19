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

package com.tencent.polaris.dubbox.ratelimit;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.exception.PolarisBlockException;
import com.tencent.polaris.common.parser.QueryParser;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperatorDelegate;
import com.tencent.polaris.common.router.RuleHandler;
import com.tencent.polaris.ratelimit.api.rpc.Argument;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResponse;
import com.tencent.polaris.ratelimit.api.rpc.QuotaResultCode;
import com.tencent.polaris.specification.api.v1.traffic.manage.RateLimitProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Activate(group = Constants.PROVIDER)
public class RateLimitFilter extends PolarisOperatorDelegate implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RuleHandler ruleHandler;

    private final QueryParser parser;

    public RateLimitFilter() {
        LOGGER.info("[POLARIS] init polaris ratelimit");
        ruleHandler = new RuleHandler();
        parser = QueryParser.load();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String service = invoker.getInterface().getName();
        PolarisOperator polarisOperator = getPolarisOperator();
        if (null == polarisOperator) {
            return invoker.invoke(invocation);
        }
        ServiceRule serviceRule = polarisOperator.getServiceRule(service, EventType.RATE_LIMITING);
        Object ruleObject = serviceRule.getRule();
        if (null == ruleObject) {
            return invoker.invoke(invocation);
        }
        RateLimitProto.RateLimit rateLimit = (RateLimitProto.RateLimit) ruleObject;
        Set<RateLimitProto.MatchArgument> ratelimitLabels = ruleHandler.getRatelimitLabels(rateLimit);
        String method = invocation.getMethodName();
        Set<Argument> arguments = new HashSet<>();
        for (RateLimitProto.MatchArgument matchArgument : ratelimitLabels) {
            switch (matchArgument.getType()) {
                case HEADER:
                    String attachmentValue = RpcContext.getContext().getAttachment(matchArgument.getKey());
                    if (!StringUtils.isBlank(attachmentValue)) {
                        arguments.add(Argument.buildHeader(matchArgument.getKey(), attachmentValue));
                    }
                    break;
                case QUERY:
                    Optional<String> queryValue = parser.parse(matchArgument.getKey(), invocation.getArguments());
                    queryValue.ifPresent(value -> arguments.add(Argument.buildQuery(matchArgument.getKey(), value)));
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
            throw new RpcException(new PolarisBlockException(
                    String.format("url=%s, info=%s", invoker.getUrl(), quotaResponse.getInfo())));
        }
        return invoker.invoke(invocation);
    }
}