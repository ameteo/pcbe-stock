package pcbe.stock.server;

import static java.util.Collections.synchronizedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.client.StockClient;
import pcbe.stock.model.Response;

public class StockService {

	private static final Logger logger = LogManager.getServerLogger();
	private static final StockService stockService = new StockService();

	public static StockService getDefault() {
		return stockService;
	}

	private StockService() { }

	private Map<UUID, StockClient> clients = synchronizedMap(new HashMap<>());

	public Response register(StockClient stockClient) {
		if (clients.containsKey(stockClient.getId()))
			return Response.alreadyRegistered();
		clients.put(stockClient.getId(), stockClient);
		logger.info("client " + stockClient.getId() + " registered successfully");
		return Response.registered();
	}
}