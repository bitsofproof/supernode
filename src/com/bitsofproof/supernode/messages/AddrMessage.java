package com.bitsofproof.supernode.messages;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class AddrMessage extends BitcoinPeer.Message
{

	public AddrMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("addr");
	}

	private List<WireFormat.Address> addresses = new ArrayList<WireFormat.Address> ();

	@Override
	public void toWire (Writer writer)
	{
		writer.writeVarInt (addresses.size ());
		for ( WireFormat.Address a : addresses )
		{
			writer.writeAddress (a, getVersion (), false);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		long n = reader.readVarInt ();
		for ( long i = 0; i < n; ++i )
		{
			addresses.add (reader.readAddress (getVersion (), false));
		}
	}

	public List<WireFormat.Address> getAddresses ()
	{
		return addresses;
	}

	public void setAddresses (List<WireFormat.Address> addresses)
	{
		this.addresses = addresses;
	}

}
