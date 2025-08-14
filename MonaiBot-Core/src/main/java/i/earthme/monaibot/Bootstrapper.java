package i.earthme.monaibot;

import i.earthme.monaibot.command.impl.DebugCommand;
import i.earthme.monaibot.command.impl.ListCommandsCommand;
import i.earthme.monaibot.data.BotConfigDatabase;
import i.earthme.monaibot.events.Listener;
import i.earthme.monaibot.events.ListenerLine;
import i.earthme.monaibot.management.BotManagerLayer;
import i.earthme.monaibot.management.CommandRegistryManager;
import i.earthme.monaibot.management.FileBasedBotManager;
import i.earthme.monaibot.util.MonaiBotWorkerFactory;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.MessageEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Bootstrapper {
    private static File BASE_DIR = new File(".");
    private static File BOT_CONFIG_FILE = new File(BASE_DIR, "bot.json");
    private static File BOT_CONFIG_DATABASE_FILE = new File(BASE_DIR, "bot_sub_configs.json");

    public static final ExecutorService BOT_WORKER_THREAD_POOL = Executors.newCachedThreadPool(new MonaiBotWorkerFactory());

    public static final ListenerLine LISTENER_LINE = new ListenerLine(BOT_WORKER_THREAD_POOL);
    public static final CommandRegistryManager COMMAND_REGISTRY_MANAGER = new CommandRegistryManager();
    public static final BotConfigDatabase BOT_CONFIG_DATABASE = new BotConfigDatabase(BOT_WORKER_THREAD_POOL, BOT_CONFIG_DATABASE_FILE);
    public static final BotManagerLayer BOT_MANAGER = new FileBasedBotManager(
            bot -> bot.addEventListener(LISTENER_LINE::onEvent),
            BOT_WORKER_THREAD_POOL, BOT_CONFIG_FILE
    );

    private static Runnable POST_BOOTSTRAP = null;
    private static Runnable PRE_BOOTSTRAP = null;

    public static File getBaseDir() {
        return BASE_DIR;
    }

    public static void setBaseDir(File dir) {
        BASE_DIR = dir;
        BOT_CONFIG_FILE = new File(BASE_DIR, "bot.json");
        BOT_CONFIG_DATABASE_FILE = new File(BASE_DIR, "bot_sub_configs.json");
    }

    //Core layer entry point
    public static void doBootstrap() throws IOException {
        BOT_CONFIG_DATABASE.load();
        ((FileBasedBotManager) BOT_MANAGER).init();

        if (PRE_BOOTSTRAP != null) {
            PRE_BOOTSTRAP.run();
        }

        BOT_MANAGER.loadAllBotsAsync().whenComplete((unused, ex) -> {
            if (ex != null) {
                throw new RuntimeException(ex);
            }

            LISTENER_LINE.registerListener(MessageEvent.class, new Listener() {

                @Override
                public boolean processEvent(@NotNull Event event) {
                    COMMAND_REGISTRY_MANAGER.onMsgInComing(((MessageEvent) event));
                    return true;
                }

                @Override
                public String name() {
                    return "commanding";
                }
            });

            doPostBootstrap();
        });
    }

    private static void shutdownExecutors() throws InterruptedException {
        BOT_WORKER_THREAD_POOL.shutdown();
        while (!BOT_WORKER_THREAD_POOL.awaitTermination(1000, TimeUnit.MILLISECONDS));
    }

    public static void doShutdownProcess(){
        try {
            BOT_CONFIG_DATABASE.close();
            shutdownExecutors();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void doPostBootstrap() {
        DebugCommand.init();
        ListCommandsCommand.init();

        if (POST_BOOTSTRAP != null) {
            POST_BOOTSTRAP.run();
        }
    }

    public static void setPostBootstrap(Runnable runnable) {
        POST_BOOTSTRAP = runnable;
    }

    public static void setPreBootstrap(Runnable runnable) {
        PRE_BOOTSTRAP = runnable;
    }
}