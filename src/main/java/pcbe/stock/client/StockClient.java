package pcbe.stock.client;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.model.Response;
import pcbe.stock.model.Transaction;
import pcbe.stock.model.StockItem.Offer;
import pcbe.stock.server.StockServer;

public class StockClient implements Callable<String> {
    /**
     *
     */
    private static final int FIXED_PRICE = 1;
    private final Logger logger = LogManager.getLogger();
    private final UUID id;
    private StockServer stockServer;
    private Map<String, Integer> ownedShares = new HashMap<>();
    private Integer currencyUnits;

    /**
     * @param id A unique identifier for the client
     * @throws NullPointerException if <code>id</code> is <code>null</code>
     */
    public StockClient(UUID id) {
        this.id = requireNonNull(id);
    }

    /**
     * @return <code>true</code> if the client is registered to a server; false otherwise
     */
    public boolean isRegistered() {
        return nonNull(stockServer);
    }

	public synchronized void notifySale(Transaction transaction) {
        currencyUnits += Double.valueOf(transaction.getShares() * transaction.getPrice()).intValue();
        ownedShares.compute(transaction.getCompany(), (k, v) -> v - transaction.getShares());
	}

	public synchronized void notifyBuy(Transaction transaction) {
	}

    /**
     * Registers the client to the given server
     * @param stockServer
     * @throws NullPointerException if <code>stockServer</code> is <code>null</code>
     */
    public void registerTo(StockServer stockServer) {
        requireNonNull(stockServer);
        requireSuccessfulResponse(() -> stockServer.register(this));
        this.stockServer = stockServer;
        logger.info("Client " + id + " registered.");
    }

    /**
     * Client entry point. Will be called by an {@link Executor}
     */
    @Override
    public String call() {
        if (!isRegistered())
            throw new RuntimeException("Client " + id + " not connected.");
        doClientyStuff();
        return "Client " + id + " done";
    }

    private void doClientyStuff() {
        while(true) 
        {
            offerOwnedShares();
            checkForOffersAndDemandShares();
            lookAtTransactionHistory();
            //make sure server has enough time to notify sales / buys
        }
    }

    private void lookAtTransactionHistory() {
        stockServer.getTransactionHistory(id);
        
    }

    private synchronized void checkForOffersAndDemandShares() {
        var existingOffers = stockServer.getOffers(id).getOffers();
        for (var offer : existingOffers) {
            if(!ownedShares.keySet().contains(offer.getCompany())) {
                var nrOfSharesToDemand = decideNrOfSharesToDemand(offer);
                stockServer.demandShares(id, offer.getCompany(), nrOfSharesToDemand, offer.getPrice());
            }
        }

    }

    private int decideNrOfSharesToDemand(Offer offer) {
        return offer.getPrice() * offer.getShares() < currencyUnits ? offer.getShares() : Double.valueOf(currencyUnits / offer.getPrice()).intValue();
    }

    private synchronized void offerOwnedShares() {
        for (var sharesPerCompany : ownedShares.entrySet())
            if(sharesPerCompany.getValue() > 0)
                stockServer.offerShares(id, sharesPerCompany.getKey(), sharesPerCompany.getValue(), FIXED_PRICE);
    }

    public void addShares(String company, int numberOfShares) {
        ownedShares.compute(company, (k, v) -> v == null ? numberOfShares : v + numberOfShares);
    }

    public void addCurrencyUnits(Integer currencyUnits) {
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