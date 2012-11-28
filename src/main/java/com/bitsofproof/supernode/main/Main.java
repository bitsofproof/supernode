/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 * Contributor: 2012 Tamas Bartfai bartfaitamas@gmail.com
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
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;

import com.google.common.base.Enums;

public class Main
{
	protected interface App
	{
		public void start (String[] args) throws Exception;
	}

	private static final Logger log = LoggerFactory.getLogger (Main.class);

	public static void main (String[] args) throws Exception
	{
		Profile profile = null;
		if ( args.length > 0 && args[0] != "help" )
		{
			profile = Enums.valueOfFunction (Profile.class).apply (args[0].toUpperCase ());
		}

		if ( profile == null )
		{
			printUsage ();
			return;
		}

		try
		{
			List<String> a = new ArrayList<String> ();
			boolean first = true;
			for ( String s : args )
			{
				if ( !first )
				{
					a.add (s);
				}
				first = false;
			}
			loadContext (profile).getBean (App.class).start (a.toArray (new String[0]));
		}
		catch ( CmdLineException cle )
		{
			cle.getParser ().printUsage (System.err);
		}
	}

	protected static void printUsage ()
	{
		System.err.println ("Usage: java com.bitsofproof.Supernode < demo | server | audit > [COMMAND ARGS]");
	}

	protected static GenericXmlApplicationContext loadContext (Profile profile) throws IOException
	{
		log.info ("bitsofproof supernode (c) 2012 Tamas Blummer tamas@bitsofproof.com");
		log.trace ("Spring context setup");
		log.info ("Profile: " + profile.toString ());

		GenericXmlApplicationContext ctx = new GenericXmlApplicationContext ();
		ctx.getEnvironment ().getPropertySources ().addFirst (loadProperties (profile));
		ctx.getEnvironment ().setActiveProfiles (profile.toString ());
		ctx.load ("classpath:context/common-context.xml");
		ctx.load ("classpath:context/*-config.xml");
		ctx.refresh ();

		return ctx;
	}

	protected static ResourcePropertySource loadProperties (Profile profile) throws IOException
	{
		String propertiesLocation = String.format ("classpath:etc/supernode-%s.properties", profile.toString ());
		return new ResourcePropertySource (propertiesLocation);
	}

	protected static enum Profile
	{
		DEMO, AUDIT, SERVER, ARCHIVE;

		@Override
		public String toString ()
		{
			return super.toString ().toLowerCase ();
		}
	}
}
