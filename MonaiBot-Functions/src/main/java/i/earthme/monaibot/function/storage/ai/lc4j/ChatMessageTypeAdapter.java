package i.earthme.monaibot.function.storage.ai.lc4j;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.langchain4j.data.message.*;

import java.lang.reflect.Field;

public class ChatMessageTypeAdapter extends TypeAdapter<ChatMessage> {
    private static final Gson GSON;

    static {
        try {
            Class<GsonChatMessageJsonCodec> codecClass = GsonChatMessageJsonCodec.class;
            Field messagesToJsonField = codecClass.getDeclaredField("GSON");
            messagesToJsonField.setAccessible(true);
            GSON = (Gson) messagesToJsonField.get(null);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(JsonWriter out, ChatMessage value) {
        final JsonElement jsonElement = GSON.toJsonTree(value);
        GSON.toJson(jsonElement, out);
    }



    @Override
    public ChatMessage read(JsonReader in) {
        JsonElement parsed = JsonParser.parseReader(in);

        return GSON.fromJson(parsed, ChatMessage.class);
    }
}
