package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static pcbe.UUIDUtil.prefixOf;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.Executor;
import pcbe.stock.model.Notifiers;
import pcbe.stock.model.Response;
import pcbe.stock.model.StockItem.Demand;
import pcbe.stock.model.StockItem.Offer;
import pcbe.stock.model.Transaction;
import pcbe.stock.server.StockServer;

public class StockClient implements Callable<String> {

    private final UUID id;
    private StockServer stockServer;
    private double currencyUnits;
    private double restrictedCurrencyUnits;
    
    private Map<String, Integer> ownedShares = new HashMap<>();
    private Map<String, Integer> offeredShares = new HashMap<>();
    private static final double DEFAULT_PRICE = 1;
    
    private Map.Entry<UUID, TimerTask> offer;
    private Map.Entry<UUID, TimerTask> demand;
    
    private final ReentrantLock lock = new ReentrantLock();
    
    private final Logger logger = LogManager.getClientLogger();
    
    private Timer timer;
    private int lifespanSeconds;
    private long taskDelay;
    private AtomicBoolean stillHaveTime = new AtomicBoolean(true);
    private TimerTask lifespan = new TimerTask() {
        @Override
        public void run() {
            stillHaveTime.set(false);
        }
    };

    /**
     * @param id A unique identifier for the client
     * @throws NullPointerException if <code>id</code> is <code>null</code>
     */
    public StockClient(UUID id, int lifespanSeconds) {
        this.id = requireNonNull(id);
        this.lifespanSeconds = lifespanSeconds;
        timer = Executor.newTimer();
        taskDelay = TimeUnit.SECONDS.toMillis(lifespanSeconds) / 10;
    }

    /**
     * @return <code>true</code> if the client is registered to a server; false
     *         otherwise
     */
    public boolean isRegistered() {
        return nonNull(stockServer);
    }

