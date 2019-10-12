package pcbe.stock.server.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import pcbe.log.LogManager;
import pcbe.stock.model.request.RegisterRequest;
import pcbe.stock.model.response.RegisterResponse;

public class StockService {

	private static final Logger logger = LogManager.getLogger();
	private static final StockService stockService = new StockService();

	public static StockService getDefault() {
		return stockService;
	}

	private StockService() { }

	private Set<String> clientIds = Collections.synchronizedSet(new HashSet<>());

	public RegisterResponse register(RegisterRequest registerRequest) {
		var clientId = registerRequest.getClientId();
		if (clientIds.contains(clientId)) {
			logger.info("Cannot register client " + clientId + " because he is already registered.");
			return RegisterResponse.failed("Client " + clientId + " already registered.");
		}
		clientIds.add(clientId);
		logger.info("Client " + clientId + " registered successfully.");
		return RegisterResponse.successful();
	}
}
