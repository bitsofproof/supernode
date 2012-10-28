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

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.InvMessage;

public class UnconfirmedTransactions implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (UnconfirmedTransactions.class);

	private final Set<String> seenHashes = new HashSet<String> ();

	private final BitcoinNetwork network;

	public UnconfirmedTransactions (BitcoinNetwork network)
	{
		this.network = network;
	}

	public void start ()
	{
		network.addListener ("inv", this);
	}

	@Override
	public synchronized void process (BitcoinPeer.Message m, BitcoinPeer peer)
	{
		if ( m instanceof InvMessage )
		{
			InvMessage im = (InvMessage) m;
			for ( byte[] h : im.getTransactionHashes () )
			{
				String hash = new Hash (h).toString ();
				if ( seenHashes.contains (hash) )
				{
					return;
				}
				seenHashes.add (hash);
				log.info ("new transaction " + hash);
			}
		}
	}
}
