package com.bitsofproof.supernode.test;

import static com.bitsofproof.supernode.core.Difficulty.getCompactTarget;
import static com.bitsofproof.supernode.core.Difficulty.getDifficulty;
import static com.bitsofproof.supernode.core.Difficulty.getNextTarget;
import static com.bitsofproof.supernode.core.Difficulty.getTarget;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class DIfficultyTest {

	@Test
	public void targetTest ()
	{
		assertTrue(getTarget (454983370L).toString(16).
				equals("1e7eca000000000000000000000000000000000000000000000000"));
	}
	
	@Test
	public void compactTargetTest ()
	{
		assertTrue (getCompactTarget (getTarget (454983370L))==454983370L);
		assertTrue(getCompactTarget(
				new BigInteger("ffff0000000000000000000000000000000000000000000000000000",16))==486604799L);
	}
	
	@Test
	public void nextTargetTest ()
	{
		assertTrue(getNextTarget(841428L, 454983370L)==454375064L);
	}
	
	@Test 
	public void difficultyTest ()
	{
		assertTrue (getDifficulty (456101533L) == 1378.0);
		assertTrue (getDifficulty (486604799L) == 1.0);
	}
}
