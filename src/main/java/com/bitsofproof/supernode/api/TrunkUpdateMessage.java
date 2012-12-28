package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.List;

public class TrunkUpdateMessage implements Serializable
{
	private static final long serialVersionUID = 6012842751107958147L;
	private final List<Block> added;
	private final List<Block> removed;

	public List<Block> getAdded ()
	{
		return added;
	}

	public List<Block> getRemoved ()
	{
		return removed;
	}

	public TrunkUpdateMessage (List<Block> added, List<Block> removed)
	{
		super ();
		this.added = added;
		this.removed = removed;
	}
}
