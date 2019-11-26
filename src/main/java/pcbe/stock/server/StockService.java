package pcbe.stock.server;

import static java.lang.System.lineSeparator;
import static java.util.Collections.unmodifiableList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static pcbe.UUIDUtil.prefixOf;
import static pcbe.stock.server.StockService.StockItemState.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

import pcbe.log.LogManager;
import pcbe.stock.Executor;
import pcbe.stock.model.Notifiers;
import pcbe.stock.model.StockItem;
import pcbe.stock.model.StockItem.Demand;
import pcbe.stock.model.StockItem.Offer;
import pcbe.stock.model.Transaction;

public class StockService {

    private static final Logger logger = LogManager.getServerLogger();
    private static StockService stockService = new StockService();
    private static final boolean FAIRNESS = true;

    public static StockService getDefault() {
        return stockService;
    }

    public static void reset() {
        stockService = new StockService();
    };

    private StockService() {}

    private final ReadWriteLock lock = new ReentrantReadWriteLock(FAIRNESS);
    private Map<UUID, Notifiers> clientNotifiers = new ConcurrentHashMap<>();
    private Map<StockItem, StockItemState> stockItems = new HashMap<>();
    private List<Transaction> transactions = new CopyOnWriteArrayList<>();

    public void configureNotifiers(UUID clientId, Notifiers notifiers) {
        clientNotifiers.put(clientId, notifiers);
    }

