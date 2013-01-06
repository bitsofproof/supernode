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

import org.junit.Test;

import com.bitsofproof.supernode.core.SatoshiChain;
import com.bitsofproof.supernode.core.Testnet3Chain;

public class GenesisTest
{
	@Test
	public void productionChainTest ()
	{
		new SatoshiChain ().getGenesis ().getHash ().toString ().equals ("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
	}

	@Test
	public void testChainTest ()
	{
		new Testnet3Chain ().getGenesis ().getHash ().toString ().equals ("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943");
	}
}
