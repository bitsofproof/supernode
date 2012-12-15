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

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

public class Main
{
	protected interface App
	{
		public void start (String[] args) throws Exception;
	}

	private static final Logger log = LoggerFactory.getLogger (Main.class);

	public static void main (String[] args) throws Exception
	{
		log.info ("bitsofproof supernode (c) 2012 Tamas Blummer tamas@bitsofproof.com");
		log.trace ("Spring context setup");

		if ( args.length == 0 )
		{
			System.err.println ("Usage: java com.bitsofproof.main.Main profile [profile...] -- [args...] [options...]");
			return;
		}

		GenericXmlApplicationContext ctx = new GenericXmlApplicationContext ();
		try
		{
			List<String> a = new ArrayList<String> ();
			boolean profiles = true;
			for ( String s : args )
			{
				if ( s.equals ("--") )
				{
					profiles = false;
				}
				if ( profiles )
				{
					log.info ("Loading profile: " + s);
					ctx.getEnvironment ().addActiveProfile (s);
				}
				else
				{
					a.add (s);
				}
			}
			ctx.load ("classpath:common.xml");
			ctx.load ("classpath:*-profile.xml");
			ctx.refresh ();
			ctx.getBean (App.class).start (a.toArray (new String[0]));
		}
		catch ( CmdLineException cle )
		{
			cle.getParser ().printUsage (System.err);
		}
	}
}
