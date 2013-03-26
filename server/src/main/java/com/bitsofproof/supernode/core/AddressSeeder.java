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
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;

public class AddressSeeder implements Runnable
{
	private static final Logger log = LoggerFactory.getLogger (AddressSeeder.class);

	private final BitcoinNetwork network;
	private int delay = 60;
	private int startDelay = 60;

	public AddressSeeder (BitcoinNetwork network)
	{
		this.network = network;
		network.scheduleJobWithFixedDelay (this, startDelay, delay, TimeUnit.SECONDS);
	}

	@Override
	public void run ()
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
				peer.send (m);
			}
		}
		log.trace ("Sent an address to peers " + pick.getAddress ().getAddress ());
	}

	public int getDelay ()
	{
		return delay;
	}

	public void setDelay (int delay)
	{
		this.delay = delay;
	}

	public int getStartDelay ()
	{
		return startDelay;
	}

	public void setStartDelay (int startDelay)
	{
		this.startDelay = startDelay;
	}
}
