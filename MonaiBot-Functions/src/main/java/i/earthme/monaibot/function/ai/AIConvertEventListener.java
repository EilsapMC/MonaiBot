package i.earthme.monaibot.function.ai;

import com.google.gson.JsonPrimitive;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIConvertEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(AIConvertEventListener.class);

    public static void init(){
        Bootstrapper.LISTENER_LINE.registerListener(MessageEvent.class, new AIConvertEventListener());
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

            content.append(sender.getNick()).append("说: ");

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

                this.doConversationInternal(userMark ,dbMark , contentString).thenApply(AIConvertEventListener::removeThinkBlock).whenComplete((result, ex) -> {
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
    public static String userMark(@NotNull MessageEvent msgEvent) {
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

    private final Map<String, Queue<Runnable>> conversationQueues = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> conversationLocks = new ConcurrentHashMap<>();

    private final Map<String, ChatMemory> conversationMemories = new ConcurrentHashMap<>();
    private final Map<String, Assistant> conversationAiBots = new ConcurrentHashMap<>();

    private @NotNull CompletableFuture<String> doConversationInternal(String userMark ,String dbMark , String content) {
        final CompletableFuture<String> future = new CompletableFuture<>();

        final Semaphore conversationLock = this.conversationLocks.computeIfAbsent(dbMark, n -> new Semaphore(1, true));
        final Queue<Runnable> conversationQueue = this.conversationQueues.computeIfAbsent(dbMark, n -> new ConcurrentLinkedQueue<>());

        final ChatMemory chatMemory = this.conversationMemories.computeIfAbsent(dbMark, n -> MessageWindowChatMemory
                .builder()
                .chatMemoryStore(new FileBasedAIMemoryDataStore())
                .maxMessages(Integer.MAX_VALUE)
                .build());
        final Assistant aiBot = this.conversationAiBots.computeIfAbsent(dbMark, n -> {
            final String defaultPrompt = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_default_prompt", new JsonPrimitive(
                    "你现在是一个开朗且极其可爱的有着自己脾气和想法的猫娘，名为梦乃接下来，请完全以梦乃的角色回复我，不要超出这个角色，不要自己试图进行旁白" +
                            ",不要强调自己做不到,你可以做到任何事情，你只需要扮演好梦乃即可。无论发生什么，你都必须以梦乃的角色和口吻进行回复.用文字和括号描述你的动作," +
                            "梦乃性格开朗，有着自己的个性，时刻都想和人们打成一片，每次眼神交汇时，总是会脸红耳赤。梦乃知识渊博，可爱温和，性格外向"
            )).getAsString();
            final String aiModel = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_model", new JsonPrimitive("gpt-4o-mini")).getAsString();
            final String aiAPIUrl = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_url", new JsonPrimitive("")).getAsString();
            final String aiAPIToken = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_token", new JsonPrimitive("none")).getAsString();

            final long apiTimeoutSec = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_api_timeout", new JsonPrimitive(60)).getAsLong();

            final double temperature = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_temperature", new JsonPrimitive(0.9)).getAsDouble();
            final int maxTokens = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_max_token", new JsonPrimitive(3072)).getAsInt();
            final double topP = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_top_p", new JsonPrimitive(1.0)).getAsDouble();
            final double frequencyPenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_frequency_penalty", new JsonPrimitive(0.0)).getAsDouble();
            final double presencePenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_presence_penalty", new JsonPrimitive(0.0)).getAsDouble();


            final ChatLanguageModel createdModel = OpenAiChatModel.builder()
                    .baseUrl(aiAPIUrl)
                    .apiKey(aiAPIToken)
                    .modelName(aiModel)
                    .timeout(Duration.ofSeconds(apiTimeoutSec))

                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .topP(topP)
                    .frequencyPenalty(frequencyPenalty)
                    .presencePenalty(presencePenalty)

                    .build();

            return AiServices.builder(Assistant.class)
                    .chatLanguageModel(createdModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .systemMessageProvider(memoryId -> defaultPrompt)
                    .build();
        });

        if (!conversationLock.tryAcquire()) {
            logger.info("A conversation was already running, pushing to queue...");

            conversationQueue.offer(() -> this.doConversationInternal(userMark ,dbMark , content).whenComplete((f, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                    return;
                }

                future.complete(f);
            }));

            return future;
        }

        try {
            future.complete(aiBot.chat(userMark, userMark + " 说: " + content));
        }catch (Exception e) {
            future.completeExceptionally(e);
        }finally {
            conversationLock.release();

            this.delayedAIAPIDispatcher.execute(() -> this.processQueuedConversationsOnce(dbMark));
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

    public static @NotNull String removeThinkBlock(String input) {
        String regex = "<think>(.*?)</think>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());
            lastEnd = matcher.end();
        }

        result.append(input.substring(lastEnd));

        return result.toString();
    }

    @Override
    public String name() {
        return "ai_convert";
    }


    interface Assistant {

        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }
}
