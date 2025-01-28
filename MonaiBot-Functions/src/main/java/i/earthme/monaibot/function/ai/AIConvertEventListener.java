package i.earthme.monaibot.function.ai;

import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.events.Listener;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class AIConvertEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(AIConvertEventListener.class);

    public static void init(){
        Bootstrapper.LISTENER_LINE.registerListener(MessageEvent.class, new AIConvertEventListener());
    }

    @Override
    public boolean processEvent(@NotNull Event event) {
        if (event instanceof MessageEvent msgEvent) {
            final MessageChain msgChain = msgEvent.getMessage();

            boolean matched = false;
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

                if (searchIdx > 1 && message instanceof PlainText plainText) {
                    plainTxtCnt++;
                    content.append(plainText.contentToString());
                }

                searchIdx++;
            }

            if (plainTxtCnt < 1) {
                matched = false;
            }

            if (matched) {
                final Contact sender = msgEvent.getSender();
                final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();
                final String databaseMark = userMark(msgEvent);

                logger.info("[AIConvert] {}({}) → AI. Destination to {}({}) - {}", sender.getId(), sender.getId(), feedback.getId(), feedback.getId(), content);
            }
        }

        return true;
    }

    public static @NotNull String userMark(MessageEvent msgEvent) {
        final Contact feedback = msgEvent instanceof GroupMessageEvent gMsgEvent ? gMsgEvent.getGroup() : msgEvent.getSender();

        return feedback instanceof Group ? "grp-" + feedback.getId() : "oth-" + feedback.getId();
    }

    @Override
    public String name() {
        return "ai_convert";
    }
}
