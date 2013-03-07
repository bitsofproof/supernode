/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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

import org.bouncycastle.util.Arrays;

public class ECPublicKey implements Key
{
	private byte[] pub;
	private int addressFlag;
	private boolean compressed;

	public ECPublicKey (byte[] pub, boolean compressed, int addressFlag)
	{
		this.pub = pub;
		this.addressFlag = addressFlag;
		this.compressed = compressed;
	}

	@Override
	public int getAddressFlag ()
	{
		return addressFlag;
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

	public void setAddressFlag (int addressFlag)
	{
		this.addressFlag = addressFlag;
	}

	@Override
	public byte[] getAddress ()
	{
		return Hash.keyHash (pub);
	}

	@Override
	public ECPublicKey clone () throws CloneNotSupportedException
	{
		ECPublicKey c = (ECPublicKey) super.clone ();
		c.pub = Arrays.clone (pub);
		c.addressFlag = addressFlag;
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

}
