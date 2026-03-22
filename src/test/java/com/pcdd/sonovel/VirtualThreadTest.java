package com.pcdd.sonovel;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class VirtualThreadTest {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadTest.class);
    private static final int MAX_CONCURRENT = 50; // 限制最大并发 50
    private static final Semaphore SEMAPHORE = new Semaphore(MAX_CONCURRENT);

    @Test
    void testLimiter() {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 1; i <= 200; i++) {
            int taskId = i;
            executor.submit(() -> {
                try {
                    SEMAPHORE.acquire();
                    log.info("执行任务 {}，线程: {}", taskId, Thread.currentThread());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    SEMAPHORE.release();
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testStartTime() throws Exception {
        int total = 20000;
        int poolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(total);
        for (int i = 0; i < total; i++) {
            executor.execute(latch::countDown);
        }
        latch.await();
        executor.shutdown();
    }

}
