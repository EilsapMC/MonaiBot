package i.earthme.monaibot.command;

import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record ParsedCommandArgument(String calledCommand, String[] arguments,
                                    Map<Class<? extends Message>, List<Message>> otherTypeArguments) {
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
        final Map<Class<? extends Message>, List<Message>> otherTypeArguments = new HashMap<>();

        int relativePlainTextIndex = 0;
        for (Message msg : messages) {
            if (msg instanceof PlainText plainText && relativePlainTextIndex == 0) {
                relativePlainTextIndex++;
                commandHead = plainText.contentToString();

                if (commandHead.isEmpty()) {
                    return null;
                }

                if (!commandHead.startsWith(COMMAND_PREFIX) || commandHead.length() == COMMAND_PREFIX.length()) {
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
                relativePlainTextIndex++;

                final String[] split = split(plainText.contentToString());

                if (split == null) {
                    continue;
                }

                arguments.addAll(Arrays.asList(split));
                continue;
            }

            otherTypeArguments.computeIfAbsent(msg.getClass(), k -> new ArrayList<>()).add(msg);
        }

        if (commandHead == null) {
            return null;
        }

        return new ParsedCommandArgument(commandHead, arguments.toArray(new String[0]), otherTypeArguments);
    }

    @Nullable
    @Contract(pure = true)
    private static String @Nullable [] split(@NotNull String input) {
        final String[] split = input.split(COMMAND_SEPARATOR);

        if (split.length == 0) {
            return null;
        }

        return split;
    }

    @NotNull
    public String toString() {
        return "i.earthme.monaibot.command.CommandArgumentParser{" +
                "calledCommand='" + calledCommand + '\'' +
                ", arguments=" + Arrays.toString(arguments) +
                ", otherTypeArguments=" + otherTypeArguments +
                '}';
    }
}
