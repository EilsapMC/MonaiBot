package i.earthme.monaibot.function;

import com.google.gson.JsonPrimitive;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.events.Listener;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class MiscGroupEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(MiscGroupEventListener.class);

    public static void init(){
        Bootstrapper.LISTENER_LINE.registerListener(BotInvitedJoinGroupRequestEvent.class, new MiscGroupEventListener());
    }

    @Override
    public boolean processEvent(@NotNull Event event) {
        if (event instanceof BotInvitedJoinGroupRequestEvent inviteEvent) {
            final boolean shouldAccept = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("auto_accept_group_invite", new JsonPrimitive(false)).getAsBoolean();

            if (shouldAccept) {
                return true;
            }

            final Friend invitor = inviteEvent.getInvitor();

            logger.info("Received group invite request from {} in group {}", invitor == null ? "[REMOVED]" : invitor.getId(), inviteEvent.getGroupId());
            inviteEvent.accept();
        }

        return true;
    }

    @Override
    public String name() {
        return "group_misc";
    }
}
