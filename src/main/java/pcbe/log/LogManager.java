package pcbe.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import pcbe.stock.client.StockClient;
import pcbe.stock.server.StockServer;

public final class LogManager {
    private static final Logger clientLogger = Logger.getLogger(StockClient.class.getName());
    private static final Logger serverLogger = Logger.getLogger(StockServer.class.getName());
    private static final String logFileName = "pcbe.log";

    static {
        try {
            Files.deleteIfExists(Paths.get(logFileName));
            var logFileHandler = new FileHandler(logFileName);
            logFileHandler.setFormatter(new SimpleFormatter());
            clientLogger.addHandler(logFileHandler);
            serverLogger.addHandler(logFileHandler);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> logFileHandler.close()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        clientLogger.setLevel(Level.ALL);
        serverLogger.setLevel(Level.ALL);
    }

    public static Logger getClientLogger() {
        return clientLogger;
    }

    public static Logger getServerLogger() {
        return serverLogger;
    }
}