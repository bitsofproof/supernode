package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

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
import org.springframework.stereotype.Component;

import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;

@Component
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
	Chain chain;

	@Override
	protected List<String> storyPaths ()
	{
		return new StoryFinder ().findPaths (CodeLocations.codeLocationFromPath ("src/test/resources"), "*.story", "");
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	@Given ("a new node")
	public void storeCleared () throws Exception
	{
		try
		{
			network.getStore ().resetStore (chain);
			network.getStore ().cache (0);
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
}
