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

public class ECPublicKey implements Key
{
	private final byte[] pub;

	public ECPublicKey (byte[] pub)
	{
		this.pub = pub;
	}

	@Override
	public byte[] getPrivate ()
	{
		return null;
	}

	@Override
	public byte[] getPublic ()
	{
		if ( pub != null )
		{
			byte[] p = new byte[pub.length];
			System.arraycopy (pub, 0, p, 0, pub.length);
			return p;
		}
		return null;
	}

}
