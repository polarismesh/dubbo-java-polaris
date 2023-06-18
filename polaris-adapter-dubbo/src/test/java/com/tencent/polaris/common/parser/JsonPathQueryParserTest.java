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

import org.junit.Assert;
import org.junit.Test;
import shade.polaris.com.google.gson.Gson;

import java.util.Optional;

public class JsonPathQueryParserTest {

    private static final String TEST_STR_BOOK = "{\n" +
            "    \"book\":[\n" +
            "        {\n" +
            "            \"category\":\"reference\",\n" +
            "            \"author\":\"Nigel Rees\",\n" +
            "            \"title\":\"Sayings of the Century\",\n" +
            "            \"price\":8.95\n" +
            "        },\n" +
            "        {\n" +
            "            \"category\":\"fiction\",\n" +
            "            \"author\":\"Evelyn Waugh\",\n" +
            "            \"title\":\"Sword of Honor\",\n" +
            "            \"price\":12.99\n" +
            "        },\n" +
            "        {\n" +
            "            \"category\":\"fiction\",\n" +
            "            \"author\":\"Herman Melville\",\n" +
            "            \"title\":\"Moby Dick\",\n" +
            "            \"isbn\":\"0-553-21311-3\",\n" +
            "            \"price\":8.99\n" +
            "        },\n" +
            "        {\n" +
            "            \"category\":\"fiction\",\n" +
            "            \"author\":\"J. R. R. Tolkien\",\n" +
            "            \"title\":\"The Lord of the Rings\",\n" +
            "            \"isbn\":\"0-395-19395-8\",\n" +
            "            \"price\":22.99\n" +
            "        }\n" +
            "    ],\n" +
            "    \"bicycle\":{\n" +
            "        \"color\":\"red\",\n" +
            "        \"price\":19.95\n" +
            "    }\n" +
            "}";

    private static final String TEST_STR_BICYLE = "{\n" +
            "    \"color\":\"red\",\n" +
            "    \"price\":19.95\n" +
            "}";

    @Test
    public void test() {
        Object bootObj = new Gson().fromJson(TEST_STR_BOOK, Object.class);
        Object bicyleObj = new Gson().fromJson(TEST_STR_BICYLE, Object.class);

        JsonPathQueryParser parser = new JsonPathQueryParser();

        Optional<String> ret = parser.parse("param.$.book[0].category", new Object[]{bootObj});
        Assert.assertEquals("reference", ret.orElse(""));

        ret = parser.parse("param.$.book[0].category", new Object[]{bootObj, bicyleObj});
        Assert.assertEquals("reference", ret.orElse(""));

        ret = parser.parse("param[1].$.color", new Object[]{bootObj, bicyleObj});
        Assert.assertEquals("red", ret.orElse(""));

        ret = parser.parse("param.$.color", new Object[]{bicyleObj});
        Assert.assertEquals("red", ret.orElse(""));
    }
}