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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class AddressHandler implements BitcoinMessageListener<AddrMessage>
{
	private static final Logger log = LoggerFactory.getLogger (AddressHandler.class);

	public AddressHandler (BitcoinNetwork network)
	{
		network.addListener ("addr", this);
	}

	@Override
	public void process (AddrMessage am, BitcoinPeer peer) throws Exception
	{
		for ( Address a : am.getAddresses () )
		{
			log.trace ("received address " + a.address + " from " + peer.getAddress ().getAddress ());
			peer.getNetwork ().addPeer (a.address, (int) a.port);
		}
	}
}
