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

import java.math.BigInteger;

import com.bitsofproof.supernode.api.ChainParameter;

public class UnitTestChain implements ChainParameter
{
	static final BigInteger minTarget = BigInteger.valueOf (1L).shiftLeft (255).subtract (BigInteger.valueOf (1L));

	@Override
	public BigInteger getMinimumTarget ()
	{
		return minTarget;
	}

	@Override
	public int getDifficultyReviewBlocks ()
	{
		return 2016;
	}

	@Override
	public int getTargetBlockTime ()
	{
		return 1209600;
	}

	@Override
	public long getRewardForHeight (int height)
	{
		return (50L * 100000000) >> (height / 210000L);
	}

	@Override
	public boolean isProduction ()
	{
		return false;
	}

	@Override
	public int getAddressFlag ()
	{
		return 0x6F;
	}

	@Override
	public int getMultisigAddressFlag ()
	{
		return 0xc4;
	}
}
