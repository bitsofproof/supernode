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

import static com.bitsofproof.supernode.api.Difficulty.getCompactTarget;
import static com.bitsofproof.supernode.api.Difficulty.getDifficulty;
import static com.bitsofproof.supernode.api.Difficulty.getNextTarget;
import static com.bitsofproof.supernode.api.Difficulty.getTarget;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class DIfficultyTest
{

	@Test
	public void targetTest ()
	{
		assertTrue (getTarget (454983370L).toString (16).equals ("1e7eca000000000000000000000000000000000000000000000000"));
	}

	@Test
	public void compactTargetTest ()
	{
		assertTrue (getCompactTarget (getTarget (454983370L)) == 454983370L);
		assertTrue (getCompactTarget (new BigInteger ("ffff0000000000000000000000000000000000000000000000000000", 16)) == 486604799L);
	}

	@Test
	public void nextTargetTest ()
	{
		assertTrue (getNextTarget (841428L, 454983370L, 1209600) == 454375064L);
	}

	@Test
	public void difficultyTest ()
	{
		assertTrue (getDifficulty (456101533L) == 1378.0);
		assertTrue (getDifficulty (486604799L) == 1.0);
	}
}
