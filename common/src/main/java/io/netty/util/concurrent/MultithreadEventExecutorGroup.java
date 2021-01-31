/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at the same time.
 * todo 继承 AbstractEventExecutorGroup 抽象类，基于多线程的 EventExecutor ( 事件执行器 )的分组抽象类。
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    // todo EventExecutor 数组
    private final EventExecutor[] children;

    // todo 不可变 (只读) 的 EventExecutor 数组.
    private final Set<EventExecutor> readonlyChildren;

    // todo 已终止的 EventExecutor 数量
    private final AtomicInteger terminatedChildren = new AtomicInteger();

    // todo 用于终止 EventExecutor 的异步 Future
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);

    // todo EventExecutor 选择器
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     *                          todo args 是 selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject()的简写
     *
     * todo 这个构造方法中，完成了一些属性的赋值, 彻底构造完成 <事件循环组对象>
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
            /**
             * todo ThreadPerTaskExecutor   意味着为当前的事件循环组创建Executor , 用于针对每一个任务的 Executor 线程的执行器
             * todo newDefaultThreadFactory 根据它的特性,可以给线程加名字等。
             * todo 比传统的好处是 把创建线程和 定义线程需要做的任务分开, 我们只关心任务,  两者解耦
             * todo 每次执行任务都会创建一个线程实体
             * todo NioEventLoop 线程命名规则  nioEventLoop-1-XX    1代表是第几个group   XX第几个eventLoop
             */
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        // todo 创建 EventExecutor 数组
        children = new EventExecutor[nThreads];

        // todo 循环
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                // todo 创建 EventLoop 事件循环   创建 EventExecutor 对象
                children[i] = newChild(executor, args);
                // todo 标记创建成功
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                // todo 创建失败,关闭所有一创建的 EventExecutor
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        // todo 迭代,关闭所有已创建的 EventExecutor
                        children[j].shutdownGracefully();
                    }

                    // todo 确保所有已创建的 EventExecutor 已关闭
                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        // todo chooser 在这里实例化   创建 EventExecutor 选择器
        chooser = chooserFactory.newChooser(children);

        // todo 创建监听器，用于 EventExecutor 终止时的监听
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        // todo 设置监听器到每个 EventExecutor 上
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        // todo 创建不可变 (只读) 的 EventExecutor 数组
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    protected ThreadFactory newDefaultThreadFactory() {
        // todo 创建的对象为 DefaultThreadFactory ，并且使用类名作为 poolType 。
        return new DefaultThreadFactory(getClass());
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
