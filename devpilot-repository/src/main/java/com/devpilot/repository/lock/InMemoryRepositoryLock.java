package com.devpilot.repository.lock;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
public class InMemoryRepositoryLock implements RepositoryLock {

    private final ConcurrentHashMap<String, Holder> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String repositoryKey) {
        if (repositoryKey == null || repositoryKey.isBlank()) {
            throw new IllegalArgumentException("仓库键不能为空");
        }
        var holder = locks.computeIfAbsent(repositoryKey, ignored -> new Holder());
        if (!holder.semaphore().tryAcquire()) {
            return false;
        }
        holder.owner(Thread.currentThread());
        return true;
    }

    @Override
    public void release(String repositoryKey) {
        var holder = locks.get(repositoryKey);
        if (holder != null && holder.owner() == Thread.currentThread()) {
            holder.owner(null);
            holder.semaphore().release();
        }
    }

    int lockCount() {
        return locks.size();
    }

    /**
     * Semaphore 非重入：同一线程重复获取会失败，避免业务代码把锁当可重入锁误用。
     */
    private static final class Holder {
        private final Semaphore semaphore = new Semaphore(1);
        private volatile Thread owner;

        Semaphore semaphore() {
            return semaphore;
        }

        Thread owner() {
            return owner;
        }

        void owner(Thread thread) {
            this.owner = thread;
        }
    }
}
