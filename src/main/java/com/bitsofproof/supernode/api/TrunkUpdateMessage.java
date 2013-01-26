package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.ArrayList;
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

	public BCSAPIMessage.TrunkUpdate toProtobuf ()
	{
		BCSAPIMessage.TrunkUpdate.Builder builder = BCSAPIMessage.TrunkUpdate.newBuilder ();
		builder.setBcsapiversion (1);
		if ( added != null )
		{
			for ( Block a : added )
			{
				builder.addAdded (a.toProtobuf ());
			}
		}
		if ( removed != null )
		{
			for ( Block r : removed )
			{
				builder.addRemoved (r.toProtobuf ());
			}
		}

		return builder.build ();
	}

	public static TrunkUpdateMessage fromProtobuf (BCSAPIMessage.TrunkUpdate pu)
	{
		List<Block> added = null;
		List<Block> removed = null;
		if ( pu.getAddedCount () > 0 )
		{
			added = new ArrayList<Block> ();
			for ( BCSAPIMessage.Block b : pu.getAddedList () )
			{
				added.add (Block.fromProtobuf (b));
			}
		}
		if ( pu.getRemovedCount () > 0 )
		{
			removed = new ArrayList<Block> ();
			for ( BCSAPIMessage.Block b : pu.getRemovedList () )
			{
				removed.add (Block.fromProtobuf (b));
			}
		}
		return new TrunkUpdateMessage (added, removed);
	}
}
