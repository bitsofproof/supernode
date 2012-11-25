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

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.model.TransactionValidationException;

public class Snapshot extends Main implements Main.App
{
	private static final Logger log = LoggerFactory.getLogger (Snapshot.class);

	@Autowired
	private Chain chain;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	static class Settings
	{
		@Option (name = "-h", aliases = "--help", usage = "I can't help you yet")
		boolean help;
		@Option (name = "-b", aliases = "--block", usage = "Snapshot at block height")
		Integer height;

		public int getHeight ()
		{
			if ( height != null )
			{
				return height;
			}

			return Integer.MAX_VALUE;
		}

		static Settings parse (String[] args) throws CmdLineException
		{
			Settings s = new Settings ();
			CmdLineParser parser = new CmdLineParser (s);
			parser.parseArgument (args);

			if ( s.help )
			{
				throw new CmdLineException (parser, "Help");
			}

			return s;
		}
	}

	public static void main (String[] args) throws Exception
	{
		try
		{
			loadContext (Profile.SNAPSHOT).getBean (App.class).start (args);
		}
		catch ( CmdLineException cle )
		{
			cle.getParser ().printUsage (System.err);
		}
	}

	@Override
	public void start (String[] args) throws IOException, TransactionValidationException, NumberFormatException, CmdLineException
	{
		Settings settings = Settings.parse (args);
		snapshot (settings.getHeight ());
	}

	public void snapshot (final int height)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{

				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
			}
		});
	}
}
