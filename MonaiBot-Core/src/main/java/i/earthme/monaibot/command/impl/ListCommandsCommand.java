package i.earthme.monaibot.command.impl;

import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.command.ICommand;
import i.earthme.monaibot.command.ParsedCommandArgument;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;

public class ListCommandsCommand implements ICommand {
    public static void init() {
        Bootstrapper.COMMAND_REGISTRY_MANAGER.registerCommand("lscommands", new ListCommandsCommand());
    }

    @Override
    public void execute(MessageEvent originalEvent, ParsedCommandArgument commandArgument) {
        final Contact feedBack = this.senderFromEvent(originalEvent);

        final StringBuilder built = new StringBuilder();
        built.append("Registed commands: \n");
        for (String commandName : Bootstrapper.COMMAND_REGISTRY_MANAGER.getRegistedNames()) {
            built.append(commandName).append("\n");
        }

        feedBack.sendMessage(built.toString());
    }
}
