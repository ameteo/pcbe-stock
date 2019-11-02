package pcbe.stock.server;

import java.util.UUID;

import pcbe.stock.client.StockClient;
import pcbe.stock.model.Response;

public class StockServer {

	private final StockService stockService = StockService.getDefault();

	public Response register(StockClient stockClient) {
		return stockService.register(stockClient);
	}

	public Response offerShares(UUID clientId, String company, int shares, double price) {
		return null;
	}


	public Response demandShares(UUID clientId, String company, int shares, double price) {
		return null;
	}

	public Response changeOffer(UUID clientId, UUID offerId, int newShares, double newPrice) {
		return null;
	}

	public Response changeDemand(UUID clientId, UUID demandId, int newShares, double newPrice) {
		return null;
	}

	public Response getOffers(UUID clientId) {
		return null;
	}

	public Response getDemands(UUID clientId) {
		return null;
	}

	public Response getTransactionHistory(UUID clientId) {
		return null;
	}
}
