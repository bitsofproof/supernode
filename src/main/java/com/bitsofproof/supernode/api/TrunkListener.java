package com.bitsofproof.supernode.api;

public interface TrunkListener
{
	public void blockAdded (Block b);

	public void blockRemoved (Block b);
}
