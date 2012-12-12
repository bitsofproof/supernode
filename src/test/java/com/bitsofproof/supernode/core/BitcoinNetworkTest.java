/**
 * 
 */
package com.bitsofproof.supernode.core;

import org.junit.Test;

import com.bitsofproof.supernode.core.BitcoinNetwork.JobWrapper.WrappedRunnable;

import static org.junit.Assert.*;

/**
 * @author Bártfai Tamás <bartfaitamas@gmail.com>
 */
public class BitcoinNetworkTest
{

	static class TestRunnable implements Runnable
	{
		int runCount = 0;
		
		@Override
		public void run ()
		{
			runCount += 1;
		}
		
		public void assertRuns(int count)
		{
			assertEquals(count, runCount);
		}
		
	}
	
	@Test
	public void testWrappedSingleRunnable ()
	{
		TestRunnable testJob = new TestRunnable ();
		
		BitcoinNetwork.JobWrapper wrapper = new BitcoinNetwork.JobWrapper();
		WrappedRunnable wrappedJob = wrapper.wrap (testJob, true);
		wrappedJob.run ();
		wrappedJob.run ();
		wrappedJob.run ();
		
		testJob.assertRuns (1);		
	}

	@Test
	public void testWrappedMultiRunnable()
	{
		TestRunnable testJob = new TestRunnable ();
		
		BitcoinNetwork.JobWrapper wrapper = new BitcoinNetwork.JobWrapper();
		WrappedRunnable wrappedJob = wrapper.wrap (testJob, false);
		wrappedJob.run ();
		wrappedJob.run ();
		wrappedJob.run ();
		
		testJob.assertRuns (3);		
		
	}
}
