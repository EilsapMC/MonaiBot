package i.earthme.monaibot.management;

import i.earthme.monaibot.command.ICommand;
import i.earthme.monaibot.command.ParsedCommandArgument;
import net.mamoe.mirai.event.events.MessageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandRegistryManager {
    private static final Logger logger = LogManager.getLogger(CommandRegistryManager.class);
    private final Map<String, ICommand> registedCommands = new ConcurrentHashMap<>();

    public void registerCommand(String commandName, ICommand command) {
        synchronized (this.registedCommands){
            if (this.registedCommands.containsKey(commandName)) {
                logger.warn("Command {} already registered, ignored.", commandName);
            }

            this.registedCommands.put(commandName, command);
        }
    }

    public String[] getRegistedNames() {
        synchronized (this.registedCommands){
            return this.registedCommands.keySet().toArray(new String[0]);
        }
    }

    public void deregisterCommand(String commandName) {
        synchronized (this.registedCommands){
            this.registedCommands.remove(commandName);
        }
    }

    public void onMsgInComing(@NotNull MessageEvent msgEvent) {
        final ParsedCommandArgument parsedCommandArgument = ParsedCommandArgument.tryParseFromMessageChain(msgEvent.getMessage());

        if (parsedCommandArgument != null) {
            final String commandHead = parsedCommandArgument.calledCommand();
            final ICommand command = this.registedCommands.get(commandHead);

            if (command != null) {
                logger.info("Command {} was called from {}, argument: {}", commandHead, msgEvent, parsedCommandArgument);
                try {
                    command.execute(msgEvent, parsedCommandArgument);
                }catch (Exception e){
                    logger.error("Error while executing command {}.", commandHead, e);
                }
            }
        }
    }
}
