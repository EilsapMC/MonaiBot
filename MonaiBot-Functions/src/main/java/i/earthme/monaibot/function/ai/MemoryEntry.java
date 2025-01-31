package i.earthme.monaibot.function.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import okhttp3.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    private static @NotNull Call requestAPIInternal(
            long apiTimeoutSec,
            @NotNull String apiUrl,
            @Nullable String apiKey,
            @NotNull List<MemoryEntry> messages,
            String model,
            double temperature,
            int maxTokens,
            double topP,
            double frequencyPenalty,
            double presencePenalty
    ) throws MalformedURLException {
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
                .callTimeout(apiTimeoutSec, TimeUnit.SECONDS)
                .readTimeout(apiTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(apiTimeoutSec, TimeUnit.SECONDS)
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

        return client.newCall(request);
    }

    public static @NotNull CompletableFuture<JsonObject> requestAPIAsync(
            long apiTimeoutSec,
            @NotNull String apiUrl,
            @Nullable String apiKey,
            @NotNull List<MemoryEntry> messages,
            String model,
            double temperature,
            int maxTokens,
            double topP,
            double frequencyPenalty,
            double presencePenalty
    ) throws MalformedURLException {
        final CompletableFuture<JsonObject> callback = new CompletableFuture<>();

        requestAPIInternal(apiTimeoutSec, apiUrl, apiKey, messages, model, temperature, maxTokens, topP, frequencyPenalty, presencePenalty)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        callback.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        final String content = response.body().string();

                        callback.complete(JsonParser.parseString(content).getAsJsonObject());
                    }
                });

        return callback;
    }

    @Deprecated
    public static @NotNull JsonObject requestAPIBlocking(
            long apiTimeoutSec,
            @NotNull String apiUrl,
            @Nullable String apiKey,
            @NotNull List<MemoryEntry> messages,
            String model,
            double temperature,
            int maxTokens,
            double topP,
            double frequencyPenalty,
            double presencePenalty
    ) throws IOException {
        final Response response = requestAPIInternal(apiTimeoutSec, apiUrl, apiKey, messages, model, temperature, maxTokens, topP, frequencyPenalty, presencePenalty).execute();

        String str = response.body().string();

        return JsonParser.parseString(str).getAsJsonObject();
    }
}
