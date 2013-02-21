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
package com.bitsofproof.supernode.messages;

import java.security.SecureRandom;

import com.bitsofproof.supernode.api.WireFormat.Reader;
import com.bitsofproof.supernode.api.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

public class PingMessage extends BitcoinPeer.Message
{
	public long nonce;

	public PingMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("ping");
		nonce = new SecureRandom ().nextLong ();
	}

	@Override
	public void toWire (Writer writer)
	{
		if ( getVersion () > 60000 )
		{
			writer.writeUint64 (nonce);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		if ( getVersion () > 60000 )
		{
			nonce = reader.readUint64 ();
		}
	}

	public long getNonce ()
	{
		return nonce;
	}

	public void setNonce (long nonce)
	{
		this.nonce = nonce;
	}

}
