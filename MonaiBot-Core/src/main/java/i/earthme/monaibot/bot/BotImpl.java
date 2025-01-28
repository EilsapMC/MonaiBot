package i.earthme.monaibot.bot;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.Event;
import top.mrxiaom.overflow.BotBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class BotImpl {
    private final BotConfig config;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile Bot internalBot = null;
    private final Set<Consumer<Event>> eventListenersToRegister = new HashSet<>();

    public BotImpl(BotConfig config) {
        this.config = config;
    }

    private Bot createBot() {
        final BotBuilder builder = BotBuilder.positive(this.config.wsUrl());

        builder.modifyBotConfiguration(botConfiguration -> {
            if (!this.config.enableBotLog()) {
                botConfiguration.noBotLog();
                botConfiguration.noNetworkLog();
            }
        });

        return this.config.token().equals("null") ? builder.connect() : builder.token(this.config.token()).connect();
    }

    public BotConfig getConfig() {
        return this.config;
    }

    public Bot getRawBot() {
        return this.internalBot;
    }

    public void closeBot() {
        final Bot currValue = this.internalBot;

        if (currValue != null) {
            this.internalBot = null;

            currValue.close();
            this.connected.set(false);
        }
    }

    public void addEventListener(Consumer<Event> eventListener) {
        this.eventListenersToRegister.add(eventListener);
    }

    public void removeEventListener(Consumer<Event> eventListener) {
        this.eventListenersToRegister.remove(eventListener);
    }

    public void startBot() {
       this.internalBot = this.createBot();

       for (Consumer<Event> eventListener : this.eventListenersToRegister) {
           this.internalBot.getEventChannel().subscribeAlways(Event.class, eventListener);
       }

       this.connected.set(true);
    }

    public boolean isConnected() {
        return this.connected.get();
    }
}
