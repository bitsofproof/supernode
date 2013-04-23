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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultAccount implements Account
{
	private final String name;
	private final ExtendedKey extended;
	private final Map<ByteVector, Key> keyForAddress = new HashMap<ByteVector, Key> ();

	public DefaultAccount (String name, boolean production)
	{
		this.name = name;
		extended = ExtendedKey.createNew ();
	}

	public DefaultAccount (String name, ExtendedKey extended, int nextSequence) throws ValidationException
	{
		this.name = name;
		this.extended = extended;
		for ( int i = 0; i < nextSequence; ++i )
		{
			Key key = extended.getKey (i);
			keyForAddress.put (new ByteVector (key.getAddress ()), key);
		}
	}

	@Override
	public String getName ()
	{
		return name;
	}

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> addresses = new ArrayList<byte[]> ();
		for ( ByteVector v : keyForAddress.keySet () )
		{
			addresses.add (v.toByteArray ());
		}
		return addresses;
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		return keyForAddress.get (new ByteVector (address));
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		Key key = extended.getKey (keyForAddress.size () + 1);
		keyForAddress.put (new ByteVector (key.getAddress ()), key);
		return key;
	}

	@Override
	public Key getKey (int ix) throws ValidationException
	{
		return extended.getKey (ix);
	}

}
