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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import com.bitsofproof.supernode.core.Base58;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.WireFormat;

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
	private byte[] script;

	@ManyToOne (fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.DETACH, CascadeType.PERSIST, CascadeType.REFRESH }, optional = false)
	private Tx transaction;

	private long ix;

	@OneToOne (fetch = FetchType.LAZY, optional = true)
	private TxIn sink;

	@Column (length = 40, nullable = true)
	private String address;

	public String toJSON ()
	{
		StringBuffer b = new StringBuffer ();
		b.append ("{");
		b.append ("\"value\":" + getValue () + ",");
		b.append ("\"script\":\"" + new Script (script).toString () + "\"");
		b.append ("}");
		return b.toString ();
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64 (getValue ());
		writer.writeVarBytes (getScript ());
	}

	public void fromWire (WireFormat.Reader reader)
	{

		value = reader.readUint64 ();
		script = reader.readVarBytes ();

		// TODO: real script interpretation needed here
		byte[] ph = new byte[20];
		if ( script.length < 2 )
		{
			return;
		}

		if ( script[0] == 0x76 && script.length >= 22 )
		{
			// new style
			System.arraycopy (script, 2, ph, 0, 20);
			address = Base58.encode (ph);
		}
		else
		{
			// old style
			byte[] key = new byte[(script[0] & 0xff)];
			if ( script.length <= script[0] + 1 )
			{
				return;
			}
			System.arraycopy (script, 1, key, 0, script[0]);
			byte[] sha256;
			try
			{
				sha256 = MessageDigest.getInstance ("SHA-256").digest (key);
				RIPEMD160Digest digest = new RIPEMD160Digest ();
				digest.update (sha256, 0, sha256.length);
				digest.doFinal (ph, 0);

				byte[] addressBytes = new byte[1 + ph.length + 4];
				addressBytes[0] = (byte) 0; // 0 for production
				System.arraycopy (ph, 0, addressBytes, 1, ph.length);
				byte[] check = Hash.hash (addressBytes, 0, ph.length + 1).toByteArray ();
				System.arraycopy (check, 0, addressBytes, ph.length + 1, 4);
				address = Base58.encode (addressBytes);

			}
			catch ( NoSuchAlgorithmException e )
			{
			}
		}
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
		return script;
	}

	public void setScript (byte[] script)
	{
		this.script = script;
	}

	public Tx getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (Tx transaction)
	{
		this.transaction = transaction;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
	}

	public TxIn getSink ()
	{
		return sink;
	}

	public void setSink (TxIn sink)
	{
		this.sink = sink;
	}

	public String getAddress ()
	{
		return address;
	}

	public void setAddress (String address)
	{
		this.address = address;
	}

	protected TxOut flatCopy (Tx tc)
	{
		TxOut c = new TxOut ();
		c.address = address;
		c.ix = ix;
		c.script = script;
		c.sink = sink;
		c.transaction = tc;
		c.value = value;

		return c;
	}

}
