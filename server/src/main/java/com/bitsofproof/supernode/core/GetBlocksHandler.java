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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.InvMessage;

public class GetBlocksHandler implements BitcoinMessageListener<GetBlocksMessage>
{
	private static final Logger log = LoggerFactory.getLogger (GetBlocksHandler.class);
	private final BlockStore store;

	public GetBlocksHandler (BitcoinNetwork network)
	{
		network.addListener ("getblocks", this);
		store = network.getStore ();
	}

	@Override
	public void process (GetBlocksMessage m, BitcoinPeer peer)
	{
		log.trace ("received getblocks from " + peer.getAddress ());
		List<String> locator = new ArrayList<String> ();
		for ( byte[] h : m.getHashes () )
		{
			locator.add (new Hash (h).toString ());
		}
		List<String> inventory = store.getInventory (locator, new Hash (m.getLastHash ()).toString (), 500);
		InvMessage im = (InvMessage) peer.createMessage ("inv");
		for ( String h : inventory )
		{
			im.getBlockHashes ().add (new Hash (h).toByteArray ());
		}
		if ( !im.getBlockHashes ().isEmpty () )
		{
			peer.send (im);
			log.trace ("delivered inventory to " + peer.getAddress ());
		}
	}
}
