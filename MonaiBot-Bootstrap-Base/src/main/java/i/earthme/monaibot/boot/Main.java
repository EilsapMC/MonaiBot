package i.earthme.monaibot.boot;

import i.earthme.monaibot.Bootstrapper;
import i.earthme.monaibot.boot.utils.ClassScanUtils;
import i.earthme.monaibot.command.ICommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final String COMMAND_PATH = "i.earthme.monaibot.function.commands";

    public static void main(String[] args) throws IOException {
        runBot();
    }

    public static void runBot() throws IOException {
        Bootstrapper.setPostBootstrap(Main::postBootstrapProcess);
        Bootstrapper.doBootstrap();
    }

    public static void postBootstrapProcess() {
        scanAndLoadCommands();
    }

    private static void scanAndLoadCommands() {
        final Set<Class<?>> scannedClasses = ClassScanUtils.scanClasses(COMMAND_PATH);

        for (Class<?> clazz : scannedClasses) {
            if (ICommand.class.isAssignableFrom(clazz)) {
                final Class<? extends ICommand> commandClass = (Class<? extends ICommand>) clazz;

                for (Method method : commandClass.getDeclaredMethods()) {
                    if (method.getName().equals("init") && Modifier.isStatic(method.getModifiers())) {
                        logger.info("Loading command: {}", commandClass.getSimpleName());
                        try {
                            method.setAccessible(true);
                            method.invoke(null);
                        }catch (Exception e){
                            logger.error("Failed to initialize command: {}", commandClass.getName(), e);
                        }
                    }
                }
            }
        }
    }
}