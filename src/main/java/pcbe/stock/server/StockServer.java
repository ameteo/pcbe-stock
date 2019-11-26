package pcbe.stock.server;

import static java.util.Collections.synchronizedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.client.StockClient;
import pcbe.stock.model.Response;
import pcbe.stock.model.StockItem;

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
		var offerId = stockService.addOffer(clientId, company, shares, price);
		return Response.created(offerId);
	}

	public Response demandShares(UUID clientId, String company, int shares, double price) {
		if (!clients.containsKey(clientId))
			return Response.notRegistered();
		var demandId = stockService.addDemand(clientId, company, shares, price);
		return Response.created(demandId);
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

	public Response getOfferById(UUID clientId, UUID offerId) {
		return clients.containsKey(clientId)
			? tryFindOffer(offerId)
			: Response.notRegistered();
	}

	private Response tryFindOffer(UUID offerId) {
		var foundOffer = stockService.getOffers().stream().filter(withId(offerId)).findAny();
		return foundOffer.isPresent()
			? Response.offer(foundOffer.get())
			: Response.doesNotExist(offerId);
	}
	
	public Response getDemandById(UUID clientId, UUID demandId) {
		return clients.containsKey(clientId)
			? tryFindDemand(demandId)
			: Response.notRegistered();
	}

	private Response tryFindDemand(UUID demandId) {
		var foundDemand = stockService.getDemands().stream().filter(withId(demandId)).findAny();
		return foundDemand.isPresent()
			? Response.demand(foundDemand.get())
			: Response.doesNotExist(demandId);
	}

	private Predicate<StockItem> withId(UUID offerId) {
		return item -> item.getId().equals(offerId);
	}

	public Response getTransactionHistory(UUID clientId) {
		return clients.containsKey(clientId)
			? Response.transactions(stockService.getTransactions())
			: Response.notRegistered();
	}

	public Response removeItem(UUID clientId, UUID itemId) {
		return clients.containsKey(clientId)
			? removeItem(itemId)
			: Response.notRegistered();
	}

	private Response removeItem(UUID itemId) {
		stockService.removeItem(itemId);
		return Response.removed();
	}
}
