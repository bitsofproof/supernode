package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jbehave.core.annotations.Configure;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.UsingEmbedder;
import org.jbehave.core.annotations.UsingSteps;
import org.jbehave.core.annotations.spring.UsingSpring;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.io.CodeLocations;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.junit.spring.SpringAnnotatedEmbedderRunner;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;

@RunWith (SpringAnnotatedEmbedderRunner.class)
@Configure
@UsingEmbedder (embedder = Embedder.class, threads = 1, generateViewAfterStories = false, ignoreFailureInStories = false, ignoreFailureInView = true, stepsFactory = true)
@UsingSpring (resources = "classpath:context/stories.xml")
@UsingSteps
public class StoriesRunner extends JUnitStories
{
	private static final Logger log = LoggerFactory.getLogger (StoriesRunner.class);

	@Autowired
	@Qualifier ("bitcoinNetwork")
	BitcoinNetwork network;

	@Autowired
	@Qualifier ("feederNetwork")
	BitcoinNetwork feeder;

	@Autowired
	@Qualifier ("production")
	Chain production;

	@Autowired
	BCSAPI directAPI;

	@Override
	protected List<String> storyPaths ()
	{
		return new StoryFinder ().findPaths (CodeLocations.codeLocationFromPath ("src/test/resources"), "*.story", "");
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	@Given ("an empty node using $chain")
	public void storeCleared (String chain) throws Exception
	{
		try
		{
			Chain c = null;
			if ( chain.equals ("production") )
			{
				c = production;
			}

			feeder.setChain (c);
			feeder.start ();

			network.setChain (c);
			network.getStore ().resetStore (c);
			network.getStore ().cache (c, 0);
			network.start ();
			Thread.sleep (1000);
		}
		catch ( Exception e )
		{
			log.error ("can not start new node", e);
			throw e;
		}
	}

	@Then ("connected")
	public void thenConnected ()
	{
		assertTrue (network.getNumberOfConnections () == 1);
	}

	@Then ("balance for $address is $amount")
	public void thenBalanceIs (String address, long amount)
	{
		List<String> addresses = new ArrayList<String> ();
		addresses.add (address);
		long sum = 0;
		AccountStatement as = directAPI.registerAccountListener (addresses, 0, null);
		for ( TransactionOutput o : as.getOpening () )
		{
			sum += o.getValue ();
		}
		assertTrue (sum == amount);
	}

}
