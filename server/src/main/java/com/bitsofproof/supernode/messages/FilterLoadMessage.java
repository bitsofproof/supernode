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
package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.api.BloomFilter;
import com.bitsofproof.supernode.api.WireFormat.Reader;
import com.bitsofproof.supernode.api.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

public class FilterLoadMessage extends BitcoinPeer.Message
{

	public FilterLoadMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("filterload");
	}

	private BloomFilter filter;

	public BloomFilter getFilter ()
	{
		return filter;
	}

	public void setFilter (BloomFilter filter)
	{
		this.filter = filter;
	}

	@Override
	public void toWire (Writer writer)
	{
		filter.toWire (writer);
	}

	@Override
	public void fromWire (Reader reader)
	{
		filter = BloomFilter.fromWire (reader);
	}
}
