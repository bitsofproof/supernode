/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.messages;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.api.BinaryAggregator;
import com.bitsofproof.supernode.api.BloomFilter;
import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.WireFormat.Reader;
import com.bitsofproof.supernode.api.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class MerkleBlockMessage extends BitcoinPeer.Message
{

	public MerkleBlockMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("merkleblock");
	}

	private Blk block = new Blk ();
	private List<byte[]> hashes;
	private byte[] flags;
	private int numberOfTransactions;

	public Blk getBlock ()
	{
		return block;
	}

	public void setBlock (Blk block)
	{
		this.block = block;
		this.numberOfTransactions = block.getTransactions ().size ();
		hashes = new ArrayList<byte[]> ();
		flags = new byte[0];
	}

	private static class InScopeHash
	{
		private byte[] hash;
		private boolean inScope;
	}

	public void filter (BloomFilter filter)
	{
		List<InScopeHash> ins = new ArrayList<InScopeHash> ();
		for ( Tx t : block.getTransactions () )
		{
			InScopeHash ish = new InScopeHash ();
			ish.hash = new Hash (t.getHash ()).toByteArray ();
			ish.inScope = t.passesFilter (filter);
		}
		BinaryAggregator<InScopeHash> aggregator = new BinaryAggregator<InScopeHash> ()
		{
			@Override
			public InScopeHash merge (InScopeHash a, InScopeHash b)
			{
				try
				{
					InScopeHash result = new InScopeHash ();

					MessageDigest digest = MessageDigest.getInstance ("SHA-256");
					digest.update (a.hash);
					result.hash = digest.digest (digest.digest (b.hash));
					result.inScope = a.inScope || b.inScope;
					return result;
				}
				catch ( NoSuchAlgorithmException e )
				{
					return null;
				}
			}
		};
		InScopeHash root = aggregator.aggregate (ins);
		BigInteger bits = BigInteger.ZERO;
		if ( root.inScope )
		{
			for ( int i = 0; i < ins.size (); ++i )
			{
				if ( ins.get (i).inScope )
				{
					bits = bits.setBit (i);
					hashes.add (ins.get (i).hash);
				}
			}
			flags = bits.toByteArray ();
			ByteUtils.reverse (flags);
		}
	}

	public int getNumberOfTransactions ()
	{
		return numberOfTransactions;
	}

	@Override
	public void toWire (Writer writer)
	{
		block.toWireHeaderOnly (writer);
		writer.writeUint32 (block.getTransactions ().size ());
		writer.writeVarInt (hashes.size ());
		for ( int i = 0; i < hashes.size (); ++i )
		{
			writer.writeBytes (hashes.get (i));
		}
		writer.writeVarBytes (flags);
	}

	@Override
	public void fromWire (Reader reader)
	{
		block.fromWireHeaderOnly (reader);
		numberOfTransactions = (int) reader.readUint32 ();
		int nh = (int) reader.readVarInt ();
		hashes = new ArrayList<byte[]> ();
		for ( int i = 0; i < nh; ++i )
		{
			hashes.add (reader.readBytes (32));
		}
		flags = reader.readVarBytes ();
	}
}
