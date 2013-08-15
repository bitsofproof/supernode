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

public class OrderedMapStoreKey
{
	public static enum KeyType
	{
		TX, BLOCK, HEAD;
	}

	public static byte[] createKey (KeyType kt, byte[] key)
	{
		byte[] k = new byte[key.length + 1];
		k[0] = (byte) kt.ordinal ();
		System.arraycopy (key, 0, k, 1, key.length);
		return k;
	}

	public static byte[] minKey (KeyType kt)
	{
		byte[] k = new byte[1];
		k[0] = (byte) kt.ordinal ();
		return k;
	}

	public static byte[] afterLAstKey (KeyType kt)
	{
		byte[] k = new byte[1];
		k[0] = (byte) (kt.ordinal () + 1);
		return k;
	}

	public static boolean hasType (KeyType kt, byte[] key)
	{
		return key[0] == (byte) kt.ordinal ();
	}
}
