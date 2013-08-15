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
package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.common.BinaryAggregator;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class Blk implements Serializable
{
	private static final long serialVersionUID = 1L;

	private Long id;

	private String hash;

	long version;

	private String previousHash;

	transient private byte[] wireTransactions;

	private String merkleRoot;

	private long createTime;
	private long difficultyTarget;
	private long chainWork;
	private int height;
	private long nonce;

	private List<Tx> transactions;

	private transient ArrayList<String> txHashes;
	private Long headId;

	private byte[] filterMap;

	private int filterFunctions;

	public static Blk fromLevelDB (byte[] data) throws ValidationException
	{
		LevelDBStore.BLOCK p;
		try
		{
			p = LevelDBStore.BLOCK.parseFrom (data);
			Blk b = new Blk ();
			b.setHash (new Hash (p.getHash ().toByteArray ()).toString ());
			b.setHeight (p.getHeight ());
			b.setVersion (p.getVersion ());
			b.setPreviousHash (new Hash (p.getPreviousHash ().toByteArray ()).toString ());
			b.setMerkleRoot (new Hash (p.getMerkleRoot ().toByteArray ()).toString ());
			b.setCreateTime (p.getCreateTime ());
			b.setDifficultyTarget (p.getDifficultyTarget ());
			b.setNonce (p.getNonce ());
			b.setChainWork (p.getChainWork ());
			b.setHeadId (p.getHeadId ());
			b.setFilterMap (p.getFilterMap ().toByteArray ());
			b.setFilterFunctions (p.getFilterFunctions ());
			if ( p.getTxHashesCount () > 0 )
			{
				b.txHashes = new ArrayList<String> (p.getTxHashesCount ());
				for ( ByteString bs : p.getTxHashesList () )
				{
					b.txHashes.add (new Hash (bs.toByteArray ()).toString ());
				}
			}

			return b;
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new ValidationException (e);
		}
	}

	public void setTxHashes (ArrayList<String> txHashes)
	{
		this.txHashes = txHashes;
	}

	public byte[] toLevelDB ()
	{
		LevelDBStore.BLOCK.Builder builder = LevelDBStore.BLOCK.newBuilder ();
		builder.setStoreVersion (1);
		builder.setHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
		builder.setHeight (height);
		builder.setVersion ((int) version);
		builder.setPreviousHash (ByteString.copyFrom (new Hash (previousHash).toByteArray ()));
		builder.setMerkleRoot (ByteString.copyFrom (new Hash (merkleRoot).toByteArray ()));
		builder.setCreateTime ((int) createTime);
		builder.setDifficultyTarget ((int) difficultyTarget);
		builder.setNonce ((int) nonce);
		builder.setChainWork (chainWork);
		builder.setHeadId (headId);
		if ( filterMap != null )
		{
			builder.setFilterMap (ByteString.copyFrom (filterMap));
			builder.setFilterFunctions (filterFunctions);
		}
		else
		{
			// genesis
			builder.setFilterMap (ByteString.copyFrom (new byte[0]));
			builder.setFilterFunctions (0);
		}
		if ( transactions != null )
		{
			for ( Tx t : transactions )
			{
				builder.addTxHashes (ByteString.copyFrom (new Hash (t.getHash ()).toByteArray ()));
			}
		}
		return builder.build ().toByteArray ();
	}

	public ArrayList<String> getTxHashes ()
	{
		return txHashes;
	}

	public JSONObject toJSON ()
	{
		parseTransactions ();

		JSONObject o = new JSONObject ();
		try
		{
			o.put ("hash", hash);
			o.put ("version", version);
			o.put ("previous", previousHash);
			o.put ("merkleRoot", merkleRoot);
			o.put ("createTime", createTime);
			o.put ("difficultyTarget", difficultyTarget);
			o.put ("chainWork", chainWork);
			o.put ("height", height);
			o.put ("nonce", nonce);
			List<JSONObject> txJSON = new ArrayList<JSONObject> ();
			for ( Tx t : transactions )
			{
				txJSON.add (t.toJSON ());
			}
			o.put ("transactions", txJSON);
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getHash ()
	{
		return hash;
	}

	public void setHash (String hash)
	{
		this.hash = hash;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public String getMerkleRoot ()
	{
		return merkleRoot;
	}

	public void setMerkleRoot (String merkleRoot)
	{
		this.merkleRoot = merkleRoot;
	}

	public long getCreateTime ()
	{
		return createTime;
	}

	public void setCreateTime (long createTime)
	{
		this.createTime = createTime;
	}

	public long getDifficultyTarget ()
	{
		return difficultyTarget;
	}

	public void setDifficultyTarget (long difficultyTarget)
	{
		this.difficultyTarget = difficultyTarget;
	}

	public long getNonce ()
	{
		return nonce;
	}

	public void setNonce (long nonce)
	{
		this.nonce = nonce;
	}

	public List<Tx> getTransactions ()
	{
		return transactions;
	}

	public void setTransactions (List<Tx> transactions)
	{
		this.transactions = transactions;
	}

	public long getChainWork ()
	{
		return chainWork;
	}

	public void setChainWork (long chainWork)
	{
		this.chainWork = chainWork;
	}

	public int getHeight ()
	{
		return height;
	}

	public void setHeight (int height)
	{
		this.height = height;
	}

	public String getPreviousHash ()
	{
		return previousHash;
	}

	public void setPreviousHash (String previousHash)
	{
		this.previousHash = previousHash;
	}

	private String computeMerkleRoot () throws ValidationException
	{
		parseTransactions ();
		if ( transactions == null )
		{
			throw new ValidationException ("block has no transactions");
		}

		ArrayList<byte[]> tree = new ArrayList<byte[]> ();
		int nt = transactions.size ();
		if ( nt == 0 )
		{
			throw new ValidationException ("block has no transactions");
		}

		for ( Tx t : transactions )
		{
			tree.add (new Hash (t.getHash ()).toByteArray ());
		}
		BinaryAggregator<byte[]> aggregator = new BinaryAggregator<byte[]> ()
		{
			@Override
			public byte[] merge (byte[] a, byte[] b)
			{
				try
				{
					MessageDigest digest = MessageDigest.getInstance ("SHA-256");
					digest.update (a);
					return digest.digest (digest.digest (b));
				}
				catch ( NoSuchAlgorithmException e )
				{
					return null;
				}
			}
		};
		return new Hash (aggregator.aggregate (tree)).toString ();
	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	public static Blk fromWireDump (String s)
	{
		WireFormat.Reader reader = new WireFormat.Reader (ByteUtils.fromHex (s));
		Blk b = new Blk ();
		b.fromWire (reader);
		return b;
	}

	public void toWire (WireFormat.Writer writer)
	{
		toWireHeaderOnly (writer);
		if ( wireTransactions != null )
		{
			writer.writeBytes (wireTransactions);
		}
		else
		{
			if ( transactions != null )
			{
				writer.writeVarInt (transactions.size ());
				for ( Tx t : transactions )
				{
					t.toWire (writer);
				}
			}
			else
			{
				writer.writeVarInt (0);
			}
		}
	}

	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor ();
		version = reader.readUint32 ();

		previousHash = reader.readHash ().toString ();
		merkleRoot = reader.readHash ().toString ();
		createTime = reader.readUint32 ();
		difficultyTarget = reader.readUint32 ();
		nonce = reader.readUint32 ();

		wireTransactions = reader.readRest ();
		transactions = null;

		hash = reader.hash (cursor, 80).toString ();
	}

	public void fromWireHeaderOnly (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor ();
		version = reader.readUint32 ();

		previousHash = reader.readHash ().toString ();
		merkleRoot = reader.readHash ().toString ();
		createTime = reader.readUint32 ();
		difficultyTarget = reader.readUint32 ();
		nonce = reader.readUint32 ();
		transactions = null;

		hash = reader.hash (cursor, 80).toString ();
	}

	public void parseTransactions ()
	{
		if ( wireTransactions == null )
		{
			return;
		}

		WireFormat.Reader reader = new WireFormat.Reader (wireTransactions);
		long nt = reader.readVarInt ();
		transactions = new ArrayList<Tx> ();
		for ( long i = 0; i < nt; ++i )
		{
			Tx t = new Tx ();
			t.fromWire (reader);
			t.setIx (i);
			if ( i == 0 )
			{
				for ( TxOut out : t.getOutputs () )
				{
					out.setCoinbase (true);
				}
			}
			transactions.add (t);
		}
		wireTransactions = null;
	}

	public void checkMerkleRoot () throws ValidationException
	{
		if ( !merkleRoot.equals (computeMerkleRoot ()) )
		{
			throw new ValidationException ("merkle root mismatch");
		}
	}

	public void checkHash () throws ValidationException
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWireHeaderOnly (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
		if ( !reader.hash ().toString ().equals (hash) )
		{
			throw new ValidationException ("block hash mismatch");
		}
	}

	public void computeHash () throws ValidationException
	{
		merkleRoot = computeMerkleRoot ();

		WireFormat.Writer writer = new WireFormat.Writer ();
		toWireHeaderOnly (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());

		hash = reader.hash ().toString ();
	}

	public void toWireHeaderOnly (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		writer.writeHash (new Hash (previousHash));
		writer.writeHash (new Hash (merkleRoot));
		writer.writeUint32 (createTime);
		writer.writeUint32 (difficultyTarget);
		writer.writeUint32 (nonce);
	}

	public Long getHeadId ()
	{
		return headId;
	}

	public void setHeadId (Long headId)
	{
		this.headId = headId;
	}

	public byte[] getFilterMap ()
	{
		return filterMap;
	}

	public void setFilterMap (byte[] filterMap)
	{
		this.filterMap = filterMap;
	}

	public int getFilterFunctions ()
	{
		return filterFunctions;
	}

	public void setFilterFunctions (int filterFunctions)
	{
		this.filterFunctions = filterFunctions;
	}

}
