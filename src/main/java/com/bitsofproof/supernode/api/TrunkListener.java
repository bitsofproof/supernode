package com.bitsofproof.supernode.api;

import java.util.List;

public interface TrunkListener
{
	public void trunkUpdate (List<Block> removed, List<Block> added);
}
