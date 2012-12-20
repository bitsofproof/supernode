package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.List;

public class AccountStatement implements Serializable
{
	private static final long serialVersionUID = 5769209959467508424L;

	private long extracted;
	private String mostRecentBlock;
	private List<String> addresses;
	private long opening;
	private List<TransactionOutput> openingBalances;
	private List<AccountPosting> postings;

	public long getExtracted ()
	{
		return extracted;
	}

	public void setExtracted (long extracted)
	{
		this.extracted = extracted;
	}

	public String getMostRecentBlock ()
	{
		return mostRecentBlock;
	}

	public void setMostRecentBlock (String mostRecentBlock)
	{
		this.mostRecentBlock = mostRecentBlock;
	}

	public List<String> getAddresses ()
	{
		return addresses;
	}

	public void setAddresses (List<String> addresses)
	{
		this.addresses = addresses;
	}

	public long getOpening ()
	{
		return opening;
	}

	public void setOpening (long opening)
	{
		this.opening = opening;
	}

	public List<TransactionOutput> getOpeningBalances ()
	{
		return openingBalances;
	}

	public void setOpeningBalances (List<TransactionOutput> openingBalances)
	{
		this.openingBalances = openingBalances;
	}

	public List<AccountPosting> getPostings ()
	{
		return postings;
	}

	public void setPostings (List<AccountPosting> postings)
	{
		this.postings = postings;
	}

}
