package i.earthme.monaibot.management;

import i.earthme.monaibot.bot.BotConfig;
import i.earthme.monaibot.bot.BotImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class FileBasedBotManager extends BotManagerLayer{
    private static final Logger logger = LogManager.getLogger(FileBasedBotManager.class);
    private final File botConf;

    private BotConfig botConfig;

    public FileBasedBotManager(Consumer<BotImpl> botModifier, ExecutorService worker, File botConf) {
        super(botModifier, worker);
        this.botConf = botConf;
    }

    public void init() throws IOException {
        if (!botConf.exists()) {
            logger.info("Bot config file not found, creating a new one");
            final BotConfig created = new BotConfig(
                    "114514",
                    "ws://127.0.0.1:8080",
                    false,
                    UUID.randomUUID()
            );
            this.botConfig = created;
            Files.writeString(botConf.toPath(), this.botConfig.toJson());
            System.exit(0);
        }

        this.botConfig = BotConfig.fromJson(Files.readString(this.botConf.toPath()));
    }

    @Override
    protected BotConfig getBotConfigOf(UUID uuid) {
        return uuid.equals(this.botConfig.dataUUID()) ? this.botConfig : null;
    }

    @Override
    protected Collection<BotConfig> getAllBotConfig() {
        return List.of(this.botConfig);
    }
}
