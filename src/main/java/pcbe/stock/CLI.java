
package pcbe.stock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import pcbe.stock.client.StockClient;
import pcbe.stock.client.StockClientGenerator;
import pcbe.stock.server.StockServer;

public class CLI {
    public static void main(String[] args) throws IOException {
        var stockServer = new StockServer();
        var clients = StockClientGenerator.generateClients();
        SystemInitializer.initializeSystem(stockServer, clients);
        clients.forEach(client -> client.registerTo(stockServer));
        List<ClientRunner> clientRunners = buildClientRunners(clients);
        try {
            var futures = Executor.getDefault().invokeAll(clientRunners);
            for (var future : futures)
                future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        System.out.println("ENTER to stop the program.");
        while (System.in.read() != '\n');
        Executor.getDefault().shutdownNow();
        Executor.cancelTimers();
    }

    private static List<ClientRunner> buildClientRunners(Collection<StockClient> clients) {
        var firstBatchOfClients = new ArrayList<StockClient>();
        var secondBatchOfClients = new ArrayList<StockClient>();
        distributeClientsEvenly(clients, firstBatchOfClients, secondBatchOfClients);
        var firstClientRunner = new ClientRunner(firstBatchOfClients);
        var secondClientRunner = new ClientRunner(secondBatchOfClients, 5, TimeUnit.SECONDS);
        List<ClientRunner> of = List.of(firstClientRunner, secondClientRunner);
        return of;
    }

    private static void distributeClientsEvenly(Collection<StockClient> clients, List<StockClient> firstBatchOfClients, List<StockClient> secondBatchOfClients) {
        for (var client : clients)
            if (Math.random() > .5)
                firstBatchOfClients.add(client);
            else
                secondBatchOfClients.add(client);
    }

    private static class ClientRunner implements Callable<Void> {
        private Collection<StockClient> clients;
        private Long delay;
        private TimeUnit timeUnit;

        public ClientRunner(Collection<StockClient> clients) {
            this.clients = clients;
        }

        public ClientRunner(Collection<StockClient> clients, long delay, TimeUnit timeUnit) {
            this(clients);
            this.delay = delay;
            this.timeUnit = timeUnit;
        }

        @Override
        public Void call() {
            try {
                if (delay != null && timeUnit != null)
                    Thread.sleep(timeUnit.toMillis(delay));
                var futures = Executor.getDefault().invokeAll(clients);
                for (var future : futures)
                    System.out.println(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
