package com.bitsofproof.supernode.test;

import java.io.IOException;

import org.jbehave.core.annotations.BeforeScenario;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;

@Component
public class BasicCommunication
{
	private static final Logger log = LoggerFactory.getLogger (BasicCommunication.class);

	@Autowired
	@Qualifier ("bitcoinNetwork")
	BitcoinNetwork network;

	@Autowired
	Chain chain;

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	@BeforeScenario
	public void init ()
	{
		try
		{
			network.getStore ().resetStore (chain);
			network.getStore ().cache (0);
			network.start ();
		}
		catch ( ValidationException e )
		{
			log.error ("can not set up story", e);
		}
		catch ( IOException e )
		{
			log.error ("can not set up story", e);
		}
	}

	@When ("version message arrives")
	public void whenVersionMessage ()
	{
	}

	@Then ("connected")
	public void thenConnected ()
	{

	}
}
