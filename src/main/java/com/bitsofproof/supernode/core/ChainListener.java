package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.Blk;

public interface ChainListener
{
	public void blockAdded (Blk blk);
}
