package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

	public static TrunkUpdateMessage fromJSON (JSONObject o) throws JSONException
	{
		List<Block> added = new ArrayList<Block> ();
		List<Block> removed = new ArrayList<Block> ();
		JSONArray a = o.getJSONArray ("added");
		for ( int i = 0; i < a.length (); ++i )
		{
			added.add (Block.fromJSON (a.getJSONObject (i)));
		}
		JSONArray r = o.getJSONArray ("removed");
		for ( int i = 0; i < r.length (); ++i )
		{
			removed.add (Block.fromJSON (r.getJSONObject (i)));
		}
		return new TrunkUpdateMessage (added, removed);
	}

	public JSONObject toJSON () throws JSONException
	{
		JSONObject o = new JSONObject ();
		JSONArray a = new JSONArray ();
		for ( Block b : added )
		{
			a.put (b.toJSON ());
		}
		o.put ("added", a);
		JSONArray r = new JSONArray ();
		for ( Block b : removed )
		{
			r.put (b.toJSON ());
		}
		o.put ("removed", r);
		return o;
	}
}
