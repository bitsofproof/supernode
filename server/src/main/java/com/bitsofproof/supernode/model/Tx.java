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
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.api.BloomFilter;
import com.bitsofproof.supernode.api.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ScriptFormat.Token;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

@Entity
@Table (name = "tx")
public class Tx implements Serializable
{
	private static final long serialVersionUID = 1L;

	public static final long COIN = 100000000;
	public static final long MAX_MONEY = 21000000L * COIN;
	// it should actually be 2099999997690000L, but above is in bitcoind

	@Id
	@GeneratedValue
	private Long id;

	private long version = 1;

	private long lockTime = 0;

	private long ix = 0;

	// this is not unique since a transaction copy might be on different branches.
	@Column (length = 64, nullable = false)
	@Index (name = "txhash")
	private String hash;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<TxIn> inputs;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<TxOut> outputs;

	@ManyToOne (fetch = FetchType.LAZY, optional = false)
	private Blk block;

	private transient String blockHash;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public long getLockTime ()
	{
		return lockTime;
	}

	public void setLockTime (long lockTime)
	{
		this.lockTime = lockTime;
	}

	public List<TxIn> getInputs ()
	{
		return inputs;
	}

	public void setInputs (List<TxIn> inputs)
	{
		this.inputs = inputs;
	}

	public List<TxOut> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (List<TxOut> outputs)
	{
		this.outputs = outputs;
	}

