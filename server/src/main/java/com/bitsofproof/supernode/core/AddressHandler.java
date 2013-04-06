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

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.api.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetAddrMessage;
import com.bitsofproof.supernode.model.KnownPeer;

public class AddressHandler implements BitcoinMessageListener<AddrMessage>
{
	private static final Logger log = LoggerFactory.getLogger (AddressHandler.class);
	private final BitcoinNetwork network;

	public AddressHandler (final BitcoinNetwork network)
	{
		this.network = network;
		network.addListener ("addr", this);
		network.addListener ("getaddr", new BitcoinMessageListener<GetAddrMessage> ()
		{
			@Override
			public void process (GetAddrMessage m, BitcoinPeer peer)
			{
				AddrMessage am = (AddrMessage) peer.createMessage ("addr");
				for ( BitcoinPeer p : network.getConnectPeers () )
				{
					InetSocketAddress is = p.getAddress ();
					WireFormat.Address a = new WireFormat.Address ();
					a.address = is.getAddress ();
					a.port = is.getPort ();
					a.services = peer.getServices ();
					a.time = System.currentTimeMillis () / 1000;
					am.getAddresses ().add (a);
				}
				peer.send (am);
				log.trace ("sent in reply to getaddr " + am.getAddresses ().size () + " peers");
			}
		});
	}

	@Override
	public void process (AddrMessage am, BitcoinPeer peer)
	{
		for ( Address a : am.getAddresses () )
		{
			log.trace ("received address " + a.address + " from " + peer.getAddress ().getAddress ());
			peer.getNetwork ().addPeer (a.address, (int) a.port);
			if ( network.getPeerStore () != null )
			{
				KnownPeer p;
				try
				{
					p = network.getPeerStore ().findPeer (a.address);
					if ( p == null )
					{
						p = new KnownPeer ();
						p.setAddress (a.address.getHostAddress ());
						p.setResponseTime (Integer.MAX_VALUE);
						network.getPeerStore ().store (p);
					}
				}
				catch ( ValidationException e )
				{
				}
			}
		}
	}
}
