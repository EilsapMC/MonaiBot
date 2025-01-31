package i.earthme.monaibot.function.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.command.ParsedCommandArgument;
import i.earthme.monaibot.events.Listener;
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

import java.util.List;
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
                final String databaseMark = userMark(msgEvent);

                logger.info("[AIConvert] {}({}) → AI. Destination to {}({}) - {}.User mark is {}", sender.getId(), sender.getId(), feedback.getId(), feedback.getId(), content, databaseMark);

                final boolean finalPrivateChat = privateChat;
                this.doConversationInternal(databaseMark, content.toString()).whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("[AIConvert] AI → {}. Error occurred.", feedback.getId(), ex);
                        feedback.sendMessage(new PlainText("An error has been occurred. Please try again later. Stack trace: \n" + ex.getLocalizedMessage()));
                        return;
                    }

                    if (result != null) {
                        logger.info("[AIConvert] AI → {}. Content is {}", feedback.getId(), result.content());
                        final MessageChainBuilder msgBuilder = new MessageChainBuilder();

                        if (!finalPrivateChat) msgBuilder.add(new At(sender.getId()));
                        msgBuilder.add(new PlainText(result.content()));

                        final MessageChain built = msgBuilder.build();
                        feedback.sendMessage(built);
                    }
                });
            }
        }

        return true;
    }

    // AI Conversation logics
    public static @NotNull String userMark(MessageEvent msgEvent) {
        final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();

        return feedback instanceof Group ? "grp-" + feedback.getId() : "oth-" + feedback.getId();
    }

    private final AIMemoryDatabase aiMemoryDatabase = new AIMemoryDatabase();

    private final Map<String, Queue<Runnable>> conversationQueues = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> conversationLocks = new ConcurrentHashMap<>();

    private @NotNull CompletableFuture<MemoryEntry> doConversationInternal(String userMark, String content) {
        final CompletableFuture<MemoryEntry> future = new CompletableFuture<>();

        final Semaphore conversationLock = this.conversationLocks.computeIfAbsent(userMark, n -> new Semaphore(1, true));
        final Queue<Runnable> conversationQueue = this.conversationQueues.computeIfAbsent(userMark, n -> new ConcurrentLinkedQueue<>());

        if (!conversationLock.tryAcquire()) {
            logger.info("A conversation was already running, pushing to queue...");

            conversationQueue.offer(() -> this.doConversationInternal(userMark, content).whenComplete((f, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                    return;
                }

                future.complete(f);
            }));

            return future;
        }

        try {
            final List<MemoryEntry> memories = this.aiMemoryDatabase.getProcessedContext(userMark);

            final String defaultPrompt = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_default_prompt", new JsonPrimitive(
                    "你现在是一个开朗且极其可爱的有着自己脾气和想法的猫娘，名为梦乃接下来，请完全以梦乃的角色回复我，不要超出这个角色，不要自己试图进行旁白" +
                            ",不要强调自己做不到,你可以做到任何事情，你只需要扮演好梦乃即可。无论发生什么，你都必须以梦乃的角色和口吻进行回复.用文字和括号描述你的动作," +
                            "梦乃性格开朗，有着自己的个性，时刻都想和人们打成一片，每次眼神交汇时，总是会脸红耳赤。梦乃知识渊博，可爱温和，性格外向"
            )).getAsString();

            if (memories.isEmpty() && !defaultPrompt.isBlank()) {
                final MemoryEntry newSystemMemory = new MemoryEntry("system", defaultPrompt);

                memories.add(newSystemMemory);

                this.aiMemoryDatabase.logToMemory(userMark, newSystemMemory.role(), newSystemMemory.content());
            }

            final MemoryEntry newMemory = new MemoryEntry("user", content);

            memories.add(newMemory);

            this.aiMemoryDatabase.logToMemory(userMark, newMemory.role(), newMemory.content());

            this.requestAPI(memories).thenApply(response -> {
                logger.info("Got llmapi response: {}", response);

                final MemoryEntry aiNewMemory = MemoryEntry.toMemoryEntry(response);

                final MemoryEntry modified = new MemoryEntry(aiNewMemory.role(), removeThinkBlock(aiNewMemory.content()));

                this.aiMemoryDatabase.logToMemory(userMark, modified.role(), modified.content());

                return modified;
            }).whenComplete((result, ex) -> {
                conversationLock.release();

                if (ex != null) {
                    future.completeExceptionally(ex);
                    return;
                }

                future.complete(result);

                this.processQueuedConversations(userMark);
            });
        }catch (Exception ex) {
            future.completeExceptionally(ex);
            conversationLock.release();

            this.processQueuedConversations(userMark);
        }

        return future;
    }

    private void processQueuedConversations(String userMark) {
        final Queue<Runnable> target = this.conversationQueues.get(userMark);

        if (target == null) {
            return;
        }

        Runnable call;
        while ((call = target.poll()) != null) {
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

    private @NotNull CompletableFuture<JsonObject> requestAPI(List<MemoryEntry> memories) throws Exception {
        final String aiModel = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_model", new JsonPrimitive("gpt-4o-mini")).getAsString();
        final String aiAPIUrl = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_url", new JsonPrimitive("")).getAsString();
        final String aiAPIToken = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_token", new JsonPrimitive("none")).getAsString();

        final double temperature = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_temperature", new JsonPrimitive(0.9)).getAsDouble();
        final int maxTokens = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_max_token", new JsonPrimitive(3072)).getAsInt();
        final int topP = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_top_p", new JsonPrimitive(1)).getAsInt();
        final int frequencyPenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_frequency_penalty", new JsonPrimitive(0)).getAsInt();
        final int presencePenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_presence_penalty", new JsonPrimitive(0)).getAsInt();

        final long apiTimeoutNs = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_api_timeout", new JsonPrimitive(TimeUnit.SECONDS.toSeconds(60))).getAsLong();

        return MemoryEntry.requestAPIAsync(apiTimeoutNs, aiAPIUrl, aiAPIToken, memories, aiModel, temperature, maxTokens, topP, frequencyPenalty, presencePenalty);
    }



    @Override
    public String name() {
        return "ai_convert";
    }
}
