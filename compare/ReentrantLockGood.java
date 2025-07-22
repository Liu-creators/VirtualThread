package compare;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

public class ReentrantLockGood {

    private static final int TASK_COUNT = 2000;
    private static final int LOCK_COUNT = 100;
    private static final ReentrantLock[] locks = new ReentrantLock[LOCK_COUNT];

    static {
        for (int i = 0; i < LOCK_COUNT; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("信息：此测试将展示 ReentrantLock 如何避免线程固定，执行会很快。");
        System.out.printf("总任务数: %d, 总锁数: %d\n", TASK_COUNT, LOCK_COUNT);
        System.out.println("预期：虚拟线程在sleep时会释放平台线程，实际并发数约等于锁的数量，总耗时 ≈ (任务数 / 锁数) * 1秒\n");

        var latch = new CountDownLatch(TASK_COUNT);
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, TASK_COUNT).forEach(i -> {
                executor.submit(() -> {
                    ReentrantLock lock = locks[i % LOCK_COUNT];
                    lock.lock();
                    try {
                        // 模拟持有锁执行I/O操作
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    } finally {
                        lock.unlock();
                    }
                    latch.countDown();
                });
            });

            latch.await();
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("✅ ReentrantLock 测试完成。耗时：%d 毫秒\n", (endTime - startTime));
    }
}