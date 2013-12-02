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
package com.bitsofproof.supernode.common;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.api.Address;

public class ECPublicKey implements Key
{
	private byte[] pub;
	private boolean compressed;

	public ECPublicKey (byte[] pub, boolean compressed)
	{
		this.pub = pub;
		this.compressed = compressed;
	}

	@Override
	public boolean isCompressed ()
	{
		return compressed;
	}

	public void setCompressed (boolean compressed)
	{
		this.compressed = compressed;
	}

	@Override
	public Address getAddress ()
	{
		try
		{
			return new Address (Address.Type.COMMON, Hash.keyHash (pub));
		}
		catch ( ValidationException e )
		{
			return null;
		}
	}

	@Override
	public ECPublicKey clone () throws CloneNotSupportedException
	{
		ECPublicKey c = (ECPublicKey) super.clone ();
		c.pub = Arrays.clone (pub);
		return c;
	}

	@Override
	public byte[] getPrivate ()
	{
		return null;
	}

	@Override
	public byte[] getPublic ()
	{
		return Arrays.clone (pub);
	}

	@Override
	public byte[] sign (byte[] data) throws ValidationException
	{
		throw new ValidationException ("Can not sign with public key");
	}

	@Override
	public boolean verify (byte[] hash, byte[] signature)
	{
		return ECKeyPair.verify (hash, signature, pub);
	}

	@Override
	public Key getReadOnly ()
	{
		return this;
	}
}
