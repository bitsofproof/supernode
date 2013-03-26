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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DNSDiscovery implements Discovery
{
	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);
	private String[] seedHosts;

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering network using DNS seed");
		int n = 0;
		List<InetAddress> al = new ArrayList<InetAddress> ();
		for ( String hostName : seedHosts )
		{
			log.trace ("Obtain addresses from " + hostName);
			InetAddress[] hostAddresses;
			try
			{
				hostAddresses = InetAddress.getAllByName (hostName);
				for ( InetAddress inetAddress : hostAddresses )
				{
					al.add (inetAddress);
					++n;
				}
			}
			catch ( UnknownHostException e )
			{
			}
		}
		Collections.shuffle (al);
		log.trace ("Found " + n + " addresses of seed hosts");
		return al;
	}

	public String[] getSeedHosts ()
	{
		return seedHosts;
	}

	public void setSeedHosts (String[] seedHosts)
	{
		this.seedHosts = seedHosts;
	}

}
