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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DNSDiscovery implements Discovery
{
	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);
	private static final String[] seedHosts = new String[] { "dnsseed.bluematt.me", // Matt Corallo
			"bitseed.xf2.org", // Jeff Garzik
			"seed.bitcoin.sipa.be", // Pieter Wuille
			"dnsseed.bitcoin.dashjr.org", // Luke Dashjr
	};

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering network");
		int n = 0;
		List<InetAddress> al = new ArrayList<InetAddress> ();
		for ( String hostName : seedHosts )
		{
			try
			{
				InetAddress[] hostAddresses = InetAddress.getAllByName (hostName);

				for ( InetAddress inetAddress : hostAddresses )
				{
					al.add (inetAddress);
					++n;
				}
			}
			catch ( Exception e )
			{
				log.trace ("DNS lookup for " + hostName + " failed.");
			}
		}

		log.trace ("Found " + n + " addresses of seed hosts");
		return al;
	}
}
