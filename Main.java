/**
 * Java虚拟线程示例
 * 本类展示了Java 21中的虚拟线程(Virtual Thread)使用方法
 * 虚拟线程是轻量级线程，不会1:1映射到操作系统线程，适合I/O密集型应用
 * 
 * 注意：本示例包含对ThreadLocal与虚拟线程内存影响的测试
 */
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.NumberFormat;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class Main {
    /**
     * 程序入口
     * 可以在这里调用ofExecutors()和ofVirtual()方法来测试不同的虚拟线程创建方式
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        try {
            System.out.println("===使用ExecutorService创建虚拟线程===");
            ofExecutors();
            
            System.out.println("\n===直接使用Thread.ofVirtual()创建虚拟线程===");
            ofVirtual();
            
            // 可以取消注释下面的代码来运行ThreadLocal测试
            // 警告：这将创建大量线程，请确保你的系统有足够的内存
            // System.out.println("\n===测试ThreadLocal在大量虚拟线程中的内存影响===");
             testThreadLocalMemoryImpact(1_000_00); // 创建100万个虚拟线程
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 演示使用ExecutorService创建虚拟线程的方法
     * 
     * Executors.newVirtualThreadPerTaskExecutor()是Java 21提供的工厂方法
     * 它创建一个ExecutorService，为每个任务分配一个新的虚拟线程
     * 这种方式适合大量短期任务的场景，尤其是I/O密集型任务
     * 
     * @throws ExecutionException 如果任务执行过程中发生异常
     * @throws InterruptedException 如果线程被中断
     */
    private static void ofExecutors() throws ExecutionException, InterruptedException {
        // 使用try-with-resources自动关闭ExecutorService
        try (ExecutorService myExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 提交一个简单任务到执行器
            Future<?> future = myExecutor.submit(() -> 
                System.out.println("Running in virtual thread: " + Thread.currentThread()));
            
            // 等待任务完成
            future.get();
            System.out.println("Task completed");
            // ExecutorService会在try块结束时自动关闭
        }
    }

    /**
     * 演示直接使用Thread.ofVirtual()创建虚拟线程的方法
     * 
     * Thread.ofVirtual()返回一个Thread.Builder，可用于配置和启动虚拟线程
     * 这种方式提供了更细粒度的控制，如命名、设置初始栈大小等
     * 与平台线程相比，虚拟线程更轻量，可以创建数百万个而不会耗尽系统资源
     * 
     * @throws InterruptedException 如果线程被中断
     */
    private static void ofVirtual() throws InterruptedException {
        // 创建虚拟线程构建器，设置线程名前缀为"worker-"，从索引0开始
        Thread.Builder builder = Thread.ofVirtual().name("worker-", 0);
        
        // 定义线程要执行的任务
        Runnable task = () -> {
            // 打印当前线程ID，展示这确实是在不同的线程中执行
            System.out.println("Thread ID: " + Thread.currentThread().threadId() + 
                             ", Thread Name: " + Thread.currentThread().getName());
        };

        // 启动第一个虚拟线程，名称为"worker-0"
        Thread t1 = builder.start(task);
        t1.join();  // 等待线程结束
        System.out.println(t1.getName() + " terminated");

        // 启动第二个虚拟线程，名称自动递增为"worker-1"
        Thread t2 = builder.start(task);
        t2.join();  // 等待线程结束
        System.out.println(t2.getName() + " terminated");
    }
    
    /**
     * 测试ThreadLocal在大量虚拟线程场景下的内存影响
     *
     * ThreadLocal在虚拟线程中使用时需要特别注意，因为：
     * 1. 每个虚拟线程都有自己的ThreadLocal变量副本
     * 2. 当创建大量虚拟线程时，ThreadLocal可能导致严重的内存消耗
     * 3. 如果不正确清理ThreadLocal，可能导致内存泄漏
     *
     * 本方法创建指定数量的虚拟线程，每个线程都使用ThreadLocal存储数据
     * 并测量内存使用情况的变化
     *
     * @param threadCount 要创建的虚拟线程数量
     * @throws InterruptedException 如果线程被中断
     */
    private static void testThreadLocalMemoryImpact(int threadCount) throws InterruptedException {
        System.out.println("开始测试ThreadLocal在" + NumberFormat.getInstance().format(threadCount) + "个虚拟线程中的内存影响");
        
        // 创建一个ThreadLocal，存储一个1KB大小的数组
        ThreadLocal<byte[]> threadLocal = ThreadLocal.withInitial(() -> new byte[1024]); // 每个线程1KB

        // 打印初始内存使用情况
        printMemoryUsage("初始");
        
        // 使用CountDownLatch等待所有线程完成
        CountDownLatch latch = new CountDownLatch(threadCount * 2); // *2因为有两组测试
        
        // 计数器，用于显示进度
        AtomicInteger counter = new AtomicInteger(0);
        
        System.out.println("====== 第一阶段：创建" + NumberFormat.getInstance().format(threadCount) + "个使用ThreadLocal的虚拟线程 ======");
        
        // 创建并启动第一组虚拟线程 - 使用ThreadLocal
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // 访问ThreadLocal，触发创建
                        byte[] data = threadLocal.get();
                        // 修改数组内容以确保JVM不会优化掉
                        data[0] = 1;
                        
                        // 显示进度
                        int completed = counter.incrementAndGet();
                        if (completed % (threadCount / 10) == 0) {
                            System.out.println("已完成: " + NumberFormat.getInstance().format(completed) + 
                                             " 个线程 (" + (completed * 100 / threadCount) + "%)");
                        }
                        
                        // 模拟线程执行一些工作
                        Thread.sleep(1);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 重要：清理ThreadLocal以避免内存泄漏
                        threadLocal.remove();
                        latch.countDown();
                    }
                });
            }
        }
        
        // 打印使用ThreadLocal后的内存使用情况
        printMemoryUsage("创建使用ThreadLocal的" + NumberFormat.getInstance().format(threadCount) + "个虚拟线程后");
        
        // 重置计数器
        counter.set(0);
        
        System.out.println("\n====== 第二阶段：创建" + NumberFormat.getInstance().format(threadCount) + "个不使用ThreadLocal的虚拟线程（对照组） ======");
        
        // 创建并启动第二组虚拟线程 - 不使用ThreadLocal
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // 创建同样大小的数组，但不使用ThreadLocal存储
                        byte[] data = new byte[1024];
                        data[0] = 1;
                        
                        // 显示进度
                        int completed = counter.incrementAndGet();
                        if (completed % (threadCount / 10) == 0) {
                            System.out.println("已完成: " + NumberFormat.getInstance().format(completed) + 
                                             " 个线程 (" + (completed * 100 / threadCount) + "%)");
                        }
                        
                        // 模拟线程执行一些工作
                        Thread.sleep(1);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        // 等待所有线程完成
        latch.await();
        
        // 打印最终内存使用情况
        printMemoryUsage("创建不使用ThreadLocal的" + NumberFormat.getInstance().format(threadCount) + "个虚拟线程后");
        
        // 主动请求垃圾回收
        System.out.println("\n执行垃圾回收...");
        System.gc();
        Thread.sleep(1000); // 给GC一些时间执行
        
        // 打印垃圾回收后的内存使用情况
        printMemoryUsage("垃圾回收后");
        
        System.out.println("\n结论：");
        System.out.println("1. 当创建大量虚拟线程时，ThreadLocal会为每个线程创建独立的副本，占用更多内存");
        System.out.println("2. 如果不调用ThreadLocal.remove()，可能导致内存泄漏");
        System.out.println("3. 虚拟线程的主要优势在于I/O密集型场景，而非CPU密集型场景");
        System.out.println("4. 在设计使用大量虚拟线程的应用时，应谨慎使用ThreadLocal");
    }
    
    /**
     * 打印当前JVM的内存使用情况
     * 
     * @param stage 当前测试阶段的描述
     */
    private static void printMemoryUsage(String stage) {
        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        NumberFormat format = NumberFormat.getInstance();
        
        System.out.println("\n====== 内存使用情况: " + stage + " ======");
        System.out.println("已用内存: " + format.format(usedMemory / 1024 / 1024) + " MB");
        System.out.println("空闲内存: " + format.format(freeMemory / 1024 / 1024) + " MB");
        System.out.println("总分配内存: " + format.format(totalMemory / 1024 / 1024) + " MB");
        System.out.println("最大可用内存: " + format.format(maxMemory / 1024 / 1024) + " MB");
        System.out.println("内存使用率: " + format.format(usedMemory * 100.0 / maxMemory) + "%");
        
        try {
            // 尝试获取系统级内存信息（仅在支持的平台上有效）
            OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long systemTotalMemory = osBean.getTotalMemorySize();
            long systemFreeMemory = osBean.getFreeMemorySize();
            
            System.out.println("系统总内存: " + format.format(systemTotalMemory / 1024 / 1024) + " MB");
            System.out.println("系统空闲内存: " + format.format(systemFreeMemory / 1024 / 1024) + " MB");
            System.out.println("系统内存使用率: " + format.format((systemTotalMemory - systemFreeMemory) * 100.0 / systemTotalMemory) + "%");
        } catch (Exception e) {
            // 忽略不支持的平台异常
            System.out.println("无法获取系统级内存信息");
        }
    }
}
