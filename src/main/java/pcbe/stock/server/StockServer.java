package pcbe.stock.server;

import pcbe.stock.model.request.RegisterRequest;
import pcbe.stock.model.response.RegisterResponse;
import pcbe.stock.server.api.StockService;

public class StockServer {

    private static final StockService stockService = StockService.getDefault();

	public RegisterResponse register(RegisterRequest registerRequest) {
		return stockService.register(registerRequest);
	}

}
