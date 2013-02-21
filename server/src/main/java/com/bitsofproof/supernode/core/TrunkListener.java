package com.bitsofproof.supernode.core;

import java.util.List;

import com.bitsofproof.supernode.model.Blk;

public interface TrunkListener
{
	public void trunkUpdate (List<Blk> removedBlocks, List<Blk> addedBlocks);
}
