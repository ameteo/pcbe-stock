package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.model.request.RegisterRequest;
import pcbe.stock.server.StockServer;

public class StockClient implements Callable<String> {
    private final Logger logger = LogManager.getLogger();
    private String id;
    private StockServer stockServer;

    public StockClient(String id) {
        this.id = id;
    }

    public boolean isConnected() {
        return nonNull(stockServer);
    }

    public void connectTo(StockServer stockServer) {
        requireNonNull(stockServer);
        this.stockServer = stockServer;
        logger.info("Client " + id + " connecting to stock server.");
    }

    public void disconnect() {
        stockServer = null;
    }

    @Override
    public String call() {
        if (!isConnected())
            throw new RuntimeException("Client " + id + " not connected.");
        var registerResponse = stockServer.register(RegisterRequest.withId(id));
        if (!registerResponse.isSuccessful())
            return "Client " + id + " failed to register with error: " + registerResponse.getError();
        return "Client " + id + " done";
    }
}