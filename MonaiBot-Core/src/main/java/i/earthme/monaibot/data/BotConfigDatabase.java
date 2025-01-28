package i.earthme.monaibot.data;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotConfigDatabase {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LogManager.getLogger(BotConfigDatabase.class);

    private final Map<String, JsonElement> loadedValue = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor;
    private final File dataFile;

    private Executor saveDispatcher;

    private volatile boolean isDirty = false;
    private volatile boolean saveNext = true;

    public BotConfigDatabase(ExecutorService ioExecutor, File dataFile) {
        this.ioExecutor = ioExecutor;
        this.dataFile = dataFile;
    }

    public void load() throws IOException {
        if (!this.dataFile.exists()) {
            logger.info("Bot config database file not found, creating new one.");
            this.initDefaultValues();
            this.save();

            this.activeSaveScheduler();
            this.scheduleSave();
            return;
        }

        final JsonObject parsedJsonObject = JsonParser.parseReader(new FileReader(this.dataFile)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : parsedJsonObject.entrySet()) {
            final String keyName = entry.getKey();
            final JsonElement value = entry.getValue();

            this.loadedValue.put(keyName, value);
        }

        logger.info("Loaded {} data key-pairs", this.loadedValue.size());

        this.activeSaveScheduler();
        this.scheduleSave();
    }

    public void scheduleSave() {
        if (this.saveDispatcher != null && this.saveNext) {
            this.saveDispatcher.execute(() -> {
                try {
                    this.save();
                }catch (Exception e){
                    logger.error("Failed to save bot config database.", e);
                }finally {
                    this.scheduleSave();
                }
            });
        }
    }

    public void close() throws IOException {
        this.save();
        this.saveNext = false;
    }

    public JsonElement get(String keyName){
        return this.loadedValue.get(keyName);
    }

    public JsonElement getOrElse(String keyName, JsonElement defaultValue){
        AtomicBoolean shouldFlush = new AtomicBoolean(false);

        final JsonElement got = this.loadedValue.computeIfAbsent(keyName, v -> {
            shouldFlush.set(true);
            return defaultValue;
        });

        if (shouldFlush.get()){
            this.isDirty = true;
        }

        return got;
    }

    public void remove(String keyName){
        this.loadedValue.remove(keyName);
        this.isDirty = true;
    }

    public void activeSaveScheduler(){
        final long saveInterval = this.get("save_interval_ms").getAsLong();

        this.saveDispatcher = CompletableFuture.delayedExecutor(saveInterval, TimeUnit.MILLISECONDS, this.ioExecutor);
    }

    public void save() throws IOException {
        this.isDirty = false;
        final JsonObject jsonObject = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : this.loadedValue.entrySet()) {
            final String keyName = entry.getKey();
            final JsonElement value = entry.getValue();

            jsonObject.add(keyName, value);
        }

        final String jsonContent = gson.toJson(jsonObject);
        Files.writeString(this.dataFile.toPath(), jsonContent);
    }

    private void initDefaultValues(){
        this.loadedValue.put("save_interval_ms", new JsonPrimitive(30_000L));
    }
}
