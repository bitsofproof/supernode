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
import java.util.List;

import com.bitsofproof.supernode.api.Address.Type;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Token;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.google.protobuf.ByteString;

public class TransactionOutput implements Serializable, Cloneable
{
	private static final long serialVersionUID = 3028618872354766234L;

	private String txHash;
	private long ix;
	private long value;
	private byte[] script;
	private String color;

	public String getTxHash ()
	{
		return txHash;
	}

	public void setTxHash (String txHash)
	{
		this.txHash = txHash;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
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

	public Address getOutputAddress ()
	{
		if ( ScriptFormat.isPayToAddress (script) )
		{
			List<Token> tokens;
			try
			{
				tokens = ScriptFormat.parse (script);
				return new Address (Type.COMMON, tokens.get (2).data);
			}
			catch ( ValidationException e )
			{
			}
		}
		else if ( ScriptFormat.isPayToScriptHash (script) )
		{
			List<Token> tokens;
			try
			{
				tokens = ScriptFormat.parse (script);
				return new Address (Type.P2SH, tokens.get (1).data);
			}
			catch ( ValidationException e )
			{
			}
		}
		return null;
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
		if ( color != null )
		{
			o.color = color;
		}
		o.ix = ix;
		o.txHash = txHash;
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
		if ( po.hasColor () )
		{
			output.color = new Hash (po.getColor ().toByteArray ()).toString ();
		}
		return output;
	}
}
