package pcbe.stock.model;

import static java.util.Arrays.asList;

import java.util.List;
import java.util.UUID;

import pcbe.stock.model.StockItem.Demand;
import pcbe.stock.model.StockItem.Offer;

/**
 * Response returned by the server to client requests.
 */
public class Response {
    private Status status;
    private UUID itemId;
    private List<Offer> offers;
    private List<Demand> demands;
    private List<Transaction> transactions;

    private Response(Status status) {
        this.status = status;
    }

    public boolean isSuccessful() {
        return status.isSuccessful();
    }

    public Status getStatus() {
        return status;
    }

    public UUID getItemId() {
        return itemId;
    }

    public List<Offer> getOffers() {
        return offers;
    }

    public List<Demand> getDemands() {
        return demands;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

   public enum Status {
        RegisteredSuccessfully,
        AlreadyRegistered,
        NotRegistered,
        Created,
        DoesNotExist,
        OngoingTransaction,
        Changed;

        public boolean isSuccessful() {
            return asList(RegisteredSuccessfully, Created, Changed).contains(this);
        }
    }

}
