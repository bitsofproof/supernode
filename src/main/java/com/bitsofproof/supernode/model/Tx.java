/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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

import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "tx")
public class Tx implements Serializable
{
	private static final long serialVersionUID = 1L;

	public static final long COIN = 100000000;
	public static final long MAX_MONEY = 2099999997690000L;

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

	public static Tx fromLevelDB (byte[] data)
	{
		WireFormat.Reader reader = new WireFormat.Reader (data);
		Tx t = new Tx ();

		t.blockHash = reader.readHash ().toString ();
		t.hash = reader.readHash ().toString ();
		t.version = reader.readUint32 ();
		t.lockTime = reader.readUint32 ();
		t.ix = reader.readUint32 ();
		int n = (int) reader.readVarInt ();
		t.setInputs (new ArrayList<TxIn> (n));
		for ( int i = 0; i < n; ++i )
		{
			TxIn in = new TxIn ();
			in.setIx (reader.readUint32 ());
			in.setSourceHash (reader.readHash ().toString ());
			in.setScript (reader.readVarBytes ());
			in.setSequence (reader.readUint32 ());
			t.getInputs ().add (in);
		}

		n = (int) reader.readVarInt ();
		t.setOutputs (new ArrayList<TxOut> ());
		for ( int i = 0; i < n; ++i )
		{
			TxOut o = new TxOut ();
			o.setValue (reader.readUint64 ());
			o.setScript (reader.readVarBytes ());
			o.setIx (reader.readUint32 ());
			o.setVotes (reader.readUint32 ());
			o.setCoinbase (reader.readByte () != 0);
			o.setOwner1 (reader.readString ());
			o.setOwner2 (reader.readString ());
			o.setOwner3 (reader.readString ());
			o.setAvailable (reader.readByte () != 0);
			o.setTxHash (reader.readHash ().toString ());
			o.setHeight (reader.readUint32 ());
			t.getOutputs ().add (o);
		}
		return t;
	}

	public byte[] toLevelDB ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		if ( blockHash == null )
		{
			blockHash = block.getHash ();
		}
		writer.writeHash (new Hash (blockHash));
		writer.writeHash (new Hash (hash));
		writer.writeUint32 (version);
		writer.writeUint32 (lockTime);
		writer.writeUint32 (ix);
		writer.writeVarInt (inputs.size ());
		for ( TxIn in : inputs )
		{
			writer.writeUint32 (in.getIx ());
			writer.writeHash (new Hash (in.getSourceHash ()));
			writer.writeBytes (in.getScript ());
			writer.writeUint32 (in.getSequence ());
		}
		writer.writeVarInt (outputs.size ());
		for ( TxOut o : outputs )
		{
			writer.writeUint64 (o.getValue ());
			writer.writeBytes (o.getScript ());
			writer.writeUint32 (o.getIx ());
			writer.writeUint32 (o.getVotes ());
			writer.writeByte (o.isCoinbase () ? 1 : 0);
			writer.writeString (o.getOwner1 ());
			writer.writeString (o.getOwner2 ());
			writer.writeString (o.getOwner3 ());
			writer.writeByte (o.isAvailable () ? 1 : 0);
			writer.writeHash (new Hash (o.getTxHash ()));
			writer.writeUint32 (o.getHeight ());
		}
		return writer.toByteArray ();
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
				output.setTxHash (hash);
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
