package pcbe.stock.model;

import java.util.function.Consumer;

public interface Notifiers {
    Consumer<Transaction> buyNotifier();
    Consumer<Transaction> saleNotifier();
}