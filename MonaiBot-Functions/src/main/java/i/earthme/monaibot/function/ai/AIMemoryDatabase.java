package i.earthme.monaibot.function.ai;

import com.google.gson.JsonPrimitive;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.function.storage.ai.cesium.api.database.DatabaseSpec;
import i.earthme.monaibot.function.storage.ai.cesium.common.lmdb.LMDBInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AIMemoryDatabase {
    private final DatabaseSpec<String, MemoryEntry[]> memoryDatabaseSpec = new DatabaseSpec<>(
            "memory",
            String.class,
            MemoryEntry[].class,
            8 * 1024
    );
    private final LMDBInstance memoryDatabase = new LMDBInstance(
            Bootstrapper.getBaseDir().toPath(),
            "memory",
            new DatabaseSpec[]{ memoryDatabaseSpec }
    );
    private final ScheduledExecutorService saveScheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean closed = false;
    private final ScheduledFuture<?> scheduledSaveTask;
    {
        final long flushInterval = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_database_flush_interval_ms", new JsonPrimitive(10_000L)).getAsLong();
        scheduledSaveTask = saveScheduler.scheduleAtFixedRate(() -> {
            if (closed) {
                return;
            }

            memoryDatabase.flushChanges();
        },flushInterval , flushInterval, TimeUnit.MILLISECONDS);
    }

    public List<MemoryEntry> getProcessedContext(String userId) {
        MemoryEntry[] actuallyData = memoryDatabase.getDatabase(memoryDatabaseSpec).getValue(userId);

        if (actuallyData == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(List.of(actuallyData));
    }

    public void logToMemory(String userId, String role, String content) {
        MemoryEntry[] data = memoryDatabase.getDatabase(memoryDatabaseSpec).getValue(userId);

        List<MemoryEntry> dataList = data == null ? new ArrayList<>() : new ArrayList<>(List.of(data));

        dataList.add(new MemoryEntry(role, content));

        memoryDatabase.getTransaction(memoryDatabaseSpec).add(userId, dataList.toArray(MemoryEntry[]::new));

        this.memoryDatabase.flushChanges();
    }

    public void close() {
        closed = true;
        scheduledSaveTask.cancel(true);

        memoryDatabase.flushChanges();
        memoryDatabase.close();
    }
}
