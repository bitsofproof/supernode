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
package com.bitsofproof.supernode.api;

import java.math.BigInteger;

public class Difficulty
{
	static final BigInteger minTarget = new BigInteger ("FFFF", 16).shiftLeft (8 * (0x1d - 3));

	public static BigInteger getTarget (long compactTarget)
	{
		return new BigInteger (Long.valueOf (compactTarget & 0x7fffffL).toString (), 10).shiftLeft ((int) (8 * ((compactTarget >>> 24) - 3)));
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

	public static long getNextTarget (long periodLength, long currentTarget, long targetTime)
	{
		// Limit the adjustment step.
		periodLength = Math.max (Math.min (periodLength, targetTime * 4), targetTime / 4);
		BigInteger newTarget = (getTarget (currentTarget).multiply (BigInteger.valueOf (periodLength))).divide (BigInteger.valueOf (targetTime));
		// not simpler than this
		if ( newTarget.compareTo (minTarget) > 0 )
		{
			newTarget = minTarget;
		}
		return getCompactTarget (newTarget);
	}
}
