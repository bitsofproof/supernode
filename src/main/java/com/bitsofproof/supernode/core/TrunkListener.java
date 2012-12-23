package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.Blk;

public interface TrunkListener
{
	public void trunkExtended (Blk b);

	public void trunkShortened (Blk b);
}
