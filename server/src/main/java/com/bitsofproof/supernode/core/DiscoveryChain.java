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
import java.util.ArrayList;
import java.util.List;

public class DiscoveryChain implements Discovery
{
	private final List<Discovery> discoveryChain;

	public DiscoveryChain (List<Discovery> chain)
	{
		discoveryChain = chain;
	}

	@Override
	public List<InetSocketAddress> discover ()
	{
		for ( Discovery discovery : discoveryChain )
		{
			List<InetSocketAddress> addresses = discovery.discover ();
			if ( !addresses.isEmpty () )
			{
				return addresses;
			}
		}
		return new ArrayList<InetSocketAddress> ();
	}
}
