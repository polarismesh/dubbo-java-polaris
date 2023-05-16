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

package com.tencent.polaris.common.router;

import com.tencent.polaris.api.utils.CollectionUtils;

import com.tencent.polaris.common.registry.TimedCache;
import com.tencent.polaris.specification.api.v1.model.ModelProto;
import com.tencent.polaris.specification.api.v1.traffic.manage.RateLimitProto;
import com.tencent.polaris.specification.api.v1.traffic.manage.RoutingProto;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RuleHandler {

    private final Map<String, TimedCache<Set<String>>> routeRuleMatchLabels = new ConcurrentHashMap<>();

    private final Map<String, TimedCache<Set<RateLimitProto.MatchArgument>>> ratelimitArguments = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public Set<String> getRouteLabels(RoutingProto.Routing routing) {
        TimedCache<Set<String>> setTimedCache = routeRuleMatchLabels.get(routing.getRevision().getValue());
        if (null != setTimedCache && !setTimedCache.isExpired()) {
            return setTimedCache.getValue();
        }
        synchronized (lock) {
            setTimedCache = routeRuleMatchLabels.get(routing.getRevision().getValue());
            if (null != setTimedCache && !setTimedCache.isExpired()) {
                return setTimedCache.getValue();
            }
            TimedCache<Set<String>> timedCache = new TimedCache<>(buildRouteLabels(routing));
            routeRuleMatchLabels.put(routing.getRevision().getValue(), timedCache);
            return timedCache.getValue();
        }
    }

    private static Set<String> buildRouteLabels(RoutingProto.Routing routing) {
        Set<String> labels = new HashSet<>();
        List<RoutingProto.Route> inboundsList = routing.getInboundsList();
        List<RoutingProto.Route> outboundsList = routing.getOutboundsList();
        routeRulesToLabels(inboundsList, labels);
        routeRulesToLabels(outboundsList, labels);
        return labels;
    }

    private static void routeRulesToLabels(List<RoutingProto.Route> routes, Collection<String> labels) {
        if (CollectionUtils.isEmpty(routes)) {
            return;
        }
        for (RoutingProto.Route route : routes) {
            List<RoutingProto.Source> sourcesList = route.getSourcesList();
            if (CollectionUtils.isEmpty(sourcesList)) {
                continue;
            }
            for (RoutingProto.Source source : sourcesList) {
                Map<String, ModelProto.MatchString> metadataMap = source.getMetadataMap();
                labels.addAll(metadataMap.keySet());
            }
        }
    }

    private static Set<RateLimitProto.MatchArgument> buildRatelimitLabels(RateLimitProto.RateLimit rateLimit) {
        List<RateLimitProto.Rule> rulesList = rateLimit.getRulesList();
        Set<RateLimitProto.MatchArgument> arguments = new HashSet<>();
        for (RateLimitProto.Rule rule : rulesList) {
            List<RateLimitProto.MatchArgument> argumentsList = rule.getArgumentsList();
            for (RateLimitProto.MatchArgument matchArgument : argumentsList) {
                arguments.add(RateLimitProto.MatchArgument.newBuilder().setType(matchArgument.getType()).setKey(matchArgument.getKey())
                        .build());
            }
        }
        return arguments;
    }

    public Set<RateLimitProto.MatchArgument> getRatelimitLabels(RateLimitProto.RateLimit rateLimit) {
        TimedCache<Set<RateLimitProto.MatchArgument>> setTimedCache = ratelimitArguments.get(rateLimit.getRevision().getValue());
        if (null != setTimedCache && !setTimedCache.isExpired()) {
            return setTimedCache.getValue();
        }
        synchronized (lock) {
            setTimedCache = ratelimitArguments.get(rateLimit.getRevision().getValue());
            if (null != setTimedCache && !setTimedCache.isExpired()) {
                return setTimedCache.getValue();
            }
            TimedCache<Set<RateLimitProto.MatchArgument>> timedCache = new TimedCache<>(buildRatelimitLabels(rateLimit));
            ratelimitArguments.put(rateLimit.getRevision().getValue(), timedCache);
            return timedCache.getValue();
        }
    }
}
