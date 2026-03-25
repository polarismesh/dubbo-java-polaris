/*
 * Tencent is pleased to support the open source community by making dubbo-polaris-java available.
 *
 * Copyright (C) 2021 Tencent. All rights reserved.
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

package com.tencent.polaris.common.metadata;

import com.tencent.polaris.metadata.core.MetadataContainer;
import com.tencent.polaris.metadata.core.MetadataStringValue;
import com.tencent.polaris.metadata.core.MetadataType;
import com.tencent.polaris.metadata.core.TransitiveType;
import com.tencent.polaris.metadata.core.manager.MetadataContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class MetadataProviderFilterTest {

    private final MetadataProviderFilter filter = new MetadataProviderFilter();

    private static String getStringValue(MetadataContainer container, String key) {
        MetadataStringValue val = container.getMetadataValue(key);
        return val != null ? val.getStringValue() : null;
    }

    @After
    public void after() {
        MetadataContextHolder.remove();
    }

    @Test
    public void testContextClearedAfterSuccessfulInvocation() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(invocation)).thenReturn(result);

        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("trace-id", "aaa-111", TransitiveType.PASS_THROUGH);

        MetadataContext ctxB = MetadataContextHolder.get();
        Assert.assertSame("context must be the same before cleanup", ctxA, ctxB);

        Result ret = filter.invoke(invoker, invocation);
        Assert.assertSame(result, ret);

        MetadataContext ctxC = MetadataContextHolder.get();
        Assert.assertNotSame("context must be renewed after cleanup", ctxA, ctxC);
        Assert.assertNull("old metadata must not leak",
                getStringValue(ctxC.getMetadataContainer(MetadataType.CUSTOM, false), "trace-id"));
    }

    @Test
    public void testContextClearedAfterException() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Invocation invocation = Mockito.mock(Invocation.class);
        Mockito.when(invoker.invoke(invocation)).thenThrow(new RpcException("boom"));

        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("trace-id", "err-222", TransitiveType.PASS_THROUGH);

        try {
            filter.invoke(invoker, invocation);
            Assert.fail("expected RpcException");
        } catch (RpcException ignored) {
        }

        MetadataContext ctxB = MetadataContextHolder.get();
        Assert.assertNotSame("context must be renewed after cleanup", ctxA, ctxB);
        Assert.assertNull("old metadata must not leak after exception",
                getStringValue(ctxB.getMetadataContainer(MetadataType.CUSTOM, false), "trace-id"));
    }

    @Test
    public void testThreadReuseNoPollution() {
        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        // Request A
        MetadataContext ctxA = MetadataContextHolder.get();
        ctxA.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("user-id", "user-A", TransitiveType.PASS_THROUGH);
        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Request B (same thread)
        MetadataContext ctxB = MetadataContextHolder.get();
        ctxB.getMetadataContainer(MetadataType.CUSTOM, false)
                .putMetadataStringValue("user-id", "user-B", TransitiveType.PASS_THROUGH);
        filter.invoke(invoker, Mockito.mock(Invocation.class));

        // Request C — should be completely clean
        MetadataContext ctxC = MetadataContextHolder.get();
        MetadataContainer containerC = ctxC.getMetadataContainer(MetadataType.CUSTOM, false);

        Assert.assertNotSame(ctxA, ctxC);
        Assert.assertNotSame(ctxB, ctxC);
        Assert.assertNull("no leftover from any previous request",
                getStringValue(containerC, "user-id"));
    }

    /**
     * Simulates concurrent requests on a thread pool, each writing a unique trace-id.
     * Verifies thread-pool reuse cleanup works correctly under concurrent pressure.
     */
    @Test
    public void testConcurrentRequestsNoContextCrossTalk() throws Exception {
        int threadCount = 10;
        int iterations = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        // mock 对象提到循环外，避免 Mockito 全局锁序列化线程执行
        Invoker<?> sharedInvoker = Mockito.mock(Invoker.class);
        Result sharedResult = Mockito.mock(Result.class);
        Mockito.when(sharedInvoker.invoke(Mockito.any())).thenReturn(sharedResult);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final String threadTag = "thread-" + t;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(); // 所有线程同时启动
                    for (int i = 0; i < iterations; i++) {
                        String uniqueId = threadTag + "-iter-" + i;

                        // 模拟：写入请求级别元数据 → filter invoke → 验证清理
                        MetadataContext ctx = MetadataContextHolder.get();
                        MetadataContainer container = ctx.getMetadataContainer(MetadataType.CUSTOM, false);
                        container.putMetadataStringValue("req-id", uniqueId, TransitiveType.PASS_THROUGH);

                        // 验证调用前数据正确
                        String valueBefore = getStringValue(container, "req-id");
                        if (!uniqueId.equals(valueBefore)) {
                            errors.add(threadTag + ": before invoke expected [" + uniqueId
                                    + "] but got [" + valueBefore + "]");
                            return;
                        }
                        if (ctx != MetadataContextHolder.get()) {
                            errors.add(threadTag + " iter " + i
                                    + ": context identity changed unexpectedly");
                            return;
                        }

                        // 调用 filter — 应在 finally 中清理
                        filter.invoke(sharedInvoker, Mockito.mock(Invocation.class));

                        // filter 之后，上下文必须是全新的，无残留
                        MetadataContext ctxAfter = MetadataContextHolder.get();
                        if (ctx == ctxAfter) {
                            errors.add(threadTag + " iter " + i
                                    + ": context not renewed after filter");
                            return;
                        }
                        String valueAfter = getStringValue(
                                ctxAfter.getMetadataContainer(MetadataType.CUSTOM, false), "req-id");
                        if (valueAfter != null) {
                            errors.add(threadTag + " iter " + i
                                    + ": leaked req-id [" + valueAfter + "] after cleanup");
                            return;
                        }
                    }
                } catch (Exception e) {
                    errors.add(threadTag + ": unexpected exception " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        Assert.assertTrue("concurrent context errors: " + errors, errors.isEmpty());
    }

    /**
     * 真正的并发竞态测试：使用精确的线程交错控制，验证两个线程在同一线程池线程上
     * 交替处理请求时，前一个请求的元数据不会泄漏到后一个请求。
     *
     * <p>场景：单线程线程池中顺序提交两个任务，第一个任务写入 metadata 并通过 filter
     * 清理，第二个任务必须看到干净的上下文。使用 CountDownLatch 确保精确的执行顺序。</p>
     */
    @Test
    public void testSequentialReuseOnSamePoolThread() throws Exception {
        // 单线程池 —— 保证两个任务在同一个线程上执行
        ExecutorService singleThread = Executors.newFixedThreadPool(1);
        List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        AtomicReference<String> threadNameA = new AtomicReference<>();
        AtomicReference<String> threadNameB = new AtomicReference<>();

        // 请求 A：写入 metadata，通过 filter 清理
        Future<?> taskA = singleThread.submit(() -> {
            threadNameA.set(Thread.currentThread().getName());
            MetadataContext ctx = MetadataContextHolder.get();
            ctx.getMetadataContainer(MetadataType.CUSTOM, false)
                    .putMetadataStringValue("secret", "request-A-secret", TransitiveType.PASS_THROUGH);
            ctx.getMetadataContainer(MetadataType.CUSTOM, false)
                    .putMetadataStringValue("user-token", "token-A-123", TransitiveType.PASS_THROUGH);
            filter.invoke(invoker, Mockito.mock(Invocation.class));
        });
        taskA.get(); // 等待 A 完成

        // 请求 B：同一线程，验证不应看到 A 的数据
        Future<?> taskB = singleThread.submit(() -> {
            threadNameB.set(Thread.currentThread().getName());
            MetadataContext ctx = MetadataContextHolder.get();
            MetadataContainer container = ctx.getMetadataContainer(MetadataType.CUSTOM, false);

            String secret = getStringValue(container, "secret");
            if (secret != null) {
                errors.add("request B leaked 'secret' from A: [" + secret + "]");
            }
            String token = getStringValue(container, "user-token");
            if (token != null) {
                errors.add("request B leaked 'user-token' from A: [" + token + "]");
            }
            // B 自己写入的数据应该只有 B 的
            container.putMetadataStringValue("secret", "request-B-secret", TransitiveType.PASS_THROUGH);
            String mySecret = getStringValue(container, "secret");
            if (!"request-B-secret".equals(mySecret)) {
                errors.add("request B own data corrupted: expected [request-B-secret] got [" + mySecret + "]");
            }
            filter.invoke(invoker, Mockito.mock(Invocation.class));
        });
        taskB.get();

        singleThread.shutdown();

        // 确认两个任务确实跑在同一个线程上
        Assert.assertEquals("tasks must run on the same thread",
                threadNameA.get(), threadNameB.get());
        Assert.assertTrue("sequential reuse errors: " + errors, errors.isEmpty());
    }

    /**
     * 并发竞态测试：两个线程精确交错执行，验证线程 A 在处理中途时，线程 B 不会读到 A 的数据。
     *
     * <p>时序如下：</p>
     * <pre>
     *   Thread-A: 写入 metadata("owner"="A") → 通知 B 开始 → 等待 B 读完 → filter.invoke() 清理
     *   Thread-B: 等待 A 写完 → 读取自己的 metadata → 验证看不到 A 的数据 → 通知 A 继续
     * </pre>
     *
     * <p>如果 ThreadLocal 隔离失效或存在共享状态，B 会读到 A 写入的 "owner"="A"。</p>
     */
    @Test
    public void testInterleavedConcurrentAccess() throws Exception {
        // 精确控制两个线程的交错时序
        CountDownLatch aHasWritten = new CountDownLatch(1);  // A 写完通知 B
        CountDownLatch bHasRead = new CountDownLatch(1);     // B 读完通知 A

        List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        // 线程 A：写入 metadata，等 B 读完后再清理
        Thread threadA = new Thread(() -> {
            try {
                MetadataContext ctxA = MetadataContextHolder.get();
                MetadataContainer containerA = ctxA.getMetadataContainer(MetadataType.CUSTOM, false);
                containerA.putMetadataStringValue("owner", "A", TransitiveType.PASS_THROUGH);
                containerA.putMetadataStringValue("sensitive-data", "A-secret-payload", TransitiveType.PASS_THROUGH);

                // 通知 B：A 已经写入数据了
                aHasWritten.countDown();

                // 等 B 读完再清理
                bHasRead.await();

                // 此时 A 的数据仍应完整
                String ownerCheck = getStringValue(containerA, "owner");
                if (!"A".equals(ownerCheck)) {
                    errors.add("Thread-A: own data corrupted, expected [A] got [" + ownerCheck + "]");
                }

                filter.invoke(invoker, Mockito.mock(Invocation.class));
            } catch (Exception e) {
                errors.add("Thread-A: exception " + e.getMessage());
            }
        }, "interleave-A");

        // 线程 B：等 A 写完后，读取自己的 context，不应看到 A 的数据
        Thread threadB = new Thread(() -> {
            try {
                // 等 A 写完
                aHasWritten.await();

                MetadataContext ctxB = MetadataContextHolder.get();
                MetadataContainer containerB = ctxB.getMetadataContainer(MetadataType.CUSTOM, false);

                // 关键验证：B 不应看到 A 写入的任何数据
                String owner = getStringValue(containerB, "owner");
                if (owner != null) {
                    errors.add("Thread-B: saw Thread-A's 'owner' value [" + owner + "] — cross-thread leak!");
                }
                String sensitiveData = getStringValue(containerB, "sensitive-data");
                if (sensitiveData != null) {
                    errors.add("Thread-B: saw Thread-A's 'sensitive-data' [" + sensitiveData + "] — cross-thread leak!");
                }

                // B 写入自己的数据，不应影响 A
                containerB.putMetadataStringValue("owner", "B", TransitiveType.PASS_THROUGH);

                // 通知 A：B 已经读完了
                bHasRead.countDown();

                filter.invoke(invoker, Mockito.mock(Invocation.class));
            } catch (Exception e) {
                errors.add("Thread-B: exception " + e.getMessage());
            }
        }, "interleave-B");

        threadA.start();
        threadB.start();
        threadA.join(5000);
        threadB.join(5000);

        Assert.assertFalse("Thread-A should have finished", threadA.isAlive());
        Assert.assertFalse("Thread-B should have finished", threadB.isAlive());
        Assert.assertTrue("interleaved access errors: " + errors, errors.isEmpty());
    }

    /**
     * 高压并发竞态测试：多线程同时写入和读取各自的 MetadataContext，
     * 验证在高并发写入的同时不会出现跨线程数据可见性问题。
     *
     * <p>与 testConcurrentRequestsNoContextCrossTalk 的区别：
     * 使用 CyclicBarrier 在每一轮迭代中对齐所有线程，确保写入操作真正并发执行，
     * 而非各线程独立地顺序执行。</p>
     */
    @Test
    public void testHighContentionConcurrentWriteAndRead() throws Exception {
        int threadCount = 10;
        int rounds = 50;
        List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        Invoker<?> invoker = Mockito.mock(Invoker.class);
        Result result = Mockito.mock(Result.class);
        Mockito.when(invoker.invoke(Mockito.any())).thenReturn(result);

        for (int round = 0; round < rounds; round++) {
            // 每轮都重新对齐，确保写入操作真正并发
            CyclicBarrier writeBarrier = new CyclicBarrier(threadCount);
            CyclicBarrier readBarrier = new CyclicBarrier(threadCount);
            CyclicBarrier cleanupBarrier = new CyclicBarrier(threadCount);

            Thread[] threads = new Thread[threadCount];
            final int r = round;

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                threads[t] = new Thread(() -> {
                    try {
                        String myValue = "t" + threadIdx + "-r" + r;

                        // 阶段1：所有线程同时写入各自的 metadata
                        writeBarrier.await();
                        MetadataContext ctx = MetadataContextHolder.get();
                        MetadataContainer container = ctx.getMetadataContainer(MetadataType.CUSTOM, false);
                        container.putMetadataStringValue("owner", myValue, TransitiveType.PASS_THROUGH);

                        // 阶段2：所有线程同时读取并验证
                        readBarrier.await();
                        String readValue = getStringValue(container, "owner");
                        if (!myValue.equals(readValue)) {
                            errors.add("round " + r + " thread " + threadIdx
                                    + ": expected [" + myValue + "] but read [" + readValue
                                    + "] — cross-thread data corruption!");
                        }

                        // 阶段3：所有线程同时执行 filter 清理
                        cleanupBarrier.await();
                        filter.invoke(invoker, Mockito.mock(Invocation.class));

                        // 验证清理后是干净的
                        MetadataContext freshCtx = MetadataContextHolder.get();
                        String afterCleanup = getStringValue(
                                freshCtx.getMetadataContainer(MetadataType.CUSTOM, false), "owner");
                        if (afterCleanup != null) {
                            errors.add("round " + r + " thread " + threadIdx
                                    + ": leaked 'owner' [" + afterCleanup + "] after cleanup");
                        }
                    } catch (Exception e) {
                        errors.add("thread " + threadIdx + " round " + r
                                + ": exception " + e.getMessage());
                    }
                }, "contention-t" + t + "-r" + round);
            }

            for (Thread th : threads) {
                th.start();
            }
            for (Thread th : threads) {
                th.join(5000);
                Assert.assertFalse("thread should have finished: " + th.getName(), th.isAlive());
            }
        }

        Assert.assertTrue("high contention errors: " + errors, errors.isEmpty());
    }
}
