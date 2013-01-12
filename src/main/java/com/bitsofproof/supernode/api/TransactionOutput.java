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
package com.bitsofproof.supernode.api;

import java.io.Serializable;

import org.json.JSONException;
import org.json.JSONObject;

public class TransactionOutput implements Serializable, Cloneable
{
	private static final long serialVersionUID = 3028618872354766234L;

	private long value;
	private byte[] script;
	private String transactionHash;

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
		return o;

	}

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
			catch ( Exception e )
			{
				o.put ("script", "0x" + ByteUtils.toHex (script));
			}
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public static TransactionOutput fromJSON (JSONObject o) throws JSONException
	{
		TransactionOutput out = new TransactionOutput ();
		out.value = o.getLong ("value");
		out.script = ScriptFormat.fromReadable (o.getString ("script"));
		return out;
	}
}
