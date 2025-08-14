package i.earthme.monaibot.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class MonaiBotWorkerFactory implements ThreadFactory {
    private final AtomicInteger id = new AtomicInteger();

    @Override
    public Thread newThread(@NotNull Runnable r) {
        final Thread worker = new Thread(r, "MonaiBot Async Worker Thread #" + id.getAndIncrement());

        worker.setPriority(Thread.NORM_PRIORITY - 2);

        return worker;
    }
}