    public void notifySale(Transaction transaction) {
        logger.info("notify sale for client " + prefixOf(id) + " and transaction " + prefixOf(transaction.getId()));
        lock.lock();
        try {
            if(offer != null) {
                currencyUnits += calculateCurrencyAmount(transaction);
                offeredShares.compute(transaction.getCompany(), (k, v) -> v - transaction.getShares()); 
                if(transaction.getOfferId().equals(offer.getKey())) {
                    offer.getValue().cancel();
                    offer = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void notifyBuy(Transaction transaction) {
        logger.info("notify buy for client " + prefixOf(id) + " and transaction " + prefixOf(transaction.getId()));
        lock.lock();
        try {
            if(demand != null) {
                restrictedCurrencyUnits -= calculateCurrencyAmount(transaction);
                ownedShares.compute(transaction.getCompany(), (k, v) -> v + transaction.getShares());
                if(transaction.getDemandId().equals(demand.getKey())) {
                    demand.getValue().cancel();
                    demand = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Notifiers getNotifiers() {
        return new StockClientNotifiers(this::notifyBuy, this::notifySale);
    }

    private int calculateCurrencyAmount(Transaction transaction) {
        return Double.valueOf(transaction.getShares() * transaction.getPrice()).intValue();
    }

    /**
     * Registers the client to the given server
     * 
     * @param stockServer
     * @throws NullPointerException if <code>stockServer</code> is <code>null</code>
     */
    public void registerTo(StockServer stockServer) {
        requireNonNull(stockServer);
        requireSuccessfulResponse(() -> stockServer.register(this));
        this.stockServer = stockServer;
    }

    /**
     * Client entry point. Will be called by an {@link java.util.concurrent.Executor}
     */
    @Override
    public String call() {
        if (!isRegistered())
            throw new RuntimeException("Client " + id + " not connected.");
        performAlgorithm();
        return "Client " + id + " done";
    }

    private void performAlgorithm() {
        timer.schedule(lifespan, TimeUnit.SECONDS.toMillis(lifespanSeconds));
        while (stillHaveTime.get()) {
            offerShares();
            demandShares();
        }
    }

    public void offerShares() {
        lock.lock();
        try {
            if(offer == null) {
                var optionalShares = ownedShares.entrySet().stream().findAny();
                if(optionalShares.isPresent()) {
                    var sharesPerCompany = optionalShares.get();
                    var calculatedPrice = calculatePrice(sharesPerCompany.getKey());
                    var response = stockServer.offerShares(id, sharesPerCompany.getKey(), sharesPerCompany.getValue(), calculatedPrice);
                    if(response.isSuccessful()) {
                        var offerId = response.getItemId();
                        offeredShares.compute(sharesPerCompany.getKey(), (k, v) -> sharesPerCompany.getValue() + (v == null ? 0 : v));
                        ownedShares.remove(sharesPerCompany.getKey());
                        offer = new AbstractMap.SimpleEntry<>(offerId, createChangeOfferTask(offerId));
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    private TimerTask createChangeOfferTask(UUID offerId) {
        var timerTask = new TimerTask() {

            @Override
            public void run() {
                var response = stockServer.getOfferById(id, offerId);
                if(response.isSuccessful()) {
                    var existentOffer = response.getOffer();
                    var changeResponse = stockServer.changeOffer(id, offerId, existentOffer.getShares(), existentOffer.getPrice() * 0.5);
                    if(changeResponse.isSuccessful()) {
                        lock.lock();
                        try {
                            if(offer != null) {
                                offer.getValue().cancel();
                                offer.setValue(createRemoveOfferTask(offerId));
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            }
        };

        timer.schedule(timerTask, taskDelay, taskDelay);
        return timerTask;
    }

    private TimerTask createRemoveOfferTask(UUID offerId) {
        var timerTask = new TimerTask() {

            @Override
            public void run() {
                lock.lock();
                try {
                    if(offer != null) {
                        var response = stockServer.removeItem(id, offerId);
                        if(response.isSuccessful()) {
                            offer.getValue().cancel();
                            offer = null;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        };
        timer.schedule(timerTask, taskDelay, taskDelay);
        return timerTask;
    }

    private double calculatePrice(String company) {
        return Math.random() > 0.5 ? consultDemandsAndCalculatePrice(company) : consultTransactionHistoryAndCalculatePrice(company);
    }
    
    private double consultDemandsAndCalculatePrice(String company) {
        var demandsOfCompany = stockServer.getDemands(id).getDemands().stream().filter(t -> t.getCompany().equals(company)).collect(toList());

        if(demandsOfCompany.isEmpty())
            return DEFAULT_PRICE;

        return demandsOfCompany.stream().map(Demand::getPrice).reduce(Math::max).get();
    }
    
    private double consultTransactionHistoryAndCalculatePrice(String company) {
        var transactionHistoryOfCompany = stockServer.getTransactionHistory(id).getTransactions()
            .stream().filter(t -> t.getCompany().equals(company)).collect(toList());

        if(transactionHistoryOfCompany.isEmpty())
            return DEFAULT_PRICE;

        var highestPriceInHistory = transactionHistoryOfCompany.stream().map(Transaction::getPrice).reduce(Math::max).get();
        return Math.random() > 0.5 ? highestPriceInHistory : highestPriceInHistory + 0.5;
    }

    public void demandShares() {
        lock.lock();
        try {
            if(demand == null && currencyUnits != 0) {
                var existingOffers = stockServer.getOffers(id).getOffers();
                for (var offer : existingOffers) {
                    if(offerIsNotMine(offer)) {
                        var nrOfSharesToDemand = calculateNumberOfSharesToDemand(offer);
                        var response = stockServer.demandShares(id, offer.getCompany(), nrOfSharesToDemand, offer.getPrice());
                        if(response.isSuccessful()) {
                            var demandId = response.getItemId();
                            putCurrencyAside(offer.getPrice() * nrOfSharesToDemand);
                            demand = new AbstractMap.SimpleEntry<>(demandId, createChangeDemandTask(demandId));
                            break;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private TimerTask createChangeDemandTask(UUID demandId) {
        var timerTask = new TimerTask() {

            @Override
            public void run() {
                var response = stockServer.getDemandById(id, demandId);
                if(response.isSuccessful()) {
                    var existentDemand = response.getDemand();
                    lock.lock();
                    try {
                        var extraCurrencyNeeded = calculateExtraCurrencyNeeded(existentDemand.getPrice(), existentDemand.getShares());
                        if(currencyUnits >= extraCurrencyNeeded) {
                            var changeResponse = stockServer.changeDemand(id, demandId, existentDemand.getShares(), existentDemand.getPrice() * 1.5);
                            if(changeResponse.isSuccessful()) {
                                if(demand != null) {
                                    demand.getValue().cancel();
                                    demand.setValue(createRemoveDemandTask(demandId));
                                    currencyUnits -= extraCurrencyNeeded;
                                    restrictedCurrencyUnits += extraCurrencyNeeded;
                                }
                            }
                        }
                        else {
                            demand.getValue().cancel();
                            demand.setValue(createRemoveDemandTask(demandId));
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }

            private double calculateExtraCurrencyNeeded(double price, int shares) {
                return price * shares * 0.5;
            }
        };

        timer.schedule(timerTask, taskDelay, taskDelay);
        return timerTask;
    }

    private TimerTask createRemoveDemandTask(UUID demandId) {
        var timerTask = new TimerTask() {

            @Override
            public void run() {
                lock.lock();
                try {
                    if(demand != null) {
                        var response = stockServer.removeItem(id, demandId);
                        if(response.isSuccessful()) {
                            demand.getValue().cancel();
                            demand = null;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        };
        timer.schedule(timerTask, taskDelay, taskDelay);
        return timerTask;
    }

    private boolean offerIsNotMine(Offer offer) {
        lock.lock();
        try {
            return this.offer != null ? !offer.getId().equals(this.offer.getKey()) : true;
        } finally {
            lock.unlock();
        }
    }

    private void putCurrencyAside(double amount) {
        currencyUnits -= amount;
        restrictedCurrencyUnits += amount;
    }

    private int calculateNumberOfSharesToDemand(Offer offer) {
        return offer.getPrice() * offer.getShares() < currencyUnits ? offer.getShares() : Double.valueOf(currencyUnits / offer.getPrice()).intValue();
    }

    public void addShares(String company, int numberOfShares) {
        logger.info(prefixOf(id) + " has been provided with " + numberOfShares + " shares of the company " + company);
        lock.lock();
        try {
            ownedShares.compute(company, (k, v) -> v == null ? numberOfShares : v + numberOfShares);
        } finally {
            lock.unlock();
        }
    }

    public void addCurrencyUnits(Integer currencyUnits) {
        logger.info(prefixOf(id) + " has been provided with " + currencyUnits + " units of currency");
        lock.lock();
        try {
            this.currencyUnits += currencyUnits;
        } finally {
            lock.unlock();
        }
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
            throw new RuntimeException();
        return response;
    }

    public UUID getId() {
        return id;
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