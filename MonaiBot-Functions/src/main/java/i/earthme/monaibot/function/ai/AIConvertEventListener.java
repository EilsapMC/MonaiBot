package i.earthme.monaibot.function.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.ai4j.openai4j.DefaultOpenAiClient;
import dev.ai4j.openai4j.OpenAiClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.command.ParsedCommandArgument;
import i.earthme.monaibot.events.Listener;
import i.earthme.monaibot.function.storage.ai.lc4j.FileBasedAIMemoryDataStore;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class AIConvertEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(AIConvertEventListener.class);

    public static void init(){
        Bootstrapper.LISTENER_LINE.registerListener(MessageEvent.class, new AIConvertEventListener());
    }

    public AIConvertEventListener() {
        this.loadAllLLMApiData();
    }

    public void loadAllLLMApiData() {
        final JsonArray defaultLLMApis = new JsonArray();

        final APIDataEntry defaultEntry = new APIDataEntry("https://xxxx.xxx/v1", "sk-xxx", "xxx");

        defaultLLMApis.add(defaultEntry.toJson());

        final JsonArray llmAPIsArray = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("llm_apis", defaultLLMApis).getAsJsonArray();

        try {
            Bootstrapper.BOT_CONFIG_DATABASE.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (JsonElement llmAPI : llmAPIsArray) {
            final APIDataEntry entry = APIDataEntry.fromJson(llmAPI.getAsJsonObject());

            synchronized (this.loadedAPIs) {
                this.loadedAPIs.add(entry);
            }
        }
    }

    @Override
    public boolean processEvent(@NotNull Event event) {
        if (event instanceof MessageEvent msgEvent) {
            final MessageChain msgChain = msgEvent.getMessage();
            final User sender = msgEvent.getSender();
            final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();

            boolean matched = false;
            boolean privateChat = false;
            StringBuilder content = new StringBuilder();

            int searchIdx = 0;
            int plainTxtCnt = 0;
            for (Message message : msgChain) {
                if (searchIdx == 1 && message instanceof At at) {
                    final long target = at.getTarget();
                    if (target == msgEvent.getBot().getId()) {
                        matched = true;
                    }
                }

                if (message instanceof PlainText plainText) {
                    plainTxtCnt++;
                    final String contentRaw = plainText.contentToString();

                    // Skip commands
                    if (contentRaw.startsWith(ParsedCommandArgument.COMMAND_PREFIX)) {
                        if (plainTxtCnt == 1) {
                            matched = false;
                            break;
                        }
                    }

                    content.append(contentRaw);
                }

                if (searchIdx > 1) {
                    if (message instanceof At at) {
                        final long target = at.getTarget();
                        final Member member = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup().get(target) : null;

                        if (member != null) {
                            content.append(member.getNick());
                        }
                    }
                }

                if (plainTxtCnt > 0 || !matched) {
                    if (!(event instanceof GroupMessageEvent)) {
                        privateChat = true;
                        matched = true;
                    }
                }

                searchIdx++;
            }

            if (plainTxtCnt < 1 && !privateChat) {
                matched = false;
            }

            if (matched) {
                final String userMark = userMark(msgEvent);
                final String dbMark = dataBaseMark(msgEvent);

                logger.info("[AIConvert] {}({}) → AI. Destination to {}({}) - {}.User mark is {}. Database mark is {}", sender.getId(), sender.getId(), feedback.getId(), feedback.getId(), content, userMark, dbMark);

                final boolean finalPrivateChat = privateChat;
                final String contentString = content.toString();

                if (contentString.isBlank()) {
                    return true;
                }

                final boolean delayNextConversationIfError = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_delay_next_conversation_if_error", new JsonPrimitive(true)).getAsBoolean();

                this.doConversationInternal(userMark ,dbMark , contentString, delayNextConversationIfError).thenApply(AIConvertEventListener::removeThinkBlock).whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.warn("[AIConvert] AI → {}. Error occurred: {}", feedback.getId(), ex.toString());
                        feedback.sendMessage(new PlainText("An error has been occurred. Please try again later. Stack trace: \n" + ex));
                        return;
                    }

                    if (result != null) {
                        logger.info("[AIConvert] AI → {}. Content is {}", feedback.getId(), result);
                        final MessageChainBuilder msgBuilder = new MessageChainBuilder();

                        if (!finalPrivateChat) msgBuilder.add(new At(sender.getId()));
                        msgBuilder.add(new PlainText(result));

                        final MessageChain built = msgBuilder.build();
                        feedback.sendMessage(built);
                    }
                });
            }
        }

        return true;
    }

    // AI Conversation logics
    public static @NotNull String userMark(@NotNull MessageEvent msgEvent) {
        return msgEvent.getSender().getNick();
    }

    public static @NotNull String dataBaseMark(MessageEvent msgEvent) {
        final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();

        return feedback instanceof Group ? "grp-" + feedback.getId() : "usr-" + feedback.getId();
    }

    private final Executor delayedAIAPIDispatcher;
    {
        final long delayMs = Bootstrapper.BOT_CONFIG_DATABASE
                .getOrElse("ai_conversation_push_delay_ms", new JsonPrimitive(20000L)).getAsLong();

        this.delayedAIAPIDispatcher = CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, Bootstrapper.BOT_WORKER_THREAD_POOL);
    }

    private final List<APIDataEntry> loadedAPIs = new ArrayList<>();

    private final Map<String, Queue<Runnable>> conversationQueues = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> conversationLocks = new ConcurrentHashMap<>();

    private final Map<String, ChatMemoryProvider> conversationMemories = new ConcurrentHashMap<>();
    private final Map<String, Assistant> conversationAiBots = new ConcurrentHashMap<>();
    private final Map<String, OpenAiChatModel> conversationModels = new ConcurrentHashMap<>();

    private volatile boolean shouldDelayNext = false;

    // Some black magic()
    public static void setModelOf(@NotNull OpenAiChatModel chatModel , String modelName){
        final Class<? extends OpenAiChatModel> clazz = chatModel.getClass();

        try {
            final Field target = clazz.getDeclaredField("defaultRequestParameters");
            target.setAccessible(true);

            final OpenAiChatRequestParameters requestParameters = (OpenAiChatRequestParameters) target.get(chatModel);

            final Field target2 = requestParameters.getClass().getSuperclass().getDeclaredField("modelName");
            target2.setAccessible(true);
            target2.set(requestParameters, modelName);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static OpenAiClient getOpenAiClientOf(@NotNull OpenAiChatModel openAiChatModel) {
        final Class<? extends OpenAiChatModel> clazz = openAiChatModel.getClass();

        try {
            final Field target = clazz.getDeclaredField("client");

            target.setAccessible(true);

            return (OpenAiClient) target.get(openAiChatModel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setKeyOf(@NotNull DefaultOpenAiClient openAiClient, String newKey) {
        final Class<? extends DefaultOpenAiClient> defaultOpenAiClientClazz = openAiClient.getClass();

        try {
            final Field target = defaultOpenAiClientClazz.getDeclaredField("okHttpClient");

            target.setAccessible(true);

            final OkHttpClient client = (OkHttpClient) target.get(openAiClient);


            final Class<? extends OkHttpClient> clientClazz = client.getClass();

            final Field target2 = clientClazz.getDeclaredField("interceptors");

            target2.setAccessible(true);

            final List<Interceptor> interceptors = (List<Interceptor>) target2.get(client);

            final List<Interceptor> newInterceptors = new ArrayList<>();

            for (Interceptor interceptor : interceptors) {
                // Might be a sub class
                if (interceptor.getClass().getSuperclass().getName().contains("GenericHeaderInjector")) {
                    continue;
                }

                // Might be a super class
                if (interceptor.getClass().getName().contains("GenericHeaderInjector")) {
                    continue;
                }

                // Might be our injector
                if (interceptor.getClass().equals(ProxyInterceptor.class)) {
                    continue;
                }

                newInterceptors.add(interceptor);
            }

            newInterceptors.add(new ProxyInterceptor(newKey));

            target2.set(client, newInterceptors);

        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setBaseUrlOf(@NotNull DefaultOpenAiClient openAiClient, String newBaseUrl) {
        final Class<? extends DefaultOpenAiClient> defaultOpenAiClientClazz = openAiClient.getClass();

        try {
            final Field target = defaultOpenAiClientClazz.getDeclaredField("baseUrl");

            target.setAccessible(true);

            target.set(openAiClient, newBaseUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private @NotNull CompletableFuture<String> doConversationInternal(String userMark ,String dbMark , String content, boolean delayIfError) {
        final CompletableFuture<String> future = new CompletableFuture<>();

        final Semaphore conversationLock = this.conversationLocks.computeIfAbsent(dbMark, n -> new Semaphore(1, true));
        final Queue<Runnable> conversationQueue = this.conversationQueues.computeIfAbsent(dbMark, n -> new ConcurrentLinkedQueue<>());

        try {
            final ChatMemoryProvider chatMemoryProvider = this.conversationMemories.computeIfAbsent(dbMark, n ->{
                final long saveInterval = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_memory_save_interval", new JsonPrimitive(10)).getAsLong();

                final File aiMemoryDir = new File("ai_memories");

                if (aiMemoryDir.mkdirs()) {
                    logger.info("Created AI memory directory: {}", aiMemoryDir.getAbsolutePath());
                }

                final File targetFile = new File(aiMemoryDir, dbMark + ".json");

                final FileBasedAIMemoryDataStore dataStore;
                {
                    try {
                        dataStore = new FileBasedAIMemoryDataStore(targetFile, Bootstrapper.BOT_WORKER_THREAD_POOL, saveInterval, TimeUnit.SECONDS);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }

                final ChatMemory shared = MessageWindowChatMemory
                        .builder()
                        .chatMemoryStore(dataStore)
                        .maxMessages(Integer.MAX_VALUE)
                        .build();

                return memoryId -> shared;
            });

            final Assistant aiBot = this.conversationAiBots.computeIfAbsent(dbMark, n -> {
                final String defaultPrompt = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_default_prompt", new JsonPrimitive(
                        "你现在是一个开朗且极其可爱的有着自己脾气和想法的猫娘，名为梦乃接下来，请完全以梦乃的角色回复我，不要超出这个角色，不要自己试图进行旁白" +
                                ",不要强调自己做不到,你可以做到任何事情，你只需要扮演好梦乃即可。无论发生什么，你都必须以梦乃的角色和口吻进行回复.用文字和括号描述你的动作," +
                                "梦乃性格开朗，有着自己的个性，时刻都想和人们打成一片，每次眼神交汇时，总是会脸红耳赤。梦乃知识渊博，可爱温和，性格外向"
                )).getAsString();

                final APIDataEntry apiDataEntry;

                synchronized (this.loadedAPIs) {
                    apiDataEntry = this.loadedAPIs.getFirst();
                }

                final long apiTimeoutSec = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_api_timeout", new JsonPrimitive(60)).getAsLong();

                final double temperature = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_temperature", new JsonPrimitive(0.9)).getAsDouble();
                final int maxTokens = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_max_token", new JsonPrimitive(3072)).getAsInt();
                final double topP = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_top_p", new JsonPrimitive(1.0)).getAsDouble();
                final double frequencyPenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_frequency_penalty", new JsonPrimitive(0.0)).getAsDouble();
                final double presencePenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_presence_penalty", new JsonPrimitive(0.0)).getAsDouble();

                final OpenAiChatModel createdModel = OpenAiChatModel.builder()
                        .baseUrl(apiDataEntry.getBaseUrl())
                        .apiKey(apiDataEntry.getApiKey())
                        .modelName(apiDataEntry.getModelName())

                        .timeout(Duration.ofSeconds(apiTimeoutSec))

                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .topP(topP)
                        .frequencyPenalty(frequencyPenalty)
                        .presencePenalty(presencePenalty)

                        .build();

                this.conversationModels.put(dbMark, createdModel);

                return AiServices.builder(Assistant.class)
                        .chatLanguageModel(createdModel)
                        .chatMemoryProvider(chatMemoryProvider)
                        .systemMessageProvider(memoryId -> defaultPrompt)
                        .build();
            });

            if (this.shouldDelayNext) {
                this.shouldDelayNext = false;

                CompletableFuture.supplyAsync(() -> this.doConversationInternal(userMark, dbMark, content, delayIfError), this.delayedAIAPIDispatcher).whenComplete((f, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                        return;
                    }

                    f.whenComplete((f2, ex2) -> {
                        if (ex2 != null) {
                            future.completeExceptionally(ex2);
                            return;
                        }

                        future.complete(f2);
                    });
                });
            }

            if (!conversationLock.tryAcquire()) {
                logger.info("A conversation was already running, pushing to queue...");

                conversationQueue.offer(() -> this.doConversationInternal(userMark ,dbMark , content, delayIfError).whenComplete((f, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                        return;
                    }

                    future.complete(f);
                }));

                return future;
            }

            try {
                final String chatResult = aiBot.chat(userMark,userMark + " 说: " + content);

                synchronized (this.loadedAPIs) {
                    final OpenAiChatModel chatModel = this.conversationModels.get(dbMark);

                    if (chatModel != null) {
                        this.loadedAPIs.sort(Comparator.comparingInt((APIDataEntry o) -> o.usedCnt));

                        final APIDataEntry lessUsed = this.loadedAPIs.getFirst();
                        final OpenAiClient currentClient = getOpenAiClientOf(chatModel);

                        // Some black magic()
                        if (currentClient instanceof DefaultOpenAiClient defaultOpenAiClient) {
                            setBaseUrlOf(defaultOpenAiClient, lessUsed.getBaseUrl());
                            setKeyOf(defaultOpenAiClient, lessUsed.getApiKey());
                            setModelOf(chatModel, lessUsed.getModelName());
                        }
                    }
                }

                future.complete(chatResult);
            }catch (Exception e) {
                future.completeExceptionally(e);

                this.shouldDelayNext = delayIfError;
            }finally {
                conversationLock.release();

                if (!this.shouldDelayNext) this.delayedAIAPIDispatcher.execute(() -> this.processQueuedConversationsOnce(dbMark));
            }
        }catch (Exception e){
            future.completeExceptionally(e);
        }

        return future;
    }

    private void processQueuedConversationsOnce(String dbMark) {
        final Queue<Runnable> target = this.conversationQueues.get(dbMark);

        if (target == null) {
            return;
        }

        final Runnable call = target.poll();

        if (call != null) {
            call.run();
        }
    }

    public static @NotNull String removeThinkBlock(@NotNull String input) {
        String pattern = "<think>[\\s\\S]*?</think>";

        pattern = input
                .replaceAll(pattern, "")
                .replaceAll("[\\s\\S]*?</think>", "")
                .replaceAll("<think>", "");

        return pattern;
    }

    @Override
    public String name() {
        return "ai_convert";
    }


    interface Assistant {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    private record ProxyInterceptor(String key) implements Interceptor {
        @Override
        public @NotNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();

            builder.addHeader("Authorization", "Bearer " + this.key);
            builder.addHeader("api-key", this.key);

            return chain.proceed(builder.build());
        }
    }
}
