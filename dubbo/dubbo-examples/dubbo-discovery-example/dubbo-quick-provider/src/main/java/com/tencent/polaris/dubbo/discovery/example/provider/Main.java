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

package com.tencent.polaris.dubbo.discovery.example.provider;

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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    private static final int LISTEN_PORT = 15700;

    private static final String PATH = "/reload";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(LISTEN_PORT), 0);
        server.createContext(PATH, new EchoClientHandler());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
        }));
        server.start();
    }

    @Configuration
    @EnableDubbo(scanBasePackages = "com.tencent.polaris.dubbo.discovery.example.provider")
    @PropertySource("classpath:/spring/dubbo-provider.properties")
    @ComponentScan(value = {"com.tencent.polaris.dubbo.discovery.example.provider"})
    static class ConsumerConfiguration {

    }

    private static class EchoClientHandler implements HttpHandler {

        private final Object mutex = new Object();

        private AnnotationConfigApplicationContext context;

        public EchoClientHandler() {
            int delay = 0;
            if (!Objects.equals(System.getenv("DELAY_REGISTER"), "")) {
                delay = Integer.parseInt(System.getenv("DELAY_REGISTER"));
            }
            synchronized (mutex) {
                try {
                    TimeUnit.MINUTES.sleep(delay);
                    context = new AnnotationConfigApplicationContext(ConsumerConfiguration.class);
                    context.start();
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            synchronized (mutex) {
               try {
                   context.destroy();
                   TimeUnit.MINUTES.sleep(1);
                   context = new AnnotationConfigApplicationContext(ConsumerConfiguration.class);
                   context.start();
               } catch (Exception e) {
                   e.printStackTrace();
               }
            }
        }

    }

}