	public String getHash ()
	{
		if ( hash == null )
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			toWire (writer);
			WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
			hash = reader.hash ().toString ();
		}
		return hash;
	}

	public static Tx fromLevelDB (byte[] data) throws ValidationException
	{
		LevelDBStore.TX p;
		try
		{
			p = LevelDBStore.TX.parseFrom (data);
			Tx t = new Tx ();

			t.setBlockHash (new Hash (p.getBlockHash ().toByteArray ()).toString ());
			t.hash = new Hash (p.getHash ().toByteArray ()).toString ();
			t.version = p.getVersion ();
			t.lockTime = p.getLockTime ();
			t.ix = p.getIx ();
			if ( p.getTxinCount () > 0 )
			{
				List<TxIn> inputs = new ArrayList<TxIn> (p.getTxinCount ());
				for ( LevelDBStore.TX.TXIN i : p.getTxinList () )
				{
					TxIn in = new TxIn ();
					in.setIx ((long) i.getIx ());
					in.setSourceHash (new Hash (i.getSourceHash ().toByteArray ()).toString ());
					in.setScript (i.getScript ().toByteArray ());
					in.setSequence (i.getSequence ());
					inputs.add (in);
				}
				t.setInputs (inputs);
			}
			if ( p.getTxoutCount () > 0 )
			{
				List<TxOut> outputs = new ArrayList<TxOut> (p.getTxoutCount ());
				long ix = 0;
				for ( LevelDBStore.TX.TXOUT o : p.getTxoutList () )
				{
					TxOut out = new TxOut ();
					out.setValue (o.getValue ());
					out.setScript (o.getScript ().toByteArray ());
					out.setIx (ix++);
					out.setHeight (o.getHeight ());
					out.setCoinbase (o.getCoinbase ());
					out.setAvailable (o.getAvailable ());
					out.setTxHash (t.hash);
					if ( o.hasColor () )
					{
						out.setColor (o.getColor ());
					}
					outputs.add (out);
				}
				t.setOutputs (outputs);
			}
			return t;
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new ValidationException (e);
		}
	}

	public byte[] toLevelDB ()
	{
		LevelDBStore.TX.Builder builder = LevelDBStore.TX.newBuilder ();

		if ( blockHash == null )
		{
			blockHash = block.getHash ();
		}
		builder.setStoreVersion (1);
		builder.setBlockHash (ByteString.copyFrom (new Hash (blockHash).toByteArray ()));
		builder.setHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
		builder.setVersion ((int) version);
		builder.setLockTime ((int) lockTime);
		builder.setIx ((int) ix);
		if ( inputs != null )
		{
			for ( TxIn in : inputs )
			{
				LevelDBStore.TX.TXIN.Builder b = LevelDBStore.TX.TXIN.newBuilder ();
				b.setIx (in.getIx ().intValue ());
				b.setSourceHash (ByteString.copyFrom (new Hash (in.getSourceHash ()).toByteArray ()));
				b.setScript (ByteString.copyFrom (in.getScript ()));
				b.setSequence ((int) in.getSequence ());
				builder.addTxin (b.build ());
			}
		}
		if ( outputs != null )
		{
			for ( TxOut o : outputs )
			{
				LevelDBStore.TX.TXOUT.Builder b = LevelDBStore.TX.TXOUT.newBuilder ();
				b.setValue (o.getValue ());
				b.setScript (ByteString.copyFrom (o.getScript ()));
				b.setHeight ((int) o.getHeight ());
				b.setAvailable (o.isAvailable ());
				b.setCoinbase (o.isCoinbase ());
				if ( o.getColor () != null )
				{
					b.setColor (o.getColor ());
				}
				builder.addTxout (b.build ());
			}
		}
		return builder.build ().toByteArray ();
	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	public static Tx fromWireDump (String s)
	{
		WireFormat.Reader reader = new WireFormat.Reader (ByteUtils.fromHex (s));
		Tx b = new Tx ();
		b.fromWire (reader);
		return b;
	}

	public Blk getBlock ()
	{
		return block;
	}

	public void setBlock (Blk block)
	{
		this.block = block;
	}

	public Long getIx ()
	{
		return ix;
	}

	public void setIx (Long ix)
	{
		this.ix = ix;
	}

	public boolean passesFilter (BloomFilter filter)
	{
		boolean found = false;
		if ( filter.contains (new Hash (hash).toByteArray ()) )
		{
			found = true;
		}
		for ( TxOut out : outputs )
		{
			try
			{
				List<Token> tokens = ScriptFormat.parse (out.getScript ());
				for ( Token t : tokens )
				{
					if ( t.data != null && filter.contains (t.data) )
					{
						if ( filter.getUpdateMode () == UpdateMode.all )
						{
							filter.addOutpoint (hash, out.getIx ());
						}
						else if ( filter.getUpdateMode () == UpdateMode.keys )
						{
							if ( ScriptFormat.isPayToKey (out.getScript ()) || ScriptFormat.isMultiSig (out.getScript ()) )
							{
								filter.addOutpoint (hash, out.getIx ());
							}
						}
						found = true;
						break;
					}
				}
			}
			catch ( ValidationException e )
			{
			}
		}
		if ( found )
		{
			return true;
		}

		for ( TxIn in : inputs )
		{
			if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				if ( filter.containsOutpoint (in.getSourceHash (), in.getIx ()) )
				{
					return true;
				}
				try
				{
					List<Token> tokens = ScriptFormat.parse (in.getScript ());
					for ( Token t : tokens )
					{
						if ( t.data != null && filter.contains (t.data) )
						{
							return true;
						}
					}
				}
				catch ( ValidationException e )
				{
				}
			}
		}
		return false;
	}

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("hash", getHash ());
			o.put ("version", version);
			List<JSONObject> ins = new ArrayList<JSONObject> ();
			for ( TxIn input : inputs )
			{
				ins.add (input.toJSON ());
			}
			o.put ("inputs", ins);
			List<JSONObject> outs = new ArrayList<JSONObject> ();
			for ( TxOut output : outputs )
			{
				outs.add (output.toJSON ());
			}
			o.put ("outputs", outs);
			o.put ("lockTime", lockTime);
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		if ( inputs != null )
		{
			writer.writeVarInt (inputs.size ());
			for ( TxIn input : inputs )
			{
				input.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		if ( outputs != null )
		{
			writer.writeVarInt (outputs.size ());
			for ( TxOut output : outputs )
			{
				output.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		writer.writeUint32 (lockTime);
	}

	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor ();

		version = reader.readUint32 ();
		long nin = reader.readVarInt ();
		if ( nin > 0 )
		{
			inputs = new ArrayList<TxIn> ();
			for ( int i = 0; i < nin; ++i )
			{
				TxIn input = new TxIn ();
				input.fromWire (reader);
				input.setTransaction (this);
				inputs.add (input);
			}
		}
		else
		{
			inputs = null;
		}

		long nout = reader.readVarInt ();
		if ( nout > 0 )
		{
			outputs = new ArrayList<TxOut> ();
			for ( long i = 0; i < nout; ++i )
			{
				TxOut output = new TxOut ();
				output.fromWire (reader);
				output.setTransaction (this);
				output.setIx (i);
				outputs.add (output);
			}
		}
		else
		{
			outputs = null;
		}

		lockTime = reader.readUint32 ();

		hash = reader.hash (cursor, reader.getCursor () - cursor).toString ();

		if ( nout > 0 )
		{
			for ( TxOut out : outputs )
			{
				out.setTxHash (hash);
			}
		}
	}

	public Tx flatCopy ()
	{
		Tx c = new Tx ();
		c.id = id;
		c.hash = hash;
		c.lockTime = lockTime;
		c.version = version;
		c.inputs = new ArrayList<TxIn> ();
		for ( TxIn in : inputs )
		{
			c.inputs.add (in.flatCopy (c));
		}
		c.outputs = new ArrayList<TxOut> ();
		for ( TxOut out : outputs )
		{
			c.outputs.add (out.flatCopy (c));
		}

		return c;
	}

	public String getBlockHash ()
	{
		return blockHash;
	}

	public void setBlockHash (String blockHash)
	{
		this.blockHash = blockHash;
	}
}
