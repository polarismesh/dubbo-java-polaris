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


package com.tencent.polaris.dubbox.router;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Router;
import com.alibaba.dubbo.rpc.cluster.router.script.ScriptRouter;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.RouteArgument;
import com.tencent.polaris.api.pojo.ServiceEventKey.EventType;
import com.tencent.polaris.api.pojo.ServiceRule;
import com.tencent.polaris.api.utils.StringUtils;
import com.tencent.polaris.client.pb.RoutingProto.Routing;
import com.tencent.polaris.common.registry.PolarisOperator;
import com.tencent.polaris.common.registry.PolarisOperators;
import com.tencent.polaris.common.router.ObjectParser;
import com.tencent.polaris.common.router.RuleHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolarisRouter implements Router {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisRouter.class);

    private final RuleHandler routeRuleHandler;

    private final PolarisOperator polarisOperator;

    private final URL url;

    private final int priority;

    public PolarisRouter(URL url) {
        super();
        LOGGER.info("[POLARIS] init service router, url is {}, parameters are {}", url,
                url.getParameters());
        this.url = url;
        this.priority = url.getParameter(Constants.PRIORITY_KEY, 0);
        routeRuleHandler = new RuleHandler();
        polarisOperator = PolarisOperators.INSTANCE.getPolarisOperator(url.getHost(), url.getPort());
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        if (null == invokers || invokers.size() == 0) {
            return invokers;
        }
        if (null == polarisOperator) {
            return invokers;
        }
        List<Instance> instances;
        if (invokers.get(0) instanceof Instance) {
            instances = (List<Instance>) ((List<?>) invokers);
        } else {
            instances = new ArrayList<>();
            for (Invoker<T> invoker : invokers) {
                instances.add(new InstanceInvoker<>(invoker, polarisOperator.getPolarisConfig().getNamespace()));
            }
        }

        String service = url.getServiceInterface();
        ServiceRule serviceRule = polarisOperator.getServiceRule(service, EventType.ROUTING);
        Object ruleObject = serviceRule.getRule();
        Set<RouteArgument> arguments = new HashSet<>();
        if (null != ruleObject) {
            Routing routing = (Routing) ruleObject;
            Set<String> routeLabels = routeRuleHandler.getRouteLabels(routing);
            for (String routeLabel : routeLabels) {
                if (StringUtils.equals(RouteArgument.LABEL_KEY_PATH, routeLabel)) {
                    arguments.add(RouteArgument.buildPath(invocation.getMethodName()));
                } else if (routeLabel.startsWith(RouteArgument.LABEL_KEY_HEADER)) {
                    String headerName = routeLabel.substring(RouteArgument.LABEL_KEY_HEADER.length());
                    String value = RpcContext.getContext().getAttachment(headerName);
                    if (!StringUtils.isBlank(value)) {
                        arguments.add(RouteArgument.buildHeader(headerName, value));
                    }
                } else if (routeLabel.startsWith(RouteArgument.LABEL_KEY_QUERY)) {
                    String queryName = routeLabel.substring(RouteArgument.LABEL_KEY_QUERY.length());
                    if (!StringUtils.isBlank(queryName)) {
                        Object value = ObjectParser.parseArgumentsByExpression(queryName, invocation.getArguments());
                        if (null != value) {
                            arguments.add(RouteArgument.buildQuery(queryName, String.valueOf(value)));
                        }
                    }
                }
            }
        }
        LOGGER.debug("[POLARIS] list service {}, method {}, labels {}, url {}", service,
                invocation.getMethodName(), arguments, url);
        List<Instance> resultInstances = polarisOperator
                .route(service, invocation.getMethodName(), arguments, instances);
        return (List<Invoker<T>>) ((List<?>) resultInstances);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(Router o) {
        if (o == null || o.getClass() != ScriptRouter.class) {
            return 1;
        }
        PolarisRouter c = (PolarisRouter) o;
        return this.priority > c.priority ? 1 : -1;
    }
}
