package i.earthme.monaibot.command.impl;

import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.command.ICommand;
import i.earthme.monaibot.command.ParsedCommandArgument;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;

public class DebugCommand implements ICommand {
    public static void init() {
        Bootstrapper.COMMAND_REGISTRY_MANAGER.registerCommand("debug", new DebugCommand());
    }

    @Override
    public void execute(MessageEvent originalEvent, ParsedCommandArgument commandArgument) {
        final Contact feedBack = this.senderFromEvent(originalEvent);

        feedBack.sendMessage("Scanned command argument is " + commandArgument);
    }
}
