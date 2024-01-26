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

package com.tencent.polaris.dubbo.router;

import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.RouteArgument;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.common.parser.QueryParser;
import com.tencent.polaris.common.registry.DubboServiceInfo;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.router.RuleHandler;
import com.tencent.polaris.common.utils.DubboUtils;
import com.tencent.polaris.specification.api.v1.traffic.manage.RoutingProto;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.AbstractRouter;
import org.apache.dubbo.rpc.cluster.router.RouterResult;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class PolarisRouter extends AbstractRouter implements ScopeModelAware {

    protected final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(getClass());

    private final RuleHandler routeRuleHandler;

    private final PolarisOperator operator;

    private final QueryParser parser;

    private ApplicationModel applicationModel;

    public PolarisRouter(URL url) {
        super(url);
        logger.info(String.format("[POLARIS] init service router, url is %s, parameters are %s", url,
                url.getParameters()));
        this.routeRuleHandler = new RuleHandler();
        this.operator = PolarisOperators.getGovernancePolarisOperator();
        this.parser = QueryParser.load();
    }

    @Override
    public <T> RouterResult<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation, boolean needToPrintMessage) throws RpcException {
        if (CollectionUtils.isEmpty(invokers) || Objects.isNull(operator)) {
            return new RouterResult<>(invokers);
        }
        List<DubboServiceInfo> serviceInfos = DubboUtils.analyzeRemoteDubboServiceInfo(url, invocation);
        for (DubboServiceInfo serviceInfo : serviceInfos) {
           RouterResult<Invoker<T>> result = realRoute(invokers, url, invocation, serviceInfo);
           if (!result.getResult().isEmpty()) {
               return result;
           }
        }
        return new RouterResult<>(invokers);
    }

    @SuppressWarnings("unchecked")
    public <T> RouterResult<Invoker<T>> realRoute(List<Invoker<T>> invokers, URL url, Invocation invocation, DubboServiceInfo serviceInfo) {
        List<Instance> instances = new ArrayList<>(invokers.size());
        for (Invoker<T> invoker : invokers) {
            instances.add(new InstanceInvoker<>(invoker, null, operator.getPolarisConfig().getNamespace()));
        }
        ServiceRule serviceRule = operator.getServiceRule(serviceInfo.getService(), EventType.ROUTING);
        Object ruleObject = serviceRule.getRule();
        if (Objects.isNull(ruleObject)) {
            return new RouterResult<>(invokers);
        }
        Set<RouteArgument> arguments = new HashSet<>();
        RoutingProto.Routing routing = (RoutingProto.Routing) ruleObject;
        Set<String> routeLabels = routeRuleHandler.getRouteLabels(routing);
        for (String routeLabel : routeLabels) {
            if (StringUtils.equals(RouteArgument.LABEL_KEY_PATH, routeLabel)) {
                arguments.add(RouteArgument.buildPath(invocation.getMethodName()));
            } else if (routeLabel.startsWith(RouteArgument.LABEL_KEY_HEADER)) {
                String headerName = routeLabel.substring(RouteArgument.LABEL_KEY_HEADER.length());
                String value = RpcContext.getClientAttachment().getAttachment(headerName);
                if (!StringUtils.isBlank(value)) {
                    arguments.add(RouteArgument.buildHeader(headerName, value));
                }
            } else if (routeLabel.startsWith(RouteArgument.LABEL_KEY_QUERY)) {
                String queryName = routeLabel.substring(RouteArgument.LABEL_KEY_QUERY.length());
                if (!StringUtils.isBlank(queryName)) {
                    Optional<String> value = parser.parse(queryName, invocation.getArguments());
                    value.ifPresent(s -> arguments.add(RouteArgument.buildQuery(queryName, s)));
                }
            }
        }
        logger.debug(String.format("[POLARIS] list service(%s), method(%s), labels(%s), url(%s)", serviceInfo.getService(),
                invocation.getMethodName(), arguments, url));
        List<Instance> resultInstances = operator.route(serviceInfo.getService(), invocation.getMethodName(), arguments, instances);
        return new RouterResult<>((List<Invoker<T>>) ((List<?>) resultInstances));
    }

    @Override
    public void setApplicationModel(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
    }
}
