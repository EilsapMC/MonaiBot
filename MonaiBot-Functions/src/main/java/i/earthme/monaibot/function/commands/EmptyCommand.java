package i.earthme.monaibot.function.commands;

import i.earthme.monaibot.command.ICommand;
import i.earthme.monaibot.command.ParsedCommandArgument;
import i.earthme.monaibot.function.MiscGroupEventListener;
import i.earthme.monaibot.function.ai.AIConvertEventListener;
import net.mamoe.mirai.event.events.MessageEvent;

public class EmptyCommand implements ICommand {
    public static void init() {
        MiscGroupEventListener.init();
        AIConvertEventListener.init();
    }

    @Override
    public void execute(MessageEvent originalEvent, ParsedCommandArgument commandArgument) {

    }
}
