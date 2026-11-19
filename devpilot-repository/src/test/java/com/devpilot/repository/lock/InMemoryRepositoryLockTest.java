package com.devpilot.repository.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRepositoryLockTest {

    @Test
    void secondAcquireFailsWhileHeld() {
        var lock = new InMemoryRepositoryLock();

        assertThat(lock.tryAcquire("repo")).isTrue();
        assertThat(lock.tryAcquire("repo")).isFalse();

        lock.release("repo");
        assertThat(lock.tryAcquire("repo")).isTrue();
        lock.release("repo");
    }

    @Test
    void differentRepositoriesDoNotBlockEachOther() {
        var lock = new InMemoryRepositoryLock();

        assertThat(lock.tryAcquire("repo-a")).isTrue();
        assertThat(lock.tryAcquire("repo-b")).isTrue();

        lock.release("repo-a");
        lock.release("repo-b");
    }

    @Test
    void concurrentThreadCannotAcquireHeldLock() throws Exception {
        var lock = new InMemoryRepositoryLock();
        assertThat(lock.tryAcquire("repo")).isTrue();

        var acquired = new AtomicBoolean(true);
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var thread = new Thread(() -> {
            started.countDown();
            acquired.set(lock.tryAcquire("repo"));
            finished.countDown();
        });
        thread.start();
        started.await();
        finished.await();
        thread.join();

        assertThat(acquired).isFalse();
        lock.release("repo");
    }
}
