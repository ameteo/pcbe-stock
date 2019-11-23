package pcbe.stock.model;

import java.util.UUID;

public final class Transaction {
	private UUID id;
	private UUID offeringClientId;
	private UUID demandingClientId;
	private UUID offerId;
	private UUID demandId;
	private String company;
	private int shares;
	private double price;

	public Transaction(UUID offeringClientId, UUID demandingClientId, UUID offerId, UUID demandId, String company, int shares, double price) {
		this.id = UUID.randomUUID();
		this.offeringClientId = offeringClientId;
		this.demandingClientId = demandingClientId;
		this.offerId = offerId;
		this.demandId = demandId;
		this.company = company;
		this.shares = shares;
		this.price = price;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOfferingClientId() {
		return offeringClientId;
	}

	public UUID getDemandingClientId() {
		return demandingClientId;
	}

	public UUID getOfferId() {
		return offerId;
	}

	public UUID getDemandId() {
		return demandId;
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

	@Override
	public String toString() {
		return "Transaction [" 
			+ "company=" + company + ", "
			+ "shares=" + shares  + ", "
			+ "price=" + price + ", "
			+ "id=" + prefixOf(id) + ", "
			+ "demandId=" + prefixOf(demandId) + ", "
			+ "demandingClientId=" + prefixOf(demandingClientId) + ", "
			+ "offerId=" + prefixOf(offerId) + ", "
			+ "offeringClientId=" + prefixOf(offeringClientId) + "]";
	}

	private static String prefixOf(UUID id) {
		return id.toString().substring(0, 8);
	}
}