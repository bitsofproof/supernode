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
package com.bitsofproof.supernode.test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.Discovery;

public class TestDiscovery implements Discovery
{
	private static final Logger log = LoggerFactory.getLogger (TestDiscovery.class);

	private String connectTo;

	@Override
	public List<InetAddress> discover ()
	{
		List<InetAddress> al = new ArrayList<InetAddress> ();
		try
		{
			al.add (InetAddress.getByName (connectTo));
		}
		catch ( UnknownHostException e )
		{
			log.error ("can not connect to " + connectTo);
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
