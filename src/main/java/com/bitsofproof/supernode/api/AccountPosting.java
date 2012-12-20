package com.bitsofproof.supernode.api;

import java.io.Serializable;

public class AccountPosting implements Serializable
{
	private static final long serialVersionUID = -6577653630851369444L;

	private long timestamp;
	private TransactionOutput received;
	private TransactionOutput spent;

	public long getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp (long timestamp)
	{
		this.timestamp = timestamp;
	}

	public TransactionOutput getReceived ()
	{
		return received;
	}

	public void setReceived (TransactionOutput received)
	{
		this.received = received;
	}

	public TransactionOutput getSpent ()
	{
		return spent;
	}

	public void setSpent (TransactionOutput spent)
	{
		this.spent = spent;
	}

}
