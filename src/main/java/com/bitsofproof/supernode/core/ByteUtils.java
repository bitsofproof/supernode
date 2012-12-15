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
package com.bitsofproof.supernode.core;

import java.io.UnsupportedEncodingException;

import org.bouncycastle.util.encoders.Hex;

public class ByteUtils
{
	public static byte[] reverse (byte[] data)
	{
		for ( int i = 0, j = data.length - 1; i < data.length / 2; i++, j-- )
		{
			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}

	public static String toHex (byte[] data)
	{
		try
		{
			return new String (Hex.encode (data), "US-ASCII");
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		return null;
	}

	public static byte[] fromHex (String hex)
	{
		return Hex.decode (hex);
	}
}
