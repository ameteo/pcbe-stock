package pcbe.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import pcbe.stock.client.StockClient;
import pcbe.stock.server.StockServer;

public final class LogManager {
    private static final Logger clientLogger = Logger.getLogger(StockClient.class.getName());
    private static final Logger serverLogger = Logger.getLogger(StockServer.class.getName());
    private static final String logFileName = "pcbe.log";

    static {
        try {
            Files.deleteIfExists(Paths.get(logFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        clientLogger.setLevel(Level.ALL);
        clientLogger.addHandler(new LogFileHandler(StockClient.class.getSimpleName()));
        serverLogger.setLevel(Level.ALL);
        serverLogger.addHandler(new LogFileHandler(StockServer.class.getSimpleName()));
    }

    public static Logger getClientLogger() {
        return clientLogger;
    }

    public static Logger getServerLogger() {
        return serverLogger;
    }

    public static class LogFileHandler extends Handler {
        private String name;

        public LogFileHandler(String name) {
            this.name = name;
        }

        @Override
        public void publish(LogRecord record) {
            var message = name + ": " + record.getMessage() + System.lineSeparator();
            try {
                Files.write(Paths.get(logFileName), message.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.out.println("Could not write to " + logFileName + ". The message was: " + message);
            }
        }

        @Override
        public void flush() {
            // we are not buffering the output
        }

        @Override
        public void close() throws SecurityException {
            // we don't need to close any resources
        }

    }
}