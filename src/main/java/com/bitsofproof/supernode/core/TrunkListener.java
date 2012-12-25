package com.bitsofproof.supernode.core;


public interface TrunkListener
{
	public void trunkExtended (String blockhash);

	public void trunkShortened (String blockhash);
}
