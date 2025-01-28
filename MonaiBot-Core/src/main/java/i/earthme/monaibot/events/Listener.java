package i.earthme.monaibot.events;

import net.mamoe.mirai.event.Event;
import org.jetbrains.annotations.NotNull;

public interface Listener{
    boolean processEvent(@NotNull Event event);

    String name();
}
