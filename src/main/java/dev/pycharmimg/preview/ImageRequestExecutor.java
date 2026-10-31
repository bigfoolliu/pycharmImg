package dev.pycharmimg.preview;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ImageRequestExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "pycharm-image-preview");
                thread.setDaemon(true);
                return thread;
            });

    Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    void cancel(Future<?> task) {
        if (task != null) {
            task.cancel(true);
            executor.remove((Runnable) task);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
