package com.bitsofproof.supernode.api;

import java.math.BigInteger;

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
