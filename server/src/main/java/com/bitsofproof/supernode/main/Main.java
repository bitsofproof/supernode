/*
 * Copyright 2013 bits of proof zrt.
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

import java.io.FileNotFoundException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.util.Log4jConfigurer;

public class Main
{
	protected interface App
	{
		public void start (String[] args) throws Exception;
	}

	static
	{
		try
		{
			Log4jConfigurer.initLogging ("config/log4j.properties");
		}
		catch ( FileNotFoundException e )
		{
			System.err.println ("Can not find config/log4.properties");
		}
	}

	private static final Logger log = LoggerFactory.getLogger (Main.class);

	public static void main (String[] args) throws Exception
	{
		log.info ("bitsofproof supernode (c) 2013-2014 bits of proof zrt.");
		try
		{
			Security.addProvider (new BouncyCastleProvider ());

			if ( args.length == 0 )
			{
				System.err.println ("Usage: java -Xmx4g -jar this.jar profile [profile...] -- [args...] [options...]");
				System.err.println ("       where profile A is any of config/A-profile.xml");
				return;
			}

			GenericXmlApplicationContext ctx = new GenericXmlApplicationContext ();
			List<String> a = new ArrayList<String> ();
			boolean profiles = true;
			for ( String s : args )
			{
				if ( s.equals ("--") )
				{
					profiles = false;
				}
				else
				{
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
			}
			ctx.load ("file:config/server.xml");
			ctx.load ("file:config/*-profile.xml");
			ctx.refresh ();
			ctx.getBean (App.class).start (a.toArray (new String[0]));
			ctx.close ();
		}
		catch ( Exception e )
		{
			log.error ("Bootstrap error", e);
		}
	}
}
