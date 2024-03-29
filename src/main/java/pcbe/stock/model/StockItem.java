package pcbe.stock.model;

import static pcbe.UUIDUtil.prefixOf;

import java.util.UUID;

public abstract class StockItem {
	private UUID id;
	private UUID clientId;
	private String company;
	private int shares;
	private double price;

	private StockItem(UUID id, UUID clientId, String company, int shares, double price) {
		this.id = id;
		this.clientId = clientId;
		this.company = company;
		this.shares = shares;
		this.price = price;
	}

	protected StockItem(UUID clientId, String company, int shares, double price) {
		this(UUID.randomUUID(), clientId, company, shares, price);
	}

	protected StockItem(StockItem stockItem) {
		this(stockItem.id, stockItem.clientId, stockItem.company, stockItem.shares, stockItem.price);
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

	public static class Offer extends StockItem {
		public Offer(UUID clientId, String company, int shares, double price) {
			super(clientId, company, shares, price);
		}

		public Offer(Offer offer) {
			super(offer);
		}
	}

	public static class Demand extends StockItem {
		public Demand(UUID clientId, String company, int shares, double price) {
			super(clientId, company, shares, price);
		}

		public Demand(Demand demand) {
			super(demand);
		}
	}


	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		return StockItem.class.cast(obj).id.equals(id);
	}

	@Override
	public String toString() {
		return "StockItem ["
			+ "type=" + getClass().getSimpleName() + ", "
			+ "company=" + company + ", "
			+ "shares=" + shares + ", "
			+ "price=" + price + ", "
			+ "id=" + prefixOf(id) + ", "
			+ "clientId=" + prefixOf(clientId)
			+ "]";
	}
}