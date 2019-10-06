package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.concurrent.Callable;

public class StockClient implements Callable<String> {
    private Requester requester;
    private String id;

    public StockClient(String id) {
        this.id = id;
    }

    public boolean isConnected() {
        return nonNull(requester);
    }

    public void connectTo(URI uri) {
        requireNonNull(uri);
        logger.info("Client " + id + " connecting to " + uri);
        requester = new Requester(uri);
    }

    public void disconnect() {
        requester = null;
    }

    @Override
    public String call() {
        if (!isConnected())
            throw new RuntimeException("Client " + id + " not connected.");
        return "Client " + id + " done";
    }
}