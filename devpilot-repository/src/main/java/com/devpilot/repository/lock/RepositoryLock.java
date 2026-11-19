package com.devpilot.repository.lock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 每个 SVN 仓库一把进程内互斥锁；多实例部署时应替换为数据库或分布式锁。
 * tryLock 而非 lock，保证调用方拿到明确的“仓库忙”信号而不是无限阻塞。
 */
public interface RepositoryLock {

    boolean tryAcquire(String repositoryKey);

    void release(String repositoryKey);
}
