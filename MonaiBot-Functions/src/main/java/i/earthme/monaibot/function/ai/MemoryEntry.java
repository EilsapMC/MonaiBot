package i.earthme.monaibot.function.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import kotlinx.serialization.json.Json;
import okhttp3.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public record MemoryEntry(
        String role,
        String content
) {
    @Contract("_ -> new")
    public static @NotNull MemoryEntry toMemoryEntry(@NotNull JsonObject jsonObject) {
        JsonArray choices = jsonObject.get("choices").getAsJsonArray();
        if (choices == null) {
            throw new RuntimeException("No choices found in the response: " + jsonObject);
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.get("message").getAsJsonObject();

        return new MemoryEntry(
                message.get("role").getAsString(),
                message.get("content").getAsString()
        );
    }

    public static @NotNull JsonObject requestAPI(
            @NotNull String apiUrl,
            @Nullable String apiKey,
            @NotNull List<MemoryEntry> messages,
            String model,
            double temperature,
            int maxTokens,
            double topP,
            double frequencyPenalty,
            double presencePenalty
    ) throws Exception {
        JsonArray memoryRecords = new JsonArray();

        for (MemoryEntry botMemory : messages) {
            JsonObject memoryRecord = new JsonObject();

            memoryRecord.add("role", new JsonPrimitive(botMemory.role()));
            memoryRecord.add("content", new JsonPrimitive(botMemory.content()));

            memoryRecords.add(memoryRecord);
        }

        JsonObject builtJsonData = new JsonObject();

        builtJsonData.add("model", new JsonPrimitive(model));
        builtJsonData.add("temperature", new JsonPrimitive(temperature));
        builtJsonData.add("top_p", new JsonPrimitive(topP));
        builtJsonData.add("max_tokens", new JsonPrimitive(maxTokens));
        builtJsonData.add("presence_penalty", new JsonPrimitive(presencePenalty));
        builtJsonData.add("frequency_penalty", new JsonPrimitive(frequencyPenalty));
        builtJsonData.add("messages", memoryRecords);
        builtJsonData.add("stream", new JsonPrimitive(false)); //Force disable stream responsing

        URL url = new URL(apiUrl);

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, builtJsonData.toString());

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json");

        if (apiKey != null) {
            requestBuilder = requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        Request request = requestBuilder.build();

        Response response = client.newCall(request).execute();
        String str = response.body().string();
        JsonObject parsed = JsonParser.parseString(str).getAsJsonObject();

        return parsed;
    }
}
