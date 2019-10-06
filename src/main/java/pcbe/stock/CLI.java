
package pcbe.stock;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import pcbe.stock.client.StockClientGenerator;
import pcbe.stock.server.StockServer;

public class CLI {
    private static final URI SERVER_URI = URI.create("http://localhost:8080/stock/");

    public static void main(String[] args) throws IOException {
        var stockServer = new StockServer(SERVER_URI);
        var clients = StockClientGenerator.generateClients();
        var executor = Executors.newCachedThreadPool();
        try {
            var futures = executor.invokeAll(clients);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        logger.info("ENTER to stop the program.");
        while (System.in.read() != '\n');
        stockServer.shutdownNow();
        executor.shutdownNow();
    }
}
