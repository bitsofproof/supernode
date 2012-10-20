package com.bitsofproof.supernode.core;

import java.math.BigInteger;

public class Difficulty
{
	static final BigInteger minTarget = new BigInteger ("FFFF", 16).shiftLeft (8 * (0x1d - 3));

	public static BigInteger getTarget (long compactTarget)
	{
		return new BigInteger (new Long (compactTarget & 0x7fffffL).toString (), 10).shiftLeft ((int) (8 * ((compactTarget >>> 24) - 3)));
	}

	public static double getDifficulty (long compactTarget)
	{
		return minTarget.divide (getTarget (compactTarget)).doubleValue ();
	}

	public static long getCompactTarget (BigInteger target)
	{
		int log2 = target.bitLength ();
		int s = (log2 / 8 + 1) * 8;

		return (target.shiftRight (s - 24)).or (new BigInteger (String.valueOf ((s - 24) / 8 + 3)).shiftLeft (24)).longValue ();
	}

	public static long getNextTarget (long periodLength, long currentTarget)
	{
		// Limit the adjustment step.
		periodLength = Math.max (Math.min (periodLength, 1209600 * 4), 1209600 / 4);
		BigInteger newTarget = (getTarget (currentTarget).multiply (BigInteger.valueOf (periodLength))).divide (BigInteger.valueOf (1209600));
		// not simpler than this
		if ( newTarget.compareTo (minTarget) > 0 )
		{
			newTarget = minTarget;
		}
		return getCompactTarget (newTarget);
	}
}
