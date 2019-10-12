package pcbe.stock.server;

import java.util.UUID;

import pcbe.stock.model.Response;
import pcbe.stock.server.api.StockService;

public class StockServer {

    private static final StockService stockService = StockService.getDefault();

	public Response register(UUID clientId) {
		return stockService.register(clientId);
	}

}
