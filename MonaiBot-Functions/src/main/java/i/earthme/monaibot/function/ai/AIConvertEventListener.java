package i.earthme.monaibot.function.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import i.earthme.monaibot.Bootstrapper;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AIConvertEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(AIConvertEventListener.class);

    private final AIMemoryDatabase aiMemoryDatabase = new AIMemoryDatabase();
    private final Map<String, Object> conversationLocks = new ConcurrentHashMap<>();

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
                    content.append(plainText.contentToString());
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
                        logger.info("PRIVATE CHAT");
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

                final MemoryEntry result = this.doConversationInternal(databaseMark, content.toString());

                if (result != null) {
                    logger.info("[AIConvert] AI → {}. Content is {}", feedback.getId(), result.content());
                    final MessageChainBuilder msgBuilder = new MessageChainBuilder();

                    if (!privateChat) msgBuilder.add(new At(sender.getId()));
                    msgBuilder.add(new PlainText(result.content()));

                    final MessageChain built = msgBuilder.build();
                    feedback.sendMessage(built);
                }
            }
        }

        return true;
    }

    public static @NotNull String userMark(MessageEvent msgEvent) {
        final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();

        return feedback instanceof Group ? "grp-" + feedback.getId() : "oth-" + feedback.getId();
    }

    private @Nullable MemoryEntry doConversationInternal(String userMark, String content) {
        final Object conversationLock = this.conversationLocks.computeIfAbsent(userMark, n -> new Object());

        synchronized (conversationLock) {
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

            try {
                final MemoryEntry aiNewMemory = MemoryEntry.toMemoryEntry(this.requestAPI(memories));

                this.aiMemoryDatabase.logToMemory(userMark, aiNewMemory.role(), aiNewMemory.content());

                return aiNewMemory;
            }catch (Exception e){
                logger.error("Error while request llm api", e);
            }

            return null;
        }
    }

    private @NotNull JsonObject requestAPI(List<MemoryEntry> memories) throws Exception {
        final String aiModel = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_model", new JsonPrimitive("gpt-4o-mini")).getAsString();
        final String aiAPIUrl = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_url", new JsonPrimitive("")).getAsString();
        final String aiAPIToken = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_llm_api_token", new JsonPrimitive("none")).getAsString();

        final double temperature = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_temperature", new JsonPrimitive(0.9)).getAsDouble();
        final int maxTokens = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_max_token", new JsonPrimitive(3072)).getAsInt();
        final int topP = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_top_p", new JsonPrimitive(1)).getAsInt();
        final int frequencyPenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_frequency_penalty", new JsonPrimitive(0)).getAsInt();
        final int presencePenalty = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("ai_presence_penalty", new JsonPrimitive(0)).getAsInt();

        return MemoryEntry.requestAPI(aiAPIUrl, aiAPIToken, memories, aiModel, temperature, maxTokens, topP, frequencyPenalty, presencePenalty);
    }



    @Override
    public String name() {
        return "ai_convert";
    }
}
