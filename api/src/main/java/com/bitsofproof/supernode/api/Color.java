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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.google.protobuf.ByteString;

public class Color
{
	private String transaction;
	private String terms;
	private long unit;
	private int expiryHeight;
	private byte[] pubkey;
	private byte[] signature;

	public BCSAPIMessage.Color toProtobuf ()
	{
		BCSAPIMessage.Color.Builder builder = BCSAPIMessage.Color.newBuilder ();
		builder.setTransaction (ByteString.copyFrom (new Hash (transaction).toByteArray ()));
		builder.setTerms (terms);
		builder.setUnit (unit);
		builder.setExpiryHeight (expiryHeight);
		builder.setPubkey (ByteString.copyFrom (pubkey));
		builder.setSignature (ByteString.copyFrom (signature));
		return builder.build ();
	}

	public static Color fromProtobuf (BCSAPIMessage.Color po)
	{
		Color color = new Color ();
		color.setTransaction (new Hash (po.getTransaction ().toByteArray ()).toString ());
		color.setTerms (po.getTerms ());
		color.setUnit (po.getUnit ());
		color.setExpiryHeight (po.getExpiryHeight ());
		color.setPubkey (po.getPubkey ().toByteArray ());
		color.setSignature (po.getSignature ().toByteArray ());
		return color;
	}

	public String getFungibleName ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		try
		{
			writer.writeBytes (terms.getBytes ("UTF-8"));
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		writer.writeUint64 (unit);
		writer.writeUint32 (expiryHeight);
		byte[] content = writer.toByteArray ();
		return new Hash (Hash.hash (content)).toString ();
	}

	private byte[] hashContent ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeHash (new Hash (transaction));
		try
		{
			writer.writeBytes (terms.getBytes ("UTF-8"));
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		writer.writeUint64 (unit);
		writer.writeUint32 (expiryHeight);
		writer.writeVarBytes (pubkey);
		byte[] content = writer.toByteArray ();
		return Hash.hash (content);
	}

	public void sign (Key key) throws ValidationException
	{
		if ( !Arrays.equals (key.getAddress (), Hash.keyHash (pubkey)) )
		{
			throw new ValidationException ("Wrong key to sign this color");
		}
		signature = key.sign (hashContent ());
	}

	public boolean verify ()
	{
		return ECKeyPair.verify (hashContent (), signature, pubkey);
	}

	public int getExpiryHeight ()
	{
		return expiryHeight;
	}

	public void setExpiryHeight (int expiryHeight)
	{
		this.expiryHeight = expiryHeight;
	}

	public long getUnit ()
	{
		return unit;
	}

	public void setUnit (long unit)
	{
		this.unit = unit;
	}

	public byte[] getSignature ()
	{
		return signature;
	}

	public void setSignature (byte[] signature)
	{
		this.signature = signature;
	}

	public String getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (String transaction)
	{
		this.transaction = transaction;
	}

	public String getTerms ()
	{
		return terms;
	}

	public void setTerms (String terms)
	{
		this.terms = terms;
	}

	public byte[] getPubkey ()
	{
		return pubkey;
	}

	public void setPubkey (byte[] pubkey)
	{
		this.pubkey = pubkey;
	}
}
