package hu.blummers.bitcoin.test;

import java.math.BigInteger;

import org.junit.Test;
import static hu.blummers.bitcoin.core.Difficulty.*;
import static org.junit.Assert.*;

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
