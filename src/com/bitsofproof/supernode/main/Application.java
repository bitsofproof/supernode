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
package com.bitsofproof.supernode.main;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.model.BlockStore;

public class Application
{
	private static final Logger log = LoggerFactory.getLogger (Application.class);

	private Chain chain;

	private BlockStore store;

	private PlatformTransactionManager transactionManager;

	private BitcoinNetwork network;

	public void start (ApplicationContext context, String[] args) throws IOException
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you").addOption ("r", "resetdb", false, "initialize database");

		final CommandLine cl;
		try
		{
			cl = parser.parse (gnuOptions, args);
		}
		catch ( ParseException e1 )
		{
			log.error ("Invalid options ", e1);
			return;
		}

		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{

			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				if ( cl.hasOption ("resetdb") )
				{
					store.resetStore (chain);
				}
				store.cache ();
			}
		});

		network.start ();
	}

	public Chain getChain ()
	{
		return chain;
	}

	public void setChain (Chain chain)
	{
		this.chain = chain;
	}

	public BlockStore getStore ()
	{
		return store;
	}

	public void setStore (BlockStore store)
	{
		this.store = store;
	}

	public BitcoinNetwork getNetwork ()
	{
		return network;
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	public PlatformTransactionManager getTransactionManager ()
	{
		return transactionManager;
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

}
