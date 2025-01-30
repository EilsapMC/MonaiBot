package i.earthme.monaibot.command;

import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record ParsedCommandArgument(String calledCommand, String[] arguments,
                                    Map<Class<? extends Message>, List<Message>> argumentsMap) {
    public static final String COMMAND_PREFIX = "#";
    public static final String COMMAND_SEPARATOR = " ";

    public static @Nullable ParsedCommandArgument tryParseFromMessageChain(@NotNull MessageChain messages) {
        if (messages.isEmpty()) {
            return null;
        }

        if (messages.size() == 1) {
            return null;
        }

        String commandHead = null;
        final List<String> arguments = new ArrayList<>();
        final Map<Class<? extends Message>, List<Message>> argumentsMap = new HashMap<>();

        int index = 0;
        for (Message msg : messages) {
            if (msg instanceof PlainText plainText && index == 1) {
                commandHead = plainText.contentToString();

                if (commandHead.isEmpty()) {
                    return null;
                }

                if (!commandHead.startsWith(COMMAND_PREFIX) || commandHead.length() <= COMMAND_PREFIX.length()) {
                    return null;
                }

                commandHead = commandHead.substring(COMMAND_PREFIX.length());

                final String[] split = split(commandHead);

                if (split == null) {
                    return null;
                }

                commandHead = split[0];

                if (split.length > 1) {
                    arguments.addAll(Arrays.asList(split).subList(1, split.length));
                }
                continue;
            }

            if (msg instanceof PlainText plainText) {
                final String[] split = split(plainText.contentToString());

                if (split == null) {
                    continue;
                }

                arguments.addAll(Arrays.asList(split));
            }

            argumentsMap.computeIfAbsent(msg.getClass(), k -> new ArrayList<>()).add(msg);

            index++;
        }

        if (commandHead == null) {
            return null;
        }

        return new ParsedCommandArgument(commandHead, arguments.toArray(new String[0]), argumentsMap);
    }

    @Nullable
    @Contract(pure = true)
    private static String [] split(@NotNull String input) {
        final String[] split = input.split(COMMAND_SEPARATOR);

        if (split.length == 0) {
            return null;
        }

        return split;
    }

    public String toString() {
        return "CommandArgumentParser{" +
                "calledCommand='" + calledCommand + '\'' +
                ", arguments=" + Arrays.toString(arguments) +
                ", argumentsMap=" + argumentsMap +
                '}';
    }
}
