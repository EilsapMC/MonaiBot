package i.earthme.monaibot.function.storage.ai.lc4j;

import com.google.gson.*;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileBasedAIMemoryDataStore implements ChatMemoryStore {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeHierarchyAdapter(ChatMessage.class, new ChatMessageTypeAdapter()).create();

    private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

    private final File dataFIle;
    private final Executor saveScheduler;

    private volatile Map<Object, List<ChatMessage>> pendingToSave;
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public FileBasedAIMemoryDataStore(File dataFIle, Executor ioWorker, long flushInterval, TimeUnit unit) throws IOException {
        this.dataFIle = dataFIle;
        this.saveScheduler = CompletableFuture.delayedExecutor(flushInterval, unit, ioWorker);

        this.load();
    }

    public List<ChatMessage> getMessages(Object memoryId) {
        return this.messagesByMemoryId.computeIfAbsent(memoryId, (ignored) -> new ArrayList<>());
    }

    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        this.messagesByMemoryId.put(memoryId, messages);

        this.pendingToSave = new HashMap<>(this.messagesByMemoryId);

        this.tryScheduleSave();
    }

    public void deleteMessages(Object memoryId) {
        this.messagesByMemoryId.remove(memoryId);

        this.pendingToSave = new HashMap<>(this.messagesByMemoryId);

        this.tryScheduleSave();
    }

    public void tryScheduleSave() {
        if (!this.savePending.getAndSet(true)) {
            this.saveScheduler.execute(() -> {
                try {
                    this.save();
                }catch (Exception e){
                    logger.error("Error while saving memory data {}!",this.dataFIle.getName(), e);
                }finally {
                    this.savePending.set(false);
                }
            });
        }
    }

    public void save() throws IOException {
        final Map<Object, List<ChatMessage>> pendingToSave = new HashMap<>(this.pendingToSave);
        final JsonObject dataJson = new JsonObject();

        for (Map.Entry<Object, List<ChatMessage>> entry : pendingToSave.entrySet()) {
            final Object memoryId = entry.getKey();
            final List<ChatMessage> messages = entry.getValue();

            if (!this.checkType(memoryId)) {
                logger.warn("Memory ID {} is not a valid type, skipping!", memoryId);
                continue;
            }

            final JsonElement json = this.mapMemoriesToArray(messages);

            dataJson.add(memoryId.toString(), json);
        }

        Files.writeString(this.dataFIle.toPath(), gson.toJson(dataJson));
    }

    private @NotNull JsonArray mapMemoriesToArray(@NotNull List<ChatMessage> messages) {
        final JsonArray messagesJson = new JsonArray();

        for (ChatMessage message : messages) {
            messagesJson.add(gson.toJsonTree(message));
        }

        return messagesJson;
    }

    private @NotNull List<ChatMessage> mapArrayToMemories(@NotNull JsonArray messagesJson) {
        final List<ChatMessage> messages = new ArrayList<>();

        for (JsonElement messageJson : messagesJson) {
            messages.add(gson.fromJson(messageJson, ChatMessage.class));
        }

        return messages;
    }

    public void load() throws IOException {
        if (!this.dataFIle.exists()) {
            return;
        }

        final JsonObject dataJson = JsonParser.parseString(Files.readString(this.dataFIle.toPath())).getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
            final Object memoryId = entry.getKey();
            final JsonElement messagesJson = entry.getValue();

            final List<ChatMessage> messages = this.mapArrayToMemories(messagesJson.getAsJsonArray());

            this.messagesByMemoryId.put(memoryId, messages);
        }
    }

    private boolean checkType(Object messageId) {
        if (messageId instanceof String) {
            return true;
        }

        return messageId instanceof Integer;
    }
}
