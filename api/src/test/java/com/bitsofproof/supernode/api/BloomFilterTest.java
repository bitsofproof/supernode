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
package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.bitsofproof.supernode.common.BloomFilter;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;

public class BloomFilterTest
{
	@Test
	public void bloomTest ()
	{
		int n = 500;
		double falsepositiveTarget = 0.01;
		BloomFilter filter = BloomFilter.createOptimalFilter (n, falsepositiveTarget, UpdateMode.all);
		assertTrue (filter.getFalsePositiveProbability (n) < 0.02);

		List<byte[]> mustHave = new ArrayList<byte[]> ();
		SecureRandom rnd = new SecureRandom ();
		for ( int i = 0; i < n; ++i )
		{
			byte[] data = new byte[32];
			rnd.nextBytes (data);
			mustHave.add (data);
			filter.add (data);
		}
		for ( byte[] data : mustHave )
		{
			assertTrue (filter.contains (data));
		}
		int falsePositive = 0;
		for ( int i = 0; i < n * 100; ++i )
		{
			byte[] data = new byte[32];
			rnd.nextBytes (data);
			if ( filter.contains (data) )
			{
				++falsePositive;
			}
		}
		assertTrue (falsePositive < 2 * n * 100 * filter.getFalsePositiveProbability (n));
	}
}
