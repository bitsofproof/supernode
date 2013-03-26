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
package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;

public class TransactionOutput implements Serializable, Cloneable
{
	private static final long serialVersionUID = 3028618872354766234L;

	private long value;
	private byte[] script;
	private String transactionHash;
	private long selfIx;
	private long votes;
	private List<String> addresses;
	private String color;

	public void parseOwners (int addressFlag, int p2sAddressFlag)
	{
		try
		{
			addresses = new ArrayList<String> ();
			List<ScriptFormat.Token> parsed;
			parsed = ScriptFormat.parse (script);
			if ( parsed.size () == 2 && parsed.get (0).data != null && parsed.get (1).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to key
				addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (0).data), addressFlag));
				setVotes (1L);
			}
			else if ( parsed.size () == 5 && parsed.get (0).op == ScriptFormat.Opcode.OP_DUP && parsed.get (1).op == ScriptFormat.Opcode.OP_HASH160
					&& parsed.get (2).data != null && parsed.get (3).op == ScriptFormat.Opcode.OP_EQUALVERIFY
					&& parsed.get (4).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to address
				addresses.add (AddressConverter.toSatoshiStyle (parsed.get (2).data, addressFlag));
				setVotes (1L);
			}
			else if ( parsed.size () == 3 && parsed.get (0).op == ScriptFormat.Opcode.OP_HASH160 && parsed.get (1).data != null
					&& parsed.get (1).data.length == 20 && parsed.get (2).op == ScriptFormat.Opcode.OP_EQUAL )
			{
				byte[] hash = parsed.get (1).data;
				if ( hash.length == 20 )
				{
					// BIP 0013
					addresses.add (AddressConverter.toSatoshiStyle (hash, p2sAddressFlag));
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
								addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag));
							}
							if ( j == 1 )
							{
								addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag));
							}
							if ( j == 2 )
							{
								addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), addressFlag));
							}
						}
						setVotes ((long) parsed.get (i - nkeys - 2).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1);
						return;
					}
				}
			}
		}
		catch ( ValidationException e )
		{
			// this is best effort.
		}
	}

	public String getTransactionHash ()
	{
		return transactionHash;
	}

	public void setTransactionHash (String transactionHash)
	{
		this.transactionHash = transactionHash;
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
			byte[] copy = new byte[script.length];
			System.arraycopy (script, 0, copy, 0, script.length);
			return copy;
		}
		return null;
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
			this.script = null;
		}
	}

	public long getSelfIx ()
	{
		return selfIx;
	}

	public void setSelfIx (long selfIx)
	{
		this.selfIx = selfIx;
	}

	public long getVotes ()
	{
		return votes;
	}

	public void setVotes (long votes)
	{
		this.votes = votes;
	}

	public List<String> getAddresses ()
	{
		return addresses;
	}

	public void setAddresses (List<String> addresses)
	{
		this.addresses = addresses;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64 (value);
		writer.writeVarBytes (script);
	}

	public static TransactionOutput fromWire (WireFormat.Reader reader)
	{
		TransactionOutput o = new TransactionOutput ();
		o.value = reader.readUint64 ();
		o.script = reader.readVarBytes ();
		return o;
	}

	@Override
	public TransactionOutput clone () throws CloneNotSupportedException
	{
		TransactionOutput o = (TransactionOutput) super.clone ();
		o.value = value;
		if ( script != null )
		{
			o.script = new byte[script.length];
			System.arraycopy (script, 0, o.script, 0, script.length);
		}
		o.transactionHash = transactionHash;
		o.selfIx = selfIx;
		o.votes = votes;
		if ( addresses != null )
		{
			o.addresses = new ArrayList<String> ();
			o.addresses.addAll (addresses);
		}
		if ( color != null )
		{
			o.color = color;
		}
		return o;

	}

	public String getColor ()
	{
		return color;
	}

	public void setColor (String color)
	{
		this.color = color;
	}

	public BCSAPIMessage.TransactionOutput toProtobuf ()
	{
		BCSAPIMessage.TransactionOutput.Builder builder = BCSAPIMessage.TransactionOutput.newBuilder ();
		builder.setScript (ByteString.copyFrom (script));
		builder.setValue (value);
		if ( transactionHash != null )
		{
			builder.setTransaction (ByteString.copyFrom (new Hash (transactionHash).toByteArray ()));
			builder.setSelfix ((int) selfIx);
		}
		if ( addresses != null )
		{
			for ( int i = 0; i < addresses.size (); ++i )
			{
				builder.addAddress (addresses.get (i));
			}
			builder.setVotes ((int) votes);
		}
		if ( color != null )
		{
			builder.setColor (ByteString.copyFrom (new Hash (color).toByteArray ()));
		}
		return builder.build ();
	}

	public static TransactionOutput fromProtobuf (BCSAPIMessage.TransactionOutput po)
	{
		TransactionOutput output = new TransactionOutput ();
		output.setScript (po.getScript ().toByteArray ());
		output.setValue (po.getValue ());
		if ( po.hasTransaction () )
		{
			output.setTransactionHash (new Hash (po.getTransaction ().toByteArray ()).toString ());
			output.setSelfIx (po.getSelfix ());
		}
		if ( po.hasVotes () )
		{
			output.setAddresses (po.getAddressList ());
			output.setVotes (po.getVotes ());
		}
		if ( po.hasColor () )
		{
			output.color = new Hash (po.getColor ().toByteArray ()).toString ();
		}
		return output;
	}
}
