# Java 虚拟线程（Virtual Thread）实验项目

此项目提供了一组演示和测试用例，用于探索 Java 21 引入的虚拟线程（Virtual Thread）特性，特别关注线程固定（Thread Pinning）问题及其解决方案，以及 ThreadLocal 在虚拟线程环境下的内存影响。

## 项目背景

**重要提示：本项目的所有实验和测试均基于 JDK 21 设计和测试。**

如果使用更高版本的 JDK（如 JDK 24），实验结果可能会有显著不同，因为更新版本对虚拟线程的行为进行了优化：

- JDK 24+ 中对 `Thread.sleep()` 进行了优化，降低了其对虚拟线程的阻塞影响
- `synchronized` 的线程固定行为在更高版本中可能已经有所改进
- 虚拟线程的调度策略和性能特性可能随着 JDK 版本的发展而变化

因此，为了准确复现本项目的实验结果和观察到线程固定问题，强烈建议使用 **JDK 21** 运行所有实验。

## 项目概述

Java 虚拟线程是一种轻量级线程实现，它允许开发者创建大量线程（数百万个）而不会耗尽系统资源。虚拟线程主要为高并发、I/O 密集型应用程序设计，可以大幅提升吞吐量并简化编程模型。

本项目特别关注虚拟线程的几个关键方面：
- 基本虚拟线程 API 使用
- 结构化并发
- **线程固定问题（Thread Pinning）** - 重点研究内容
- 使用 `ReentrantLock` 避免线程固定
- **ThreadLocal 在大规模虚拟线程场景下的内存影响**
- 高并发性能比较

## 线程固定问题说明

**线程固定（Thread Pinning）** 是虚拟线程的一个重要概念。当虚拟线程在执行 `synchronized` 代码块时，它会被"固定"在底层的载体线程（Carrier Thread）上，直到 `synchronized` 代码块执行完毕。这意味着：

1. 如果 `synchronized` 块内有 I/O 或阻塞操作，载体线程也会被阻塞
2. 其他等待该载体线程的虚拟线程也会受影响
3. 在高并发场景下，这会严重影响性能和吞吐量

## 项目结构与文件说明

本项目包含以下关键文件，每个文件都有特定的目的和功能：

### 基础演示类

#### Main.java
此文件是项目的主要入口点，展示了虚拟线程的基本功能和使用方法。

**主要功能：**
- 演示两种创建虚拟线程的方法：`Executors.newVirtualThreadPerTaskExecutor()` 和 `Thread.ofVirtual()`
- 实现大规模虚拟线程下 ThreadLocal 的内存影响测试，可创建高达 100 万个虚拟线程进行测试
- 包含详细的内存使用监控和分析功能

**关键方法：**
- `ofExecutors()`：演示使用 ExecutorService 创建虚拟线程
- `ofVirtual()`：演示使用 Thread.Builder 创建和配置虚拟线程
- `testThreadLocalMemoryImpact()`：测试 ThreadLocal 在大量虚拟线程环境下的内存影响
- `printMemoryUsage()`：打印详细的 JVM 和系统内存使用状态

#### EchoServer.java
使用虚拟线程实现的简单网络服务器，演示虚拟线程在网络 I/O 场景下的应用。

**主要功能：**
- 使用虚拟线程处理客户端连接，为每个连接创建一个新的虚拟线程
- 实现简单的回显协议，将客户端发送的数据原样返回
- 演示了虚拟线程在高并发网络连接场景下的优势

#### EchoClient.java
与 EchoServer 配合使用的客户端实现，用于测试服务器功能。

**主要功能：**
- 连接到 EchoServer 并发送用户输入数据
- 接收并显示服务器返回的数据
- 支持通过输入 "bye" 结束连接

### 极端测试场景 (compare 目录)

此目录下的类专门设计用于对照实验，清晰展示线程固定问题的影响和解决方案。

#### SynchronizedPinningBad.java
演示使用 `synchronized` 关键字导致的线程固定问题。

**主要功能：**
- 创建 2000 个虚拟线程任务，使用 100 个共享锁
- 每个线程获取 `synchronized` 锁后模拟 I/O 操作（Thread.sleep 1秒）
- 演示当虚拟线程执行 `synchronized` 块时被固定到载体线程上，导致并发性能下降
- 实际执行时间约为 (2000任务 / CPU核心数) * 1秒

#### ReentrantLockGood.java
演示使用 `ReentrantLock` 避免线程固定问题的解决方案。

