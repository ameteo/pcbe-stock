package pcbe.stock.model;

import java.util.UUID;

public class StockItem {
	private UUID id = UUID.randomUUID();
	private UUID clientId;
	private String company;
	private int shares;
	private double price;

	public StockItem(UUID clientId, String company, int shares, double price) {
		this.clientId = clientId;
		this.company = company;
		this.shares = shares;
		this.price = price;
	}

	public UUID getId() {
		return id;
	}

	public UUID getClientId() {
		return clientId;
	}

	public String getCompany() {
		return company;
	}

	public int getShares() {
		return shares;
	}

	public void setShares(int shares) {
		this.shares = shares;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public class Offer extends StockItem {
		public Offer(UUID clientId, String company, int shares, double price) {
			super(clientId, company, shares, price);
			
		}
	}

	public class Demand extends StockItem {
		public Demand(UUID clientId, String company, int shares, double price) {
			super(clientId, company, shares, price);
			
		}
	}
}