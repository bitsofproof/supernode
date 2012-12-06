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

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.TransactionValidationException;
import com.bitsofproof.supernode.core.ValidationException;

public class Demo extends Main implements Main.App
{
	private static final Logger log = LoggerFactory.getLogger (Demo.class);

	private BitcoinNetwork network;

	public static void main (String[] args) throws Exception
	{
		try
		{
			loadContext (Profile.DEMO).getBean (App.class).start (args);
		}
		catch ( CmdLineException cle )
		{
			cle.getParser ().printUsage (System.err);
		}
	}

	@Override
	public void start (String[] args) throws IOException, TransactionValidationException
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you yet");

		try
		{
			parser.parse (gnuOptions, args);
		}
		catch ( ParseException e1 )
		{
			log.error ("Invalid options ", e1);
			return;
		}

		if ( network.getStore ().isEmpty () )
		{
			network.getStore ().resetStore (network.getChain ());
		}
		try
		{
			network.getStore ().cache (false);
			network.start ();
		}
		catch ( ValidationException e )
		{
			log.error ("Cache read failed", e);
		}
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}
}
