package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

import pcbe.log.LogManager;
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
    private static final double FIXED_PRICE = 1;

    private final Logger logger = LogManager.getClientLogger();

    private Timer timer;
    private int lifespanSeconds;
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
        timer = new Timer();
    }

    /**
     * @return <code>true</code> if the client is registered to a server; false
     *         otherwise
     */
    public boolean isRegistered() {
        return nonNull(stockServer);
    }

    public synchronized void notifySale(Transaction transaction) {
        logger.info("notify sale for client " + id + " and transaction " + transaction.getId());
        currencyUnits += calculateCurrencyAmount(transaction);
        offeredShares.compute(transaction.getCompany(), (k, v) -> v - transaction.getShares());
    }

    public synchronized void notifyBuy(Transaction transaction) {
        logger.info("notify buy for client " + id + " and transaction " + transaction.getId());
        restrictedCurrencyUnits -= calculateCurrencyAmount(transaction);
        ownedShares.compute(transaction.getCompany(), (k, v) -> v + transaction.getShares());
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
     * Client entry point. Will be called by an {@link Executor}
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
        timer.cancel();
    }

    public synchronized void offerShares() {
        for (var sharesPerCompany : ownedShares.entrySet())
            if(sharesPerCompany.getValue() > 0) {
                var calculatedPrice = calculatePrice(sharesPerCompany.getKey());
                stockServer.offerShares(id, sharesPerCompany.getKey(), sharesPerCompany.getValue(), calculatedPrice);
                offeredShares.compute(sharesPerCompany.getKey(), (k, v) -> sharesPerCompany.getValue() + (v == null ? 0 : v));
                sharesPerCompany.setValue(0);
                break;
            }
    }
    
    private double calculatePrice(String company) {
        return Math.random() > 0.5 ? consultDemandsAndCalculatePrice(company) : consultTransactionHistoryAndCalculatePrice(company);
    }
    
    private double consultDemandsAndCalculatePrice(String company) {
        var demandsOfCompany = stockServer.getDemands(id).getDemands().stream().filter(t -> t.getCompany().equals(company)).collect(toList());

        if(demandsOfCompany.isEmpty())
            return FIXED_PRICE;

        return demandsOfCompany.stream().map(Demand::getPrice).reduce(Math::max).get();
    }
    
    private double consultTransactionHistoryAndCalculatePrice(String company) {
        var transactionHistoryOfCompany = stockServer.getTransactionHistory(id).getTransactions()
            .stream().filter(t -> t.getCompany().equals(company)).collect(toList());
        if(transactionHistoryOfCompany.isEmpty())
            return FIXED_PRICE;

        var highestPriceInHistory = transactionHistoryOfCompany.stream().map(Transaction::getPrice).reduce(Math::max).get();
        return Math.random() > 0.5 ? highestPriceInHistory : highestPriceInHistory + 0.5;
    }

    public synchronized void demandShares() {
        var existingOffers = stockServer.getOffers(id).getOffers();
        for (var offer : existingOffers) {
            if (currencyUnits == 0)
                break;
            if(!ownedShares.keySet().contains(offer.getCompany())) {
                var nrOfSharesToDemand = calculateNumberOfSharesToDemand(offer);
                stockServer.demandShares(id, offer.getCompany(), nrOfSharesToDemand, offer.getPrice());
                putCurrencyAside(offer.getPrice() * nrOfSharesToDemand);
            }
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
        logger.info(id + " has been provided with " + numberOfShares + " shares of the company " + company);
        ownedShares.compute(company, (k, v) -> v == null ? numberOfShares : v + numberOfShares);
    }

    public void addCurrencyUnits(Integer currencyUnits) {
        logger.info(id + " has been provided with " + currencyUnits + " units of currency");
        this.currencyUnits += currencyUnits;
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