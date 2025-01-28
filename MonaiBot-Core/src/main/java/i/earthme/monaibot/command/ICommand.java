package i.earthme.monaibot.command;

import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;

public interface ICommand {
    void execute(MessageEvent originalEvent, ParsedCommandArgument commandArgument);

    default Contact senderFromEvent(MessageEvent event) {
        if (event instanceof GroupMessageEvent groupMessageEvent) {
            return groupMessageEvent.getGroup();
        }

        return event.getSender();
    }
}
