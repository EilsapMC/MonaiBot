package i.earthme.monaibot.management;

import i.earthme.monaibot.bot.BotConfig;
import i.earthme.monaibot.bot.BotImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class BotManagerLayer {
    private static final Logger logger = LogManager.getLogger(BotManagerLayer.class);

    private final ExecutorService worker;
    private final Consumer<BotImpl> botModifier;
    private final Map<UUID, BotImpl> loadedBots = new ConcurrentHashMap<>();

    protected BotManagerLayer(Consumer<BotImpl> botModifier, ExecutorService worker) {
        this.worker = worker;
        this.botModifier = botModifier;
    }

    public BotImpl getBot(UUID botUUID) {
        return this.loadedBots.get(botUUID);
    }

    public void closeBot(UUID botUUID) {
        final BotImpl removed = this.loadedBots.remove(botUUID);

        if (removed != null) {
            removed.closeBot();
            return;
        }

        logger.warn("Bot {} not found for closing!", botUUID);
    }

    public void loadBot(BotConfig botConfig) {
        final BotImpl created = new BotImpl(botConfig);

        try {
            this.botModifier.accept(created);
        }catch (Exception e) {
            logger.error("Failed to modify bot {}! Full config content: {}", botConfig.dataUUID(), botConfig.toJson(), e);
            return;
        }

        created.startBot();

        if (!created.isConnected()) {
            logger.warn("Ignoring unconnected bot : {}!", botConfig.dataUUID());
            return;
        }

        logger.info("Loaded bot {}", botConfig.dataUUID());

        if (this.loadedBots.containsKey(botConfig.dataUUID())) {
            logger.warn("Bot {} already loaded!", botConfig.dataUUID());
            return;
        }

        this.loadedBots.put(botConfig.dataUUID(), created);
    }

    public CompletableFuture<Void> loadAllBotsAsync() {
        final Collection<BotConfig> bots = new ArrayList<>(this.getAllBotConfig());
        final CompletableFuture<Void> callback = new CompletableFuture<>();
        final AtomicInteger taskCounter = new AtomicInteger(bots.size());

        for (BotConfig botConfig : bots) {
            this.worker.execute(() -> {
                try {
                    this.loadBot(botConfig);
                }catch (Exception e) {
                    logger.error("Failed to load bot {}! Full config content: {}", botConfig.dataUUID(), botConfig.toJson(), e);
                }finally {
                    if (taskCounter.decrementAndGet() <= 0) {
                        callback.complete(null);
                    }
                }
            });
        }

        return callback;
    }

    public Collection<BotImpl> getAllLoadedBots() {
        final List<BotImpl> ret = new ArrayList<>();

        for (BotImpl bot : this.loadedBots.values()) {
            if (bot.isConnected()) {
                ret.add(bot);
            }
        }

        return ret;
    }

    protected abstract BotConfig getBotConfigOf(UUID uuid);

    protected abstract Collection<BotConfig> getAllBotConfig();
}
