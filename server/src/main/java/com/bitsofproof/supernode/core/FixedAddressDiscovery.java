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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FixedAddressDiscovery implements Discovery
{
	private static final Logger log = LoggerFactory.getLogger (FixedAddressDiscovery.class);

	private String connectTo;

	@Autowired
	private Chain chain;

	@Override
	public List<InetSocketAddress> discover ()
	{
		List<InetSocketAddress> al = new ArrayList<InetSocketAddress> ();
		try
		{
			String[] split = connectTo.split (":");
			if ( split.length == 2 )
			{
				al.add (new InetSocketAddress (InetAddress.getByName (split[0]), Integer.valueOf (split[1])));
			}
			else
			{
				al.add (new InetSocketAddress (InetAddress.getByName (split[0]), chain.getPort ()));
			}
		}
		catch ( Exception e )
		{
			log.error ("can not connect to " + connectTo, e);
		}
		return al;
	}

	public String getConnectTo ()
	{
		return connectTo;
	}

	public void setConnectTo (String connectTo)
	{
		this.connectTo = connectTo;
	}

}
