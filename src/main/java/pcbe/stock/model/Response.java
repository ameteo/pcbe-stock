package pcbe.stock.model;

import static java.util.Arrays.asList;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import pcbe.stock.model.StockItem.Demand;
import pcbe.stock.model.StockItem.Offer;

/**
 * Response returned by the server to client requests.
 */
public class Response {
    private Status status;
    private UUID itemId;
    private Set<Offer> offers;
    private Set<Demand> demands;
    private StockItem item;
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

    public Set<Offer> getOffers() {
        return offers;
    }

    public Offer getOffer() {
        return Offer.class.cast(item);
    }

    public Set<Demand> getDemands() {
        return demands;
    }

    public Demand getDemand() {
        return Demand.class.cast(item);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

   public enum Status {
        Successful,
        AlreadyRegistered,
        NotRegistered,
        Created,
        DoesNotExist,
        OngoingTransaction,
        Changed;

        public boolean isSuccessful() {
            return asList(Successful, Created, Changed).contains(this);
        }
    }

    public static Response alreadyRegistered() {
        return new Response(Status.AlreadyRegistered);
    }
    
    public static Response registeredSuccessfully() {
        return new Response(Status.Successful);
    }

	public static Response notRegistered() {
		return new Response(Status.NotRegistered);
	}

	public static Response created(UUID itemId) {
        var response = new Response(Status.Created);
        response.itemId = itemId;
        return response;
	}

	public static Response doesNotExist(UUID itemId) {
		var response = new Response(Status.DoesNotExist);
        response.itemId = itemId;
        return response;
	}

	public static Response changed() {
        return new Response(Status.Changed);
	}

	public static Response ongoingTransaction() {
		return new Response(Status.OngoingTransaction);
	}

	public static Response offers(Set<Offer> offers) {
		var response = new Response(Status.Successful);
        response.offers = offers;
        return response;
	}

	public static Response demands(Set<Demand> demands) {
		var response = new Response(Status.Successful);
        response.demands = demands;
        return response;
    }
    
    public static Response offer(Offer offer) {
		var response = new Response(Status.Successful);
        response.item = offer;
        return response;
    }
    
    public static Response demand(Demand demand) {
		var response = new Response(Status.Successful);
        response.item = demand;
        return response;
    }

	public static Response transactions(List<Transaction> transactions) {
		var response = new Response(Status.Successful);
        response.transactions = transactions;
        return response;
	}

	public static Response removed() {
		return new Response(Status.Successful);
	}
}
