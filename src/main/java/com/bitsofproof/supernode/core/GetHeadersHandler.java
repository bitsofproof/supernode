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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetHeadersMessage;
import com.bitsofproof.supernode.messages.HeadersMessage;
import com.bitsofproof.supernode.model.Blk;

public class GetHeadersHandler implements BitcoinMessageListener<GetHeadersMessage>
{
	private final BlockStore store;
	private static final Logger log = LoggerFactory.getLogger (GetHeadersHandler.class);
	private PlatformTransactionManager transactionManager;

	public GetHeadersHandler (BitcoinNetwork network)
	{
		network.addListener ("getheader", this);
		store = network.getStore ();
	}

	@Override
	public void process (GetHeadersMessage m, BitcoinPeer peer)
	{
		log.trace ("received getheader from " + peer.getAddress ());
		List<String> locator = new ArrayList<String> ();
		for ( byte[] h : m.getLocators () )
		{
			locator.add (new Hash (h).toString ());
		}
		List<String> inventory = store.getInventory (locator, new Hash (m.getStop ()).toString (), 2000);
		final HeadersMessage hm = (HeadersMessage) peer.createMessage ("headers");
		for ( final String h : inventory )
		{
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();

					Blk b = store.getBlock (h);
					WireFormat.Writer writer = new WireFormat.Writer ();
					b.toWireHeaderOnly (writer);
					hm.getBlockHeader ().add (writer.toByteArray ());
				}
			});
		}
		if ( hm.getBlockHeader ().size () > 0 )
		{
			peer.send (hm);
		}
		log.trace ("delivered inventory to " + peer.getAddress ());
	}

}
