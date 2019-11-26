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

    /**
     * Returns a set of {@link Offer}s that are <code>Waiting</code>.
     * <p>
     * The items returned are copies of the items in the <code>stockItems</code> map.
     * <p>
     * The returned set is unmodifiable.
     */
    public Set<Offer> getOffers() {
        return getItems(Offer.class, Waiting).stream().map(Offer::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    /**
     * Returns a set of {@link Demand}s that are <code>Waiting</code>.
     * <p>
     * The items returned are copies of the items in the <code>stockItems</code> map.
     * <p>
     * The returned set is unmodifiable.
     */
    public Set<Demand> getDemands() {
        return getItems(Demand.class, Waiting).stream().map(Demand::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    /**
     * Returns an unmodifiable list of complete {@link Transaction}s.
     */
    public List<Transaction> getTransactions() {
        return unmodifiableList(transactions);
    }

    /**
     * Creates a new {@link Demand} with the given parameters and adds it to the <code>stockItems</code> map.
     * <p>
     * After the {@link Demand} is added, possible transactions will be executed on a separate thread.
     * @return the id of the created {@link Demand}
     */
    public UUID addDemand(UUID clientId, String company, int shares, double price) {
        var demand = new Demand(clientId, company, shares, price);
        addItem(demand);
        logger.finest(stringFrom("New demand added: ", demand));
        doTransactionsWithDemand(demand);
        return demand.getId();
    }

    /**
     * Creates a new {@link Offer} with the given parameters and adds it to the <code>stockItems</code> map.
     * After the {@link Offer} is added, possible transactions will be executed on a separate thread.
     * @return the id of the created {@link Offer}
     */
    public UUID addOffer(UUID clientId, String company, int shares, double price) {
        var offer = new Offer(clientId, company, shares, price);
        addItem(offer);
        logger.finest(stringFrom("New offer added: ", offer));
        doTransactionsWithOffer(offer);
        return offer.getId();
    }

    /**
     * Updates the demand with the new parameters if possible.
     * After the {@link Demand} is changed, possible transactions will be executed on a separate thread.
     * @throws RuntimeException if a demand with the id <code>demandId</code> does not exist
     */
    public void changeDemand(UUID demandId, int newShares, double newPrice) {
        var demand = changeItem(demandId, newShares, newPrice);
        logger.finest(stringFrom("Demand changed: ", demand));
        doTransactionsWithDemand(Demand.class.cast(demand));
    }

    /**
     * Updates the offer with the new parameters if possible.
     * After the {@link Offer} is changed, possible transactions will be executed on a separate thread.
     * @throws RuntimeException if an offer with the id <code>offerId</code> does not exist
     */
    public void changeOffer(UUID offerId, int newShares, double newPrice) {
        var offer = changeItem(offerId, newShares, newPrice);
        logger.finest(stringFrom("Offer changed: ", offer));
        doTransactionsWithOffer(Offer.class.cast(offer));
    }

    /**
     * Starts a task that will try to do transactions with {@link Offer}s that
     * {@link this#match(Demand, Offer)} with the given {@link Demand}.
     * <p>
     * This method does not hold a write lock while iterating over the matching
     * offers, so the offers and the demand may be modified on a different thread.
     * <p>
     * In order to avoid doing transactions with demands or offers that have 0 shares,
     * each iteration will check that.
     */
    private void doTransactionsWithDemand(Demand demand) {
        Executor.getDefault().submit(() -> {
            for (Offer offer : getMatchingOffers(demand)) {
                if (demand.getShares() == 0)
                    break;
                if (offer.getShares() == 0)
                    continue;
                doTransaction(demand, offer);
                if (demand.getShares() == 0)
                    break;
            }
        });
    }

    /**
     * Starts a task that will try to do transactions with {@link Demand}s that
     * {@link this#match(Demand, Offer)} with the given {@link Offer}.
     * <p>
     * This method does not hold a write lock while iterating over the matching
     * demands, so the demands and the offer may be modified on a different thread.
     * <p>
     * In order to avoid doing transactions with demands or offers that have 0 shares,
     * each iteration will check that.
     */
    private void doTransactionsWithOffer(Offer offer) {
        Executor.getDefault().submit(() -> {
            for (Demand demand : getMatchingDemands(offer)) {
                if (offer.getShares() == 0)
                    break;
                if (demand.getShares() == 0)
                    continue;
                doTransaction(demand, offer);
                if (offer.getShares() == 0)
                    break;
            }
        });
    }

    /**
     * @return a collection of {@link Demand}s that are <code>Waiting</code>
     * or in <code>Transaction</code> that match with <code>offer</code>.
     */
    private Collection<Demand> getMatchingDemands(Offer offer) {
        return getItems(Demand.class, Waiting, Transaction).stream()
            .filter(demand -> match(demand, offer))
            .collect(toList());
    }

    /**
     * @return a collection of {@link Offers}s that are <code>Waiting</code>
     * or in <code>Transaction</code> that match with <code>demand</code>.
     */
    private Collection<Offer> getMatchingOffers(Demand demand) {
        return getItems(Offer.class, Waiting, Transaction).stream()
            .filter(offer -> match(demand, offer))
            .collect(toList());
    }

    /**
     * Tries to complete a transaction between <code>demand</code> and
     * <code>offer</code>.
     * <p>
     * The transaction cannot be completed if either <code>demand</code> and
     * <code>offer</code> do not match or they are not in the <code>Waiting</code> state.
     * Otherwise, <code>demand</code> and <code>offer</code> are put in the <code>Transaction</code>
     * state and the transaction is done.
     * <p>
     * After the transaction is complete, the items with 0 remaining shares are set to <code>Complete</code>
     * ,the other items are set to <code>Waiting</code> and the clients are notified.
     * <p>
     * If the transaction can be completed, the amount of traded shares is:
     * <p>
     * <b>Math.min(offer.getShares(), demand.getShares())</b>.
     * 
     * @throws RuntimeException if <code>demand</code> or <code>offer</code> are not in the <code>stockItems</code> map
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
    
    /**
     * Returns true if:
     * <ul>
     *  <li><code>demand</code> and <code>offer</code> have a different <code>clientId</code>
     *  <li><code>demand</code> and <code>offer</code> have the same <code>company</code>
     *  <li><code>demand</code> and <code>offer</code> have the same <code>price</code>
     * </ul>
     */
    public boolean match(Demand demand, Offer offer) {
        return !demand.getClientId().equals(offer.getClientId())
            && demand.getCompany().equals(offer.getCompany())
            && demand.getPrice() == offer.getPrice();
    }

    /**
     * Aquires the write lock and sets the state of each <code>itemToPutToWaiting</code> to <code>Waiting</code>.
     * @throws RuntimeException if any of the items are not in <code>Transaction</code>
     */
    private void setItemsStateToWaiting(List<StockItem> itemsToPutToWaiting) {
        doUnderWriteLock(() -> {
            if(itemsToPutToWaiting.stream().map(stockItems::get).anyMatch(not(Transaction::equals)))
                throw new RuntimeException("Only items in transaction can be put back to waiting.");
            itemsToPutToWaiting.forEach(item -> stockItems.put(item, Waiting));
        });
    }

    /**
     * Aquries the write lock and sets the state of each <code>itemToComplete</code> to <code>Complete</code>.
     * @throws RuntimeException if any of the items are not in <code>Transaction</code>
     */
    private void setItemsStateToComplete(List<StockItem> itemsToComplete) {
        doUnderWriteLock(() -> {
            if(itemsToComplete.stream().map(stockItems::get).anyMatch(not(Transaction::equals)))
                throw new RuntimeException("Only items in transaction can be completed.");
            itemsToComplete.forEach(item -> stockItems.put(item, Complete));
        });
    }

    /**
     * Aquires the write lock and sets the state of the item with id <code>itemId</code> to <code>Removed</code>.
     * <p>
     * If the item has already been removed or is complete, nothing is done.
     * 
     * @param itemId the id of the item
     * @throws AlreadyInTransactionException if the item with id <code>itemId</code> is in a transaction
     * @throws RuntimeException if no item in the <code>stockItems</code> map has the id <code>itemId</code>
     */
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
                    logger.finest(stringFrom("Item removed: ", item.getKey()));
                    break; 
                case Removed: case Complete:
                    logger.fine(stringFrom("Trying to remove item ", itemId, " but item is ", item.getValue()));
            }
        });
    }

    /**
     * Notifies the client with id <code>demandClientId</code> about his sale and client with id <code>offerClientId</code about his buy.
     */
    private void notifyClients(UUID demandClientId, UUID offerClientId, Transaction transaction) {
        Executor.getDefault().submit(() -> clientNotifiers.get(offerClientId).saleNotifier().accept(transaction));
        Executor.getDefault().submit(() -> clientNotifiers.get(demandClientId).buyNotifier().accept(transaction));
    }

    /**
     * Aquires the write lock, sets the state of <code>demand</code> and <code>offer</code> to <code>Transaction</code> and returns true.
     * The states will not be changed and the method will return false if:
     * <ul>
     *  <li><code>demand</code> and <code>offer</code> do not match
     *  <li><code>demand</code> or <code>offer</code> are in a state other than <code>Waiting</code>
     * </ul>
     * @throws RuntimeException if <code>demand</code> or <code>offer</code> are not keys of the <code>stockItems</code> map
     */
    private boolean makeSureTransactionIsPossibleAndSetStates(Demand demand, Offer offer) {
        return doUnderWriteLock(() -> {        
            if (!match(demand, offer)) {
                logger.fine(getCannotMakeTransactionMessage(demand, offer, "demand and offer do not match"));
                return false;
            }
            if (!stockItems.containsKey(demand))
                throw new RuntimeException(getCannotMakeTransactionMessage(demand, offer, "demand does not exist"));
            if (!stockItems.containsKey(offer))
                throw new RuntimeException(getCannotMakeTransactionMessage(demand, offer, "demand does not exist"));
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

    /**
     * Aquires the read lock and computes a collection of the items that:
     * <ul>
     *  <li>are in a state present in <code>states</code>
     *  <li>are an instance of <code>cls</code>
     * </ul>
     * The objects returned are keys of the <code>stockItems</code> map.
     * 
     * @param <T>
     * @param cls one of
     * <ul>
     *  <li><code>Offer.class</code>
     *  <li><code>Demand.class</code>
     *  <li><code>StockItem.class</code>
     * </ul>
     * @param states 
     */
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

    /**
     * Aquires the write lock and adds <code>stockItem</code> to the <code>stockItems</code> map.
     * @throws RuntimeException if the <code>stockItem</code> is already in the <code>stockItems</code> map.
     */
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

    /**
     * Aquires the write lock and updates the item <code>itemId</code> with a
     * new number of shares, <code>newShares</code> and a new price per share
     * <code>newPrice</code>.
     * <p>
     * If the item has been removed or is complete, the method will not update the item.
     * 
     * @param itemId the id of the item
     * @param newShares the new number of shares
     * @param newPrice the new price per share
     * @throws RuntimeException if no item in <code>stockItems</code> has the id <code>itemId</code>
     * @throws AlreadyInTransactionException if the item is in another transaction
     * @return the item with id <code>itemId</code>
     */
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

    /**
     * Executes the given {@link Callable} under <code>lock.readLock</code>
     * and returns the result.
     */
    private <T> T doUnderReadLock(Callable<T> action) {
        return doUnderLock(lock.readLock(), action);
    }

    /**
     * Executes the given {@link Callable} under <code>lock.writeLock</code>
     * and returns the result.
     */
    private <T> T doUnderWriteLock(Callable<T> action) {
        return doUnderLock(lock.writeLock(), action);
    }

    /**
     * Executes the given {@link Runnable} under <code>lock.writeLock</code>.
     */
    private void doUnderWriteLock(Runnable action) {
        doUnderLock(lock.writeLock(), Executors.callable(action));
    }

    /**
     * Executes <code>callable</code> after aquiring <code>lock</code> and returns the result.
     * {@link RuntimeException}s are passed to the caller and {@link Exception}s are wrapped in
     * a {@link RuntimeException} and rethrown.
     * <p>
     * This method will always release the aquired lock.
     */
    private <T> T doUnderLock(Lock lock, Callable<T> callable) {
        lock.lock();
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    enum StockItemState { 
        /**
         * The item can take part in transactions and can be changed.
         */
        Waiting,
        /**
         * The item is in a transaction.
         * <p>
         * It cannot take part in another transaction and cannot be changed.
         * <p>
         * An item that is <code>Waiting</code> can 
         */
        Transaction,
        /**
         * The item has been removed by the client.
         * <p>
         * It cannot take part in transactions and cannot be changed.
         */
        Removed,
        /**
         * The item has been has been 0 shares.
         * <p>
         * It cannot take part in transactions and cannot be changed.
         */
        Complete 
    }
}