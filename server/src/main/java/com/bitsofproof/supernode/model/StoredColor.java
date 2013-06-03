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
import java.io.UnsupportedEncodingException;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.WireFormat;

@Entity
@Table (name = "color")
public class StoredColor implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false, unique = true)
	private String fungibleName;

	@Column (length = 64, nullable = false, unique = true)
	private String txHash;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	private String terms;

	private long unit;
	private int expiryHeight;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	private byte[] signature;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	private byte[] pubkey;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getTerms ()
	{
		return terms;
	}

	public void setFungibleName (String fungibleName)
	{
		this.fungibleName = fungibleName;
	}

	public String getFungibleName ()
	{
		return fungibleName;
	}

	public void setTerms (String terms)
	{
		this.terms = terms;
	}

	public long getUnit ()
	{
		return unit;
	}

	public void setUnit (long unit)
	{
		this.unit = unit;
	}

	public int getExpiryHeight ()
	{
		return expiryHeight;
	}

	public void setExpiryHeight (int expiryHeight)
	{
		this.expiryHeight = expiryHeight;
	}

	public byte[] getSignature ()
	{
		return signature;
	}

	public void setSignature (byte[] signature)
	{
		this.signature = signature;
	}

	public byte[] getPubkey ()
	{
		return pubkey;
	}

	public void setPubkey (byte[] pubkey)
	{
		this.pubkey = pubkey;
	}

	public String getTxHash ()
	{
		return txHash;
	}

	public void setTxHash (String txHash)
	{
		this.txHash = txHash;
	}

	public byte[] hashFungibleName ()
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
		return content;
	}

	private byte[] hashContent ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeHash (new Hash (txHash));
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

	public boolean verify ()
	{
		if ( !Hash.hash (hashFungibleName ()).equals (fungibleName) )
		{
			return false;
		}
		return ECKeyPair.verify (hashContent (), signature, pubkey);
	}
}
