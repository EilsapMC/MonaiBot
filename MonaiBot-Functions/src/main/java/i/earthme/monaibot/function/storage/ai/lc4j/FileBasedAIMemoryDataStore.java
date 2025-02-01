package i.earthme.monaibot.function.storage.ai.lc4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileBasedAIMemoryDataStore implements ChatMemoryStore {
    private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

   /* private final File dataFIle;

    public FileBasedAIMemoryDataStore(File dataFIle) {
        this.dataFIle = dataFIle;
    }*/

    public List<ChatMessage> getMessages(Object memoryId) {
        return this.messagesByMemoryId.computeIfAbsent(memoryId, (ignored) -> new ArrayList());
    }

    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        this.messagesByMemoryId.put(memoryId, messages);
    }

    public void deleteMessages(Object memoryId) {
        this.messagesByMemoryId.remove(memoryId);
    }
}
