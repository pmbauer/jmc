package org.openjdk.jmc.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.junit.Test;

public class DebouncerTest {
    private final static java.util.logging.Logger LOGGER = Logger.getLogger("org.openjdk.jmc.common.util");

    @Test
    public void testLastInWins() throws ExecutionException, InterruptedException {
        Debouncer<Integer> debouncer = new Debouncer<>();
        // first task, will be cancelled before it starts executing
        Future<Integer> first = debouncer.execute(delayedResult(10, 42), 50);
        Thread.sleep(20);
        // second task, it will start executing then be cancelled
        Future<Integer> second = debouncer.execute(delayedResult(20, 7), 50);
        Thread.sleep(60);
        // third task, will execute and complete successfully
        Future<Integer> third = debouncer.execute(delayedResult(30, 777), 10);

        // check expected status for each task
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        assertFalse(third.isCancelled());
        // check returned result
        int result = third.get();
        assertEquals(result, 777);
    }

    private Callable<Integer> delayedResult(final int delayMs, final int result) {
        return () -> {
            LOGGER.info("Execution started, waiting for " + delayMs + "ms");
            int waitTime = 0;
            while (!Thread.currentThread().isInterrupted() && waitTime < delayMs) {
                waitTime += 1;
                Thread.sleep(1);
            }

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("Execution interrupted after " + waitTime + "ms");
                throw new InterruptedException();
            }

            LOGGER.info("Wait completed, returning " + result);
            if (result == 7) {
                // the second result should be cancelled before it can return
                throw new RuntimeException();
            }
            return result;
        };
    }
}
