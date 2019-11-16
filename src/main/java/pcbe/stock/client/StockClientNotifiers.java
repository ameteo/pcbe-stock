package pcbe.stock.client;

import java.util.function.Consumer;

import pcbe.stock.model.Notifiers;
import pcbe.stock.model.Transaction;
import pcbe.stock.server.StockService;

/**
 * This class provides two <code>Consumer&lt;Transaction&gt;</code> that act as
 * transaction notifiers: one for buys and one for sales.
 * <p>
 * This class was introduced so that the {@link StockService} class does not
 * interact directly with {@link StockClient}s
 * 
 * @see {@link StockClient#getNotifiers()}
 */
public class StockClientNotifiers implements Notifiers {
    private Consumer<Transaction> buyNotifier;
    private Consumer<Transaction> saleNotifier;

    /**
     * Create a <code>StockClientNotifiers</code> with the given consumer
     * 
     * @param buyNotifier the value that will be returned by {@link #buyNotifier()}
     * @param saleNotifier the value that will be returned by {@link #saleNotifier()}
     */
    public StockClientNotifiers(Consumer<Transaction> buyNotifier, Consumer<Transaction> saleNotifier) {
        this.buyNotifier = buyNotifier;
        this.saleNotifier = saleNotifier;
    }

    public Consumer<Transaction> buyNotifier() {
        return buyNotifier;
    }

    public Consumer<Transaction> saleNotifier() {
        return saleNotifier;
    }
}