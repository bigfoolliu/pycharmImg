package dev.pycharmimg.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ImageRequestExecutorTest {
    @Test
    public void onlyTheLatestQueuedRequestRunsAfterTheActiveOne() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch lastFinished = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger obsoleteRuns = new AtomicInteger();

        try (ImageRequestExecutor executor = new ImageRequestExecutor()) {
            executor.submit(() -> {
                int count = active.incrementAndGet();
                maximumActive.accumulateAndGet(count, Math::max);
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
            });
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            Future<?> obsolete = executor.submit(obsoleteRuns::incrementAndGet);
            executor.cancel(obsolete);
            executor.submit(() -> {
                int count = active.incrementAndGet();
                maximumActive.accumulateAndGet(count, Math::max);
                active.decrementAndGet();
                lastFinished.countDown();
            });
            releaseFirst.countDown();
            assertTrue(lastFinished.await(1, TimeUnit.SECONDS));
            assertTrue(obsolete.isCancelled());
            assertEquals(0, obsoleteRuns.get());
            assertEquals(1, maximumActive.get());
        } finally {
            releaseFirst.countDown();
        }
    }
}
