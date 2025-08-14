package i.earthme.monaibot.events;

import net.mamoe.mirai.event.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class ListenerLine {
    private static final Logger logger = LogManager.getLogger(ListenerLine.class);

    private final ExecutorService worker;
    private final Map<Class<? extends Event>, Set<Listener>> listeners = new ConcurrentHashMap<>();

    public ListenerLine(ExecutorService worker) {
        this.worker = worker;
    }

    public void onEvent(@NotNull Event event) {
        final Set<Listener> listeners = this.getListeners(event.getClass());
        if (listeners != null) {
            // use array dequeue would be faster, and there is only 1 thread modify it, so
            // there is no need to concern its thread safety
            final ArrayDeque<Listener> eventPipeLine = new ArrayDeque<>(listeners);
            this.worker.execute(() -> {
                Listener listener;
                while ((listener = eventPipeLine.poll()) != null) {
                    try {
                        if (!listener.processEvent(event)) {
                            break;
                        }
                    }catch (Exception ex){
                        logger.error("An error occurred while processing event", ex);
                    }
                }
            });
        }
    }

    public Set<Listener> getListeners(@NotNull Class<? extends Event> eventClazz) {
        final Map.Entry<Class<? extends Event>, Set<Listener>> entry = this.listeners.entrySet().stream().filter(e -> e.getKey().isAssignableFrom(eventClazz)).findFirst().orElse(null);

        return entry == null ? null : entry.getValue();
    }

    @Nullable
    public Listener getByName(String name) {
        for (Set<Listener> listeners : this.listeners.values()) {
            for (Listener listener : listeners) {
                if (listener.name().equals(name)) {
                    return listener;
                }
            }
        }

        return null;
    }

    public <T extends Event> void registerListener(@NotNull Class<T> eventClazz, @NotNull Listener listener) {
        final Set<Listener> listeners = this.listeners.computeIfAbsent(eventClazz, k -> ConcurrentHashMap.newKeySet());

        if (!listeners.add(listener)) {
            logger.warn("Listener {} is already registered", listener.name());
        }
    }

    public void unregisterListener(@NotNull Listener listener) {
        for (Set<Listener> listeners : this.listeners.values()) {
            listeners.remove(listener);
        }
    }

    public <T extends Event> void unregisterListener(@NotNull Class<T> eventClazz, @NotNull Listener listener) {
        final Set<Listener> listeners = this.listeners.get(eventClazz);

        if (listeners == null) {
            return;
        }

        listeners.remove(listener);
    }
}
