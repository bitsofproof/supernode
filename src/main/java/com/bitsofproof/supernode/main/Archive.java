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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.model.BlockStore;
import com.bitsofproof.supernode.model.TransactionValidationException;

public class Archive extends Main implements Main.App
{
	private static final Logger log = LoggerFactory.getLogger (Archive.class);

	@Autowired
	private Chain chain;

	@Autowired
	BlockStore store;

	static class Settings
	{
		@Option (name = "-h", aliases = "--help", usage = "I can't help you yet")
		boolean help;
		@Option (name = "-u", aliases = "--upto", usage = "Run checks upto n")
		Integer upto;

		public int getEnd ()
		{
			return upto == null ? Integer.MAX_VALUE : upto;
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
			loadContext (Profile.ARCHIVE).getBean (App.class).start (args);
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
		archive (settings.getEnd ());
	}

	public void archive (final int to)
	{
		try
		{
			store.cache ();
			store.archive ();
		}
		catch ( ValidationException e )
		{
			log.error ("Exception", e);
		}
	}
}
