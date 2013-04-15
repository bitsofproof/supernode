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
package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.FilterAddMessage;
import com.bitsofproof.supernode.messages.FilterClearMessage;
import com.bitsofproof.supernode.messages.FilterLoadMessage;

public class BloomFilterHandler
{
	private static final Logger log = LoggerFactory.getLogger (BloomFilterHandler.class);

	public BloomFilterHandler (final BitcoinNetwork network)
	{
		network.addListener ("filterclear", new BitcoinMessageListener<FilterClearMessage> ()
		{
			@Override
			public void process (FilterClearMessage m, BitcoinPeer peer)
			{
				peer.setFilter (null);
				log.trace ("cleared Bloom filter for " + peer.getAddress ());
			}
		});
		network.addListener ("filterload", new BitcoinMessageListener<FilterLoadMessage> ()
		{
			@Override
			public void process (FilterLoadMessage m, BitcoinPeer peer)
			{
				peer.setFilter (m.getFilter ());
				log.trace ("loaded Bloom filter for " + peer.getAddress ());
			}
		});
		network.addListener ("filteradd", new BitcoinMessageListener<FilterAddMessage> ()
		{
			@Override
			public void process (FilterAddMessage m, BitcoinPeer peer)
			{
				if ( peer.getFilter () != null )
				{
					peer.getFilter ().add (m.getData ());
					log.trace ("update Bloom filter for " + peer.getAddress ());
				}
			}
		});
	}
}
