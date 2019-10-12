package pcbe.stock.server;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

import pcbe.stock.model.Response;

public class StockServer {

	private static final StockService stockService = StockService.getDefault();
	private Collection<String> companies = new HashSet<>();

	public Response register(UUID clientId) {
		return stockService.register(clientId);
	}

	public void addCompanies(Collection<String> companiesToBeAdded) {
		companies.addAll(companiesToBeAdded);
	}

}