    public Set<Offer> getOffers() {
        return getItems(Offer.class, Waiting).stream().map(Offer::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    public Set<Demand> getDemands() {
        return getItems(Demand.class, Waiting).stream().map(Demand::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    public List<Transaction> getTransactions() {
        return unmodifiableList(transactions);
    }

    public UUID addDemand(UUID clientId, String company, int shares, double price) {
        var demand = new Demand(clientId, company, shares, price);
        addItem(demand);
        doTransactionsWithDemand(demand);
        return demand.getId();
    }

    public UUID addOffer(UUID clientId, String company, int shares, double price) {
        var offer = new Offer(clientId, company, shares, price);
        addItem(offer);
        doTransactionsWithOffer(offer);
        return offer.getId();
    }

    public void changeDemand(UUID demandId, int newShares, double newPrice) {
        var demand = changeItem(demandId, newShares, newPrice);
        doTransactionsWithDemand(Demand.class.cast(demand));
    }

    public void changeOffer(UUID offerId, int newShares, double newPrice) {
        var offer = changeItem(offerId, newShares, newPrice);
        doTransactionsWithOffer(Offer.class.cast(offer));
    }

    private void doTransactionsWithDemand(Demand demand) {
        Executor.getDefault().submit(() -> {
            for (Offer offer : getMatchingOffers(demand)) {
                if (offer.getShares() == 0)
                    continue;
                doTransaction(demand, offer);
                if (demand.getShares() == 0)
                    break;
            }
        });
    }

    private void doTransactionsWithOffer(Offer offer) {
        Executor.getDefault().submit(() -> {
            for (Demand demand : getMatchingDemands(offer)) {
                if (demand.getShares() == 0)
                    continue;
                doTransaction(demand, offer);
                if (offer.getShares() == 0)
                    break;
            }
        });
    }

    private Collection<Demand> getMatchingDemands(Offer offer) {
        return getItems(Demand.class, Waiting, Transaction).stream()
            .filter(demand -> match(demand, offer))
            .collect(toList());
    }

    private Collection<Offer> getMatchingOffers(Demand demand) {
        return getItems(Offer.class, Waiting, Transaction).stream()
            .filter(offer -> match(demand, offer))
            .collect(toList());
    }

    /**
     * Tries to complete a transaction between <code>demand</code> and
     * <code>offer</code>.
     * <p>
     * If the transaction cannot be completed because either <code>demand</code> or
     * <code>offer</code> is in another transaction, this method will return
     * <code>false</code> and will have no side effects. If the transaction cannot
     * be completed for other reasons, a <code>RuntimeException</code> will be
     * thrown.
     * <p>
     * If the transaction can be completed, the amount of traded shares is:
     * <p>
     * <b>Math.min(offer.getShares(), demand.getShares())</b>.
     * 
     * @return <code>true</code> if the transaction was made successfully,
     *         <code>false</code> otherwise
     * @throws RuntimeException will be thrown in the followin situations:
     *                          <ul>
     *                          <li>if the demand and offer do not match
     *                          <li>if the demand or offer does not exist
     *                          </ul>
     */
    private void doTransaction(Demand demand, Offer offer) {
        if (!makeSureTransactionIsPossibleAndSetStates(demand, offer))
            return;
        var company = demand.getCompany();
        var price = Math.min(offer.getPrice(), demand.getPrice());
        var demandClientId = demand.getClientId();
        var offerClientId = offer.getClientId();
        var tradedShares = Math.min(offer.getShares(), demand.getShares());
        logBeforeTransaction(demand, offer, tradedShares);
        offer.setShares(offer.getShares() - tradedShares);
        demand.setShares(demand.getShares() - tradedShares);
        var transaction = new Transaction(offerClientId, demandClientId, offer.getId(), demand.getId(), company, tradedShares, price);
        transactions.add(transaction);
        var partition = Stream.of(demand, offer).collect(partitioningBy(item -> item.getShares() == 0));
        var completeItems = partition.get(true);
        var incompleteItems = partition.get(false);
        setItemsStateToComplete(completeItems);
        setItemsStateToWaiting(incompleteItems);
        notifyClients(demandClientId, offerClientId, transaction);
        logAfterTransaction(transaction);
    }
    
    public boolean match(Demand demand, Offer offer) {
        return !demand.getClientId().equals(offer.getClientId())
            && demand.getCompany().equals(offer.getCompany())
            && demand.getPrice() == offer.getPrice();
    }

    private void setItemsStateToWaiting(List<StockItem> itemsToPutToWaiting) {
        doUnderWriteLock(() -> {
            itemsToPutToWaiting.forEach(item -> stockItems.put(item, Waiting));
        });
    }

    private void setItemsStateToComplete(List<StockItem> itemsToComplete) {
        doUnderWriteLock(() -> {
            if(itemsToComplete.stream().map(stockItems::get).anyMatch(not(Transaction::equals)))
                throw new RuntimeException("Only items in transaction can be completed.");
            itemsToComplete.forEach(item -> stockItems.put(item, Complete));
        });
    }

    public void removeItem(UUID itemId) {
        doUnderWriteLock(() -> {
            var optionalItem = stockItems.entrySet().stream()
                .filter(entry -> itemId.equals(entry.getKey().getId())).findAny();
            if (!optionalItem.isPresent())
                throw new RuntimeException(stringFrom("Cannot remove item ", itemId, " because it does not exist."));
            var item = optionalItem.get();
            switch (item.getValue()) {
                case Transaction:
                    throw new AlreadyInTransactionException();
                case Waiting:
                    stockItems.put(item.getKey(), Removed);
                    break; 
                case Removed: case Complete:
                    logger.fine(stringFrom("Trying to remove item ", itemId, " but item is ", item.getValue()));
            }
        });
    }

    private void notifyClients(UUID demandClientId, UUID offerClientId, Transaction transaction) {
        Executor.getDefault().submit(() -> clientNotifiers.get(offerClientId).saleNotifier().accept(transaction));
        Executor.getDefault().submit(() -> clientNotifiers.get(demandClientId).buyNotifier().accept(transaction));
    }

    private boolean makeSureTransactionIsPossibleAndSetStates(Demand demand, Offer offer) {
        return doUnderWriteLock(() -> {        
            if (!match(demand, offer)) {
                logger.info(getCannotMakeTransactionMessage(demand, offer, "demand and offer do not match"));
                return false;
            }
            if (!stockItems.containsKey(demand)) {
                logger.severe(getCannotMakeTransactionMessage(demand, offer, "demand does not exist"));
                return false;
            }
            if (!stockItems.containsKey(offer)) {
                logger.severe(getCannotMakeTransactionMessage(demand, offer, "offer does not exist"));
                return false;
            }
            var demandState = stockItems.get(demand);
            switch(demandState) {
                case Transaction: case Removed: case Complete:
                    logger.fine(getCannotMakeTransactionMessage(demand, offer, stringFrom("demand is is ", demandState)));
                    return false;
                case Waiting: 
                    break;
            };
            var offerState = stockItems.get(offer);
            switch(offerState) {
                case Transaction: case Removed: case Complete:
                    logger.fine(getCannotMakeTransactionMessage(demand, offer, stringFrom("offer is ", offerState)));
                    return false;
                case Waiting: 
                    break;
            };
            stockItems.put(demand, Transaction);
            stockItems.put(offer, Transaction);
            return true;
        });
    }

    private <T extends StockItem> Collection<T> getItems(Class<T> cls, StockItemState ... states) {
        return doUnderReadLock(() -> 
            stockItems.entrySet().stream()
                .filter(inStates(states))
                .map(Entry::getKey)
                .filter(cls::isInstance)
                .map(cls::cast)
                .collect(toList())
        );
    }

    private Predicate<Entry<StockItem, StockItemState>> inStates(StockItemState ... states) {
        return entry -> Arrays.stream(states).anyMatch(entry.getValue()::equals);
    }

    private void logAfterTransaction(Transaction transaction) {
        logger.info(stringFrom("Transaction complete: ", transaction));
    }

    private void logBeforeTransaction(Demand demand, Offer offer, int tradedShares) {
        logger.info(stringFrom(
            "Making a transaction for ", tradedShares, " between:", lineSeparator(),
            demand, lineSeparator(),
            offer
        ));
    }

	private void addItem(StockItem stockItem) {
        doUnderWriteLock(() -> {
            if (stockItems.containsKey(stockItem)) {
                throw new RuntimeException(stringFrom(
                    "Trying to add but item with id ", prefixOf(stockItem.getId()), " already exists.", lineSeparator(),
                    stockItem.getClass().getSimpleName(), ": ", stockItem
                ));
            }
            stockItems.put(stockItem, Waiting);
        });
    }

    private StockItem changeItem(UUID itemId, int newShares, double newPrice) {
        return doUnderWriteLock(() -> {
            var optionalItem = stockItems.entrySet().stream()
                .filter(e -> e.getKey().getId().equals(itemId))
                .findFirst();
            if (optionalItem.isEmpty())
                throw new RuntimeException(stringFrom("Trying to change item with id ", itemId, " but it does not exist"));
            var item = optionalItem.get();
            switch (item.getValue()) {
                case Transaction:
                    throw new AlreadyInTransactionException();
                case Waiting:
                    item.getKey().setShares(newShares);
                    item.getKey().setPrice(newPrice);
                    break;
                case Removed: case Complete:
                    logger.fine(stringFrom("Trying to remove item ", itemId, " but item is ", item.getValue()));
            }
            return item.getKey();
        });
    }

    private String getCannotMakeTransactionMessage(Demand demand, Offer offer, String reason) {
        return stringFrom(
            "Trying to make a transaction but ", reason, ".", lineSeparator(),
            demand, lineSeparator(),
            offer
        );
    }

    private String stringFrom(Object ... objects) {
        return Arrays.stream(objects).map(Object::toString).collect(joining());
    }

    private <T> T doUnderReadLock(Callable<T> action) {
        return doUnderLock(lock.readLock(), action);
    }

    private <T> T doUnderWriteLock(Callable<T> action) {
        return doUnderLock(lock.writeLock(), action);
    }

    private void doUnderWriteLock(Runnable action) {
        doUnderLock(lock.writeLock(), Executors.callable(action));
    }

    private <T> T doUnderLock(Lock lock, Callable<T> a) {
        lock.lock();
        try {
            return a.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    enum StockItemState { Waiting, Transaction, Removed, Complete }
}