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

import com.bitsofproof.supernode.core.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;

public class AddressSeeder implements Runnable
{
	private static final Logger log = LoggerFactory.getLogger (AddressSeeder.class);

	BitcoinNetwork network;

	public AddressSeeder (BitcoinNetwork network)
	{
		this.network = network;
	}

	@Override
	public void run ()
	{
		try
		{
			BitcoinPeer[] peers = network.getConnectPeers ();
			if ( peers.length == 0 )
			{
				return;
			}
			BitcoinPeer pick = peers[(int) (Math.floor (Math.random () * peers.length))];
			List<Address> al = new ArrayList<Address> ();
			Address address = new Address ();
			address.address = pick.getAddress ().getAddress ();
			address.port = network.getPort ();
			address.services = pick.getServices ();
			address.time = System.currentTimeMillis () / 1000;
			al.add (address);
			for ( BitcoinPeer peer : peers )
			{
				if ( peer != pick )
				{
					AddrMessage m = new AddrMessage (peer);
					m.setAddresses (al);
					try
					{
						peer.send (m);
					}
					catch ( Exception e )
					{
					}
				}
				log.trace ("Sent an address to peers " + pick.getAddress ().getAddress ());
			}
		}
		catch ( Exception e )
		{
			log.error ("Exception while broadcasting addresses", e);
		}
	}

}
