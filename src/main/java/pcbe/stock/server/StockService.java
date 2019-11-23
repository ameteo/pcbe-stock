package pcbe.stock.server;

import static java.lang.System.lineSeparator;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private Map<UUID, Notifiers> clientNotifiers = Collections.synchronizedMap(new HashMap<>());
    private Map<StockItem, StockItemState> stockItems = new HashMap<>();
    private List<Transaction> transactions = new CopyOnWriteArrayList<>();

    public void configureNotifiers(UUID clientId, Notifiers notifiers) {
        clientNotifiers.put(clientId, notifiers);
    }

    public Set<Offer> getOffers() {
        return getItemsAs(Offer.class).stream().map(Offer::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    public Set<Demand> getDemands() {
        return getItemsAs(Demand.class).stream().map(Demand::new)
                .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));
    }

    public List<Transaction> getTransactions() {
        return unmodifiableList(transactions);
    }

    public void addDemand(Demand demand) {
        addItem(demand);
        doTransactionsWithDemand(demand);
    }

    public void addOffer(Offer offer) {
        addItem(offer);
        doTransactionsWithOffer(offer);
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
        return getMatchingItemsAs(offer, Demand.class);
    }

    private Collection<Offer> getMatchingOffers(Demand demand) {
        return getMatchingItemsAs(demand, Offer.class);
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
        var price = demand.getPrice();
        var demandClientId = demand.getClientId();
        var offerClientId = offer.getClientId();
        var tradedShares = Math.min(offer.getShares(), demand.getShares());
        logBeforeTransaction(demand, offer, tradedShares);
        offer.setShares(offer.getShares() - tradedShares);
        demand.setShares(demand.getShares() - tradedShares);
        var transaction = new Transaction(offerClientId, demandClientId, offer.getId(), demand.getId(), company, tradedShares, price);
        transactions.add(transaction);
        var partition = Stream.of(demand, offer).collect(partitioningBy(item -> item.getShares() == 0));
        var itemsToRemove = partition.get(true);
        var itemsToPutToWaiting = partition.get(false);
        removeItems(itemsToRemove);
        setItemsStateToWaiting(itemsToPutToWaiting);
        notifyClients(demandClientId, offerClientId, transaction);
        logAfterTransaction(transaction);
	}

    private void setItemsStateToWaiting(List<StockItem> itemsToPutToWaiting) {
        doUnderWriteLock(() -> {
            itemsToPutToWaiting.forEach(item -> stockItems.put(item, StockItemState.Waiting));
        });
    }

    public void removeItem(UUID itemId) {
        doUnderWriteLock(() -> {
            stockItems.entrySet().stream()
                .filter(entry -> itemId.equals(entry.getKey().getId())).findAny()
                .ifPresent(entry -> stockItems.remove(entry.getKey()));
        });
    }

    private void removeItems(List<StockItem> itemsToRemove) {
        doUnderWriteLock(() -> {
            itemsToRemove.forEach(stockItems::remove);
        });
    }

    private void notifyClients(UUID demandClientId, UUID offerClientId, Transaction transaction) {
        Executor.getDefault().submit(() -> clientNotifiers.get(offerClientId).saleNotifier().accept(transaction));
        Executor.getDefault().submit(() -> clientNotifiers.get(demandClientId).buyNotifier().accept(transaction));
    }

    private boolean makeSureTransactionIsPossibleAndSetStates(Demand demand, Offer offer) {
        if (!demand.matches(offer)) {
            var message = getCannotMakeTransactionMessage(demand, offer, "demand and offer do not match");
            logger.warning(message);
            return false;
        }
        return doUnderWriteLock(() -> {
            if (!stockItems.containsKey(demand)) {
                var message = getCannotMakeTransactionMessage(demand, offer, "demand does not exist");
                logger.warning(message);
                return false;
            }
            if (!stockItems.containsKey(offer)) {
                var message = getCannotMakeTransactionMessage(demand, offer, "offer does not exist");
                logger.warning(message);
                return false;
            }
            switch(stockItems.get(demand)) {
                case Transaction:
                    logger.info(getCannotMakeTransactionMessage(demand, offer, "demand is in another transaction"));
                    return false;
                case Waiting: 
                    break;
            };
            switch(stockItems.get(offer)) {
                case Transaction:
                    logger.info(getCannotMakeTransactionMessage(demand, offer, "offer is in another transaction"));
                    return false;
                case Waiting: 
                    break;
            };
            stockItems.put(demand, StockItemState.Transaction);
            stockItems.put(offer, StockItemState.Transaction);
            return true;
        });
    }

    private <T extends StockItem> Collection<T> getItemsAs(Class<T> cls) {
        return doUnderReadLock(() -> 
            stockItems.entrySet().stream()
                .filter(x -> x.getValue() == StockItemState.Waiting)
                .map(Entry::getKey)
                .filter(cls::isInstance)
                .map(cls::cast)
                .collect(toList())
        );
    }

    private <T extends StockItem> Collection<T> getMatchingItemsAs(StockItem stockItem, Class<T> cls) {
        return doUnderReadLock(() -> 
            stockItems.keySet().stream()
                .filter(stockItem::matches)
                .filter(cls::isInstance)
                .map(cls::cast)
                .collect(toList())
        );
    }

    private void logAfterTransaction(Transaction transaction) {
        logger.info(stringFrom(
            "Transaction complete: ", transaction
        ));
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
                var message = stringFrom(
                    "Trying to add but item with id ", stockItem.getId(), " already exists.", lineSeparator(),
                    stockItem.getClass().getSimpleName(), ": ", stockItem
                );
                logger.severe(message);
                throw new RuntimeException(message);
            }
            stockItems.put(stockItem, StockItemState.Waiting);
        });
    }

    private StockItem changeItem(UUID itemId, int newShares, double newPrice) {
        return doUnderWriteLock(() -> {
            var optionalEntry = stockItems.entrySet().stream()
                .filter(e -> e.getKey().getId().equals(itemId))
                .findFirst();
            if (optionalEntry.isEmpty()) {
                var message = stringFrom("Trying to change item with id ", itemId, " but it does not exist");
                logger.severe(message);
                throw new RuntimeException();
            }
            var entry = optionalEntry.get();
            if (entry.getValue() == StockItemState.Transaction) {
                logger.info(stringFrom("Trying to change item with id ", itemId, " but it's currently in a transaction"));
                throw new AlreadyInTransactionException();
            } else {
                var stockItem = entry.getKey();
                stockItem.setShares(newShares);
                stockItem.setPrice(newPrice);
                return stockItem;
            }
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

    enum StockItemState { Waiting, Transaction }
}