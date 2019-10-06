package pcbe.stock.client;

import java.util.concurrent.Callable;

public class StockClient implements Callable<String> {
    private String id;

    public StockClient(String id) {
        this.id = id;
    }

    @Override
    public String call() {
        return "Client " + id + " done";
    }
}