package compare;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class SynchronizedPinningBad {

    private static final int TASK_COUNT = 2000;
    private static final int LOCK_COUNT = 100; // 有100个不同的锁
    private static final Object[] locks = new Object[LOCK_COUNT];

    static {
        for (int i = 0; i < LOCK_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // 获取可用的平台线程数（通常等于CPU核心数）
        int platformThreadCount = Runtime.getRuntime().availableProcessors();
        System.out.println("警告：此测试将演示线程固定问题，执行会非常缓慢。");
        System.out.println("平台线程（CPU核心）数量: " + platformThreadCount);
        System.out.printf("总任务数: %d, 总锁数: %d\n", TASK_COUNT, LOCK_COUNT);
        System.out.println("预期：由于平台线程被固定，实际并发数约等于核心数，总耗时 ≈ (任务数 / 核心数) * 1秒\n");

        var latch = new CountDownLatch(TASK_COUNT);
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, TASK_COUNT).forEach(i -> {
                executor.submit(() -> {
                    // 随机选择一个锁
                    Object lock = locks[i % LOCK_COUNT];
                    synchronized (lock) {
                        try {
                            // 模拟持有锁执行I/O操作
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    latch.countDown();
                });
            });

            latch.await(); // 等待所有任务完成
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("✅ synchronized 测试完成。耗时：%d 毫秒\n", (endTime - startTime));
    }
}