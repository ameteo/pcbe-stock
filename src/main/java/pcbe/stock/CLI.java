
package pcbe.stock;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import pcbe.stock.client.StockClientGenerator;
import pcbe.stock.server.StockServer;

public class CLI {
    public static void main(String[] args) throws IOException {
        var stockServer = new StockServer();
        var clients = StockClientGenerator.generateClients();
        SystemInitializer.initializeSystem(stockServer, clients);
        clients.forEach(client -> client.registerTo(stockServer));
        var executor = Executors.newCachedThreadPool();
        try {
            var futures = executor.invokeAll(clients);
            for (var future : futures)
                System.out.println(future.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        System.out.println("ENTER to stop the program.");
        while (System.in.read() != '\n');
        executor.shutdownNow();
    }
}