**主要功能：**
- 创建相同数量的虚拟线程和锁（与 SynchronizedPinningBad 参数相同）
- 使用 `ReentrantLock` 替代 `synchronized` 来控制临界区
- 演示当虚拟线程在持有 ReentrantLock 时进行 I/O 操作，仍然可以让出载体线程
- 实际执行时间显著缩短，约为 (2000任务 / 锁数量) * 1秒

## 如何运行

### 基本示例

```bash
# 编译
javac Main.java

# 运行基本示例
java Main
```

### 线程固定对比实验

```bash
# 编译所有测试类
javac compare/*.java

# 运行测试（建议分别运行以便观察差异）
java compare.SynchronizedPinningBad
java compare.ReentrantLockGood
```

## 实验结论

### 线程固定问题结论

通过实际实验，我们可以得出以下结论：

1. 在包含阻塞操作（如 I/O 或 `Thread.sleep()`）的代码中，`synchronized` 会导致严重的线程固定问题
2. 当载体线程数量有限（如设置 `jdk.virtualThreadScheduler.parallelism` 为低值）时，线程固定问题会更加明显
3. 使用 `ReentrantLock` 可以有效避免线程固定问题，因为虚拟线程可以在等待锁或执行 I/O 时让出载体线程
4. 在高并发、I/O 密集型应用中，使用 `ReentrantLock` 替代 `synchronized` 可以显著提升吞吐量

### ThreadLocal 内存影响结论

通过 `testThreadLocalMemoryImpact` 方法的测试（创建 10 万个虚拟线程），我们发现：

1. **内存占用**：使用 ThreadLocal 的 10 万虚拟线程使内存增加约 248MB（从 6MB 到 254MB），平均每个虚拟线程的 ThreadLocal 开销约为 2.5KB
2. **对照组差异**：不使用 ThreadLocal 的对照组内存使用略高（291MB），但总分配内存显著增加（1,288MB vs 644MB）
3. **垃圾回收效果**：正确调用 `threadLocal.remove()` 后，垃圾回收非常有效，可将内存使用降至接近初始状态
4. **系统影响**：创建 10 万虚拟线程本身并未对系统造成严重负担，证明虚拟线程的轻量特性

## 环境要求

- **Java 21**（强烈推荐，为保证实验结果一致性）
- 支持 JEP 444 (Virtual Threads) 的 JVM
- 注意：使用 JDK 24+ 可能观察不到相同的线程固定现象，因为更新版本对虚拟线程的实现进行了优化

## 参考资料

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Java 虚拟线程官方文档](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)

## ThreadLocal 在虚拟线程中的使用指南

### 使用场景

在传统平台线程中，ThreadLocal 常用于：

1. **用户身份信息与上下文传递**：在 Web 应用中保存当前用户信息，避免层层传参
2. **数据库连接与事务管理**：维护线程专属的数据库连接
3. **简化方法参数传递**：当多层方法调用需要共享状态时
4. **线程安全的单例变种**：如 ThreadLocal 维护的 SimpleDateFormat 实例

### 虚拟线程中的注意事项

1. **内存消耗问题**
   - 每个 ThreadLocal 实例会在每个虚拟线程中创建副本
   - 百万级虚拟线程可能导致 GB 级内存消耗
   - 必须通过 `remove()` 及时清理，否则内存无法及时释放

2. **线程池与虚拟线程使用 ThreadLocal 的陷阱**
   - 忘记清理可能导致资源无法释放
   - 虚拟线程数量庞大时问题被放大

### 替代方案

在大规模虚拟线程场景下，考虑以下替代方案：

1. **显式参数传递**：直接通过方法参数传递信息，避免隐式依赖
2. **上下文对象模式**：创建专门的上下文对象，显式传递
3. **Java 21+ 结构化并发与作用域值**：使用 ScopedValue 替代 ThreadLocal，提供更安全的线程局部变量

### 最佳实践

如果必须在虚拟线程中使用 ThreadLocal：

1. **始终使用 try-finally 清理**
   ```java
   try {
       threadLocal.set(value);
       // 使用资源
   } finally {
       threadLocal.remove(); // 确保清理
   }
   ```

2. **限制创建的虚拟线程数量**：对使用 ThreadLocal 的场景，考虑限制最大并发虚拟线程数
3. **监控内存使用**：定期监控内存使用，防止内存泄漏
4. **教育团队**：确保团队了解 ThreadLocal 在虚拟线程下的潜在问题
