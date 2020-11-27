package org.openjdk.jmc.common.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Debouncer<T> {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<T> scheduledTask;

    public synchronized Future<T> execute(Callable<T> callable, int delayMs) {
        if (scheduledTask != null) {
            if (scheduledTask.isDone()) {
                try {
                    scheduledTask.get();
                } catch (Throwable t) {
                    // log eventual errors from the task
                    System.out.println(t);
                    t.getCause().printStackTrace();
                }
            }
            scheduledTask.cancel(true);
        }
        scheduledTask = executorService.schedule(callable, delayMs, TimeUnit.MILLISECONDS);

        return scheduledTask;
    }
}
