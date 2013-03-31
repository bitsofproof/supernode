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
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;

@Entity
@Table (name = "txout")
public class TxOut implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	private long value;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	// scriptPubKey
	private byte[] script;

	@ManyToOne (fetch = FetchType.LAZY, optional = false)
	private Tx transaction;

	@Column (nullable = false)
	private Long ix = new Long (0L);

	private Long votes;

	private boolean coinbase = false;

	// these are denormalized since only 3 owner allowed at the moment
	@Column (length = 40, nullable = true)
	@Index (name = "own1index")
	private String owner1;

	@Column (length = 40, nullable = true)
	@Index (name = "own2index")
	private String owner2;

	@Column (length = 40, nullable = true)
	@Index (name = "own3index")
	private String owner3;

	// indicate if available (UTXO)
	private boolean available = false;

	private String color;

	// this is redundant but saves a join at cacheing
	@Column (length = 64, nullable = false)
	@Index (name = "outthash")
	private String txHash;

	// this is redundant for a check and quick cache load
	@Index (name = "heightix")
	private long height;

	// this is redundant for joinless account balance
	@Index (name = "outtime")
	private long blockTime;

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("value", value);
			try
			{
				o.put ("script", ScriptFormat.toReadable (script));
			}
			catch ( ValidationException e )
			{
				o.put ("invalidScript", ByteUtils.toHex (script));
			}
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64 (value);
		writer.writeVarBytes (script);
	}

	public void fromWire (WireFormat.Reader reader)
	{
		value = reader.readUint64 ();
		script = reader.readVarBytes ();
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getValue ()
	{
		return value;
	}

	public void setValue (long value)
	{
		this.value = value;
	}

	public byte[] getScript ()
	{
		if ( script != null )
		{
			byte[] s = new byte[script.length];
			System.arraycopy (script, 0, s, 0, script.length);
			return s;
		}
		return script;
	}

	public void setScript (byte[] script)
	{
		if ( script != null )
		{
			this.script = new byte[script.length];
			System.arraycopy (script, 0, this.script, 0, script.length);
		}
		else
		{
			script = null;
		}
	}

	public Tx getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (Tx transaction)
	{
		this.transaction = transaction;
	}

	public Long getIx ()
	{
		return ix;
	}

	public void setIx (Long ix)
	{
		this.ix = ix;
	}

	public Long getVotes ()
	{
		return votes;
	}

	public void setVotes (Long votes)
	{
		this.votes = votes;
	}

	public String getOwner1 ()
	{
		return owner1;
	}

	public void setOwner1 (String owner1)
	{
		this.owner1 = owner1;
	}

	public String getOwner2 ()
	{
		return owner2;
	}

	public void setOwner2 (String owner2)
	{
		this.owner2 = owner2;
	}

	public String getOwner3 ()
	{
		return owner3;
	}

	public void setOwner3 (String owner3)
	{
		this.owner3 = owner3;
	}

	public boolean isCoinbase ()
	{
		return coinbase;
	}

	public void setCoinbase (boolean coinbase)
	{
		this.coinbase = coinbase;
	}

	protected TxOut flatCopy (Tx tc)
	{
		TxOut c = new TxOut ();
		c.id = id;
		c.ix = ix;
		c.script = script;
		c.transaction = tc;
		c.txHash = txHash;
		c.value = value;
		c.available = available;
		c.height = height;
		c.blockTime = blockTime;
		c.coinbase = coinbase;
		c.color = color;
		c.owner1 = owner1;
		c.owner2 = owner2;
		c.owner3 = owner3;
		c.votes = votes;
		return c;
	}

	public void parseOwners (int addressFlag, int p2sAddressFlag)
	{
		try
		{
			List<ScriptFormat.Token> parsed;
			parsed = ScriptFormat.parse (script);
			if ( parsed.size () == 2 && parsed.get (0).data != null && parsed.get (1).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to key
				owner1 = AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (0).data), addressFlag);
				setVotes (1L);
			}
			else if ( parsed.size () == 5 && parsed.get (0).op == ScriptFormat.Opcode.OP_DUP && parsed.get (1).op == ScriptFormat.Opcode.OP_HASH160
					&& parsed.get (2).data != null && parsed.get (3).op == ScriptFormat.Opcode.OP_EQUALVERIFY
					&& parsed.get (4).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to address
				owner1 = AddressConverter.toSatoshiStyle (parsed.get (2).data, addressFlag);
				setVotes (1L);
			}
			else if ( parsed.size () == 3 && parsed.get (0).op == ScriptFormat.Opcode.OP_HASH160 && parsed.get (1).data != null
					&& parsed.get (1).data.length == 20 && parsed.get (2).op == ScriptFormat.Opcode.OP_EQUAL )
			{
				byte[] hash = parsed.get (1).data;
				if ( hash.length == 20 )
				{
					// BIP 0013
					owner1 = AddressConverter.toSatoshiStyle (hash, p2sAddressFlag);
					setVotes (1L);
				}
			}
			else
			{
				for ( int i = 0; i < parsed.size (); ++i )
				{
					if ( parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIG || parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIGVERIFY )
					{
						int nkeys = parsed.get (i - 1).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1;
						for ( int j = 0; j < nkeys; ++j )
						{
							if ( j == 0 )
							{
								owner1 = AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag);
							}
							if ( j == 1 )
							{
								owner2 = AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag);
							}
							if ( j == 2 )
							{
								owner3 = AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag);
							}
						}
						setVotes ((long) parsed.get (i - nkeys - 2).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1);
						return;
					}
				}
			}
		}
		catch ( Exception e )
		{
			// this is best effort.
		}
	}

	public boolean isAvailable ()
	{
		return available;
	}

	public void setAvailable (boolean available)
	{
		this.available = available;
	}

	public String getTxHash ()
	{
		return txHash;
	}

	public void setTxHash (String txHash)
	{
		this.txHash = txHash;
	}

	public long getHeight ()
	{
		return height;
	}

	public void setHeight (long height)
	{
		this.height = height;
	}

	public long getBlockTime ()
	{
		return blockTime;
	}

	public void setBlockTime (long blockTime)
	{
		this.blockTime = blockTime;
	}

	public String getColor ()
	{
		return color;
	}

	public void setColor (String color)
	{
		this.color = color;
	}
}
