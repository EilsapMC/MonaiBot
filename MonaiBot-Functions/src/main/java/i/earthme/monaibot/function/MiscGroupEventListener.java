package i.earthme.monaibot.function;

import com.google.gson.JsonPrimitive;
import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.events.Listener;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent;
import net.mamoe.mirai.event.events.FriendAddEvent;
import net.mamoe.mirai.event.events.NewFriendRequestEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class MiscGroupEventListener implements Listener {
    private static final Logger logger = LogManager.getLogger(MiscGroupEventListener.class);

    public static void init(){
        final MiscGroupEventListener listener = new MiscGroupEventListener();

        Bootstrapper.LISTENER_LINE.registerListener(BotInvitedJoinGroupRequestEvent.class, listener);
        Bootstrapper.LISTENER_LINE.registerListener(NewFriendRequestEvent.class, listener);
    }

    @Override
    public boolean processEvent(@NotNull Event event) {
        if (event instanceof BotInvitedJoinGroupRequestEvent inviteEvent) {
            final boolean shouldAccept = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("auto_accept_group_invite", new JsonPrimitive(false)).getAsBoolean();
            final Friend invitor = inviteEvent.getInvitor();

            logger.info("Received group invite request from {} in group {}", invitor == null ? "[REMOVED]" : invitor.getId(), inviteEvent.getGroupId());

            if (!shouldAccept) {
                return true;
            }

            logger.info("Accepted group invite request from {} in group {}", invitor == null ? "[REMOVED]" : invitor.getId(), inviteEvent.getGroupId());
            inviteEvent.accept();
        }

        if (event instanceof NewFriendRequestEvent newFriendRequestEvent) {
            final boolean shouldAccept = Bootstrapper.BOT_CONFIG_DATABASE.getOrElse("auto_accept_friend_request", new JsonPrimitive(false)).getAsBoolean();

            logger.info("Received friend request from {}", newFriendRequestEvent.getFromId());
            if (shouldAccept) {
                logger.info("Accepted friend request from {}", newFriendRequestEvent.getFromId());
                newFriendRequestEvent.accept();
            }
        }

        return true;
    }

    @Override
    public String name() {
        return "group_misc";
    }
}
