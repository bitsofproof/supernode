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
package com.bitsofproof.supernode.wallet;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;

public class AddressConverter
{
	private static final int PRODUCTION_FLAG = 0;
	private static final int TESTNET_FLAG = 111;

	public static byte[] fromSatoshiStyle (String s, int addressFlag) throws ValidationException
	{
		try
		{
			byte[] raw = ByteUtils.fromBase58 (s);
			if ( raw[0] != (byte) (addressFlag & 0xff) )
			{
				throw new ValidationException ("invalid address for this chain");
			}
			byte[] check = Hash.hash (raw, 0, raw.length - 4);
			for ( int i = 0; i < 4; ++i )
			{
				if ( check[i] != raw[raw.length - 4 + i] )
				{
					throw new ValidationException ("Address checksum mismatch");
				}
			}
			byte[] keyDigest = new byte[raw.length - 5];
			System.arraycopy (raw, 1, keyDigest, 0, raw.length - 5);
			return keyDigest;
		}
		catch ( Exception e )
		{
			throw new ValidationException (e);
		}
	}
	
	public static byte[] fromSatoshiStyle(String s, boolean production) throws ValidationException
	{
		return fromSatoshiStyle(s, production ? PRODUCTION_FLAG : TESTNET_FLAG);
	}
	
	public static String toSatoshiStyle (byte[] keyDigest, int addressFlag)
	{
		byte[] addressBytes = new byte[1 + keyDigest.length + 4];
		addressBytes[0] = (byte) (addressFlag & 0xff);
		System.arraycopy (keyDigest, 0, addressBytes, 1, keyDigest.length);
		byte[] check = Hash.hash (addressBytes, 0, keyDigest.length + 1);
		System.arraycopy (check, 0, addressBytes, keyDigest.length + 1, 4);
		return ByteUtils.toBase58 (addressBytes);
	}
	
	public static String toSatoshiStyle (byte[] keyDigest, boolean production)
	{
		return toSatoshiStyle(keyDigest, production ? PRODUCTION_FLAG : TESTNET_FLAG);
	}	
}
