package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.model.Response;
import pcbe.stock.server.StockServer;

public class StockClient implements Callable<String> {
    private final Logger logger = LogManager.getLogger();
    private final UUID id;
    private StockServer stockServer;

    /**
     * @param id A unique identifier for the client
     * @throws NullPointerException if <code>id</code> is <code>null</code>
     */
    public StockClient(UUID id) {
        this.id = requireNonNull(id);
    }

    /**
     * @return true if the client is registered to a server; false otherwise
     */
    public boolean isRegistered() {
        return nonNull(stockServer);
    }

    /**
     * Registers the client to the given server
     * @param stockServer
     * @throws NullPointerException if <code>stockServer</code> is <code>null</code>
     */
    public void registerTo(StockServer stockServer) {
        requireNonNull(stockServer);
        requireSuccessfulResponse(() -> stockServer.register(id));
        this.stockServer = stockServer;
        logger.info("Client " + id + " registered.");
    }

    /**
     * Client entry point. Will be called by an {@link Executor}  
     */
    @Override
    public String call() {
        if (!isRegistered())
            throw new RuntimeException("Client " + id + " not connected.");
        return "Client " + id + " done";
    }

    /**
     * Executes the supplied request and checks if the response is successful
     * @param request the request to be executed
     * @return the successful response
     * @throws RuntimeException if the request failed
     */
    private Response requireSuccessfulResponse(Supplier<Response> request) {
        var response = request.get();
        if (!response.isSuccessful())
            throw new RuntimeException(response.getErrorMessage());
        return response;
    }

    /**
     * Auto generated {@link #hashCode()}
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Auto generated {@link #equals(Object)}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StockClient other = (StockClient) obj;
        return id.equals(other.id);
    }
}