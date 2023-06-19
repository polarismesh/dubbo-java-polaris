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

package com.tencent.polaris.common.parser;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import shade.polaris.com.google.gson.Gson;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

class JsonPathQueryParser implements QueryParser {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("^.+\\[[0-9]+\\]");

    private static final String PREFIX_PARAM = "param";

    private static final String PREFIX_PARAM_ARRAY = "param[";


    @Override
    public String name() {
        return "JsonPath";
    }

    @Override
    public Optional<String> parse(String query, Object[] parameters) {
        if (Objects.isNull(parameters) || parameters.length == 0) {
            return Optional.empty();
        }
        Index index = resolveIndex(query);
        if (index.index == -1 || index.index > parameters.length) {
            return Optional.empty();
        }
        String str = new Gson().toJson(parameters[index.index]);
        ReadContext ctx = JsonPath.parse(str);
        Object value = ctx.read(index.key, Object.class);
        if (Objects.isNull(value)) {
            return Optional.empty();
        }
        return Optional.of(Objects.toString(value));
    }

    private Index resolveIndex(String key) {
        if (key.startsWith(PREFIX_PARAM_ARRAY)) {
            int endIndex = key.indexOf("]");
            String indexStr = key.substring(PREFIX_PARAM_ARRAY.length(), endIndex);
            int index = Integer.parseInt(indexStr);
            int startIndex = endIndex + 2;
            if (!Objects.equals(key.charAt(endIndex+1), '.')) {
                startIndex = endIndex + 1;
            }
            return new Index(index, key.substring(startIndex));
        }
        if (key.startsWith(PREFIX_PARAM)) {
            return new Index(0, key.replace(PREFIX_PARAM + ".", ""));
        }
        return EMPTY_INDEX;
    }

    private static final Index EMPTY_INDEX = new Index(-1, "");

    private static final class Index {
        private final int index;

        private final String key;

        public Index(int index, String key) {
            this.index = index;
            this.key = key;
        }
    }
}
