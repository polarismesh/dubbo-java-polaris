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

package com.tencent.polaris.dubbo.router.front.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    private static final int LISTEN_PORT = 15700;

    private static final String PATH = "/echo";

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConsumerConfiguration.class);
        context.start();
        MiddleConsumer greetingServiceConsumer = context.getBean(MiddleConsumer.class);

        HttpServer server = HttpServer.create(new InetSocketAddress(LISTEN_PORT), 0);
        server.createContext(PATH, new EchoClientHandler(greetingServiceConsumer));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
        }));
        server.start();
    }

    @Configuration
    @EnableDubbo(scanBasePackages = "com.tencent.polaris.dubbo.router.front.example")
    @PropertySource("classpath:/spring/dubbo.properties")
    @ComponentScan(value = {"com.tencent.polaris.dubbo.router.front.example"})
    static class ConsumerConfiguration {

    }

    private static class EchoClientHandler implements HttpHandler {

        private final MiddleConsumer consumer;

        public EchoClientHandler(MiddleConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> parameters = splitQuery(exchange.getRequestURI());
            String echoValue = parameters.get("value");
            String method = parameters.get("method");
            String response = "";
            switch (method) {
                case "sayHello":
                    response = consumer.sayHello(echoValue);
                    break;
                case "sayHi":
                    response = consumer.sayHi(echoValue);
                    break;
            }
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
            Map<String, String> query_pairs = new LinkedHashMap<>();
            String query = uri.getQuery();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return query_pairs;
        }
    }
}