package pcbe.log;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogManager {
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    
    static {
        setLevel(Level.ALL);
    }

    public static void setLevel(Level level) {
        logger.setLevel(level);
    }

    public static Logger getLogger() {
        return logger;
    }
}