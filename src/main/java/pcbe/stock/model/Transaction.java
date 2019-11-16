package pcbe.stock.model;

import java.util.UUID;

public final class Transaction {
	private UUID id;
	private UUID from;
	private UUID to;
	private String company;
	private int shares;
	private double price;

	public Transaction(UUID from, UUID to, String company, int shares, double price) {
		this.id = UUID.randomUUID();
		this.from = from;
		this.to = to;
		this.company = company;
		this.shares = shares;
		this.price = price;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFrom() {
		return from;
	}

	public UUID getTo() {
		return to;
	}

	public String getCompany() {
		return company;
	}

	public int getShares() {
		return shares;
	}

	public double getPrice() {
		return price;
	}
}