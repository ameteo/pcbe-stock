package pcbe.stock.server;

import static java.util.Collections.synchronizedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.client.StockClient;
import pcbe.stock.model.Response;
import pcbe.stock.model.StockItem.Offer;
import pcbe.stock.model.StockItem.Demand;

public class StockServer {

	private static final Logger logger = LogManager.getServerLogger();
	private final StockService stockService = StockService.getDefault();
	private Map<UUID, StockClient> clients = synchronizedMap(new HashMap<>());

	public Response register(StockClient stockClient) {
		if (clients.containsKey(stockClient.getId()))
			return Response.alreadyRegistered();
		clients.put(stockClient.getId(), stockClient);
		stockService.configureNotifiers(stockClient.getId(), stockClient.getNotifiers());
		logger.info("Client " + stockClient.getId() + " registered successfully.");
		return Response.registeredSuccessfully();
	}

	public Response offerShares(UUID clientId, String company, int shares, double price) {
		if (!clients.containsKey(clientId))
			return Response.notRegistered();
		var offer = new Offer(clientId, company, shares, price);
		stockService.addOffer(offer);
		return Response.created(offer.getId());
	}

	public Response demandShares(UUID clientId, String company, int shares, double price) {
		if (!clients.containsKey(clientId))
			return Response.notRegistered();
		var demand = new Demand(clientId, company, shares, price);
		stockService.addDemand(demand);
		return Response.created(demand.getId());
	}

	public Response changeOffer(UUID clientId, UUID offerId, int newShares, double newPrice) {
		if (!clients.containsKey(clientId))
			return Response.notRegistered();
		try {
			stockService.changeOffer(offerId, newShares, newPrice);
		} catch (AlreadyInTransactionException e) {
			return Response.ongoingTransaction();
		}
		return Response.changed();
	}

	public Response changeDemand(UUID clientId, UUID demandId, int newShares, double newPrice) {
		if (!clients.containsKey(clientId))
			return Response.notRegistered();
		try {
			stockService.changeDemand(demandId, newShares, newPrice);
		} catch (AlreadyInTransactionException e) {
			return Response.ongoingTransaction();
		}
		return Response.changed();
	}

	public Response getOffers(UUID clientId) {
		return clients.containsKey(clientId)
			? Response.offers(stockService.getOffers())
			: Response.notRegistered();
	}

	public Response getDemands(UUID clientId) {
		return clients.containsKey(clientId)
			? Response.demands(stockService.getDemands())
			: Response.notRegistered();
	}

	public Response getTransactionHistory(UUID clientId) {
		return clients.containsKey(clientId)
			? Response.transactions(stockService.getTransactions())
			: Response.notRegistered();
	}
}
