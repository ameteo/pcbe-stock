package pcbe.stock.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.model.Response;

public class StockService {

	private static final Logger logger = LogManager.getLogger();
	private static final StockService stockService = new StockService();

	public static StockService getDefault() {
		return stockService;
	}

	private StockService() { }

	private Set<UUID> clientIds = Collections.synchronizedSet(new HashSet<>());

	public Response register(UUID clientId) {
		if (clientIds.contains(clientId))
			return Response.failed("Client " + clientId + " already registered.");
		logger.info("Client " + clientId + " registered successfully.");
		clientIds.add(clientId);
		return Response.successful();
	}
}