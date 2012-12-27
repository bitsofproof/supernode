package com.bitsofproof.supernode.core;

import java.util.List;

public interface TrunkListener
{
	public void trunkUpdate (List<String> removedBlocks, List<String> addedBlocks);
}
