package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.List;

public class AccountPosting implements Serializable
{
	private static final long serialVersionUID = -6577653630851369444L;

	private long timestamp;
	private List<TransactionOutput> received;
	private List<TransactionOutput> spent;

	public long getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp (long timestamp)
	{
		this.timestamp = timestamp;
	}

	public List<TransactionOutput> getReceived ()
	{
		return received;
	}

	public void setReceived (List<TransactionOutput> received)
	{
		this.received = received;
	}

	public List<TransactionOutput> getSpent ()
	{
		return spent;
	}

	public void setSpent (List<TransactionOutput> spent)
	{
		this.spent = spent;
	}

}
