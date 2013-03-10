package com.bitsofproof.client.demo;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;

public class Explorer
{
	private static final Logger log = LoggerFactory.getLogger (Explorer.class);

	private BCSAPI api;

	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	public static void main (String[] args) throws Exception
	{
		log.info ("bitsofproof supernode demo (c) 2013 Tamas Blummer tamas@bitsofproof.com");
		log.trace ("Spring context setup");

		if ( args.length == 0 )
		{
			System.err.println ("Usage: java com.bitsofproof.client.demo.Explorer profile [profile...] -- [args...] [options...]");
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
		ctx.load ("classpath:context/explorer.xml");
		ctx.load ("classpath:context/*-profile.xml");
		ctx.refresh ();
		ctx.getBean (Explorer.class).start (a.toArray (new String[0]));
	}

	public void start (String[] args)
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you yet");
		gnuOptions.addOption ("b", "block", true, "Get raw block");

		CommandLine cl = null;
		String block = null;
		try
		{
			cl = parser.parse (gnuOptions, args);
			if ( cl.hasOption ('b') )
			{
				block = cl.getOptionValue ('b');
			}
		}
		catch ( ParseException e )
		{
			log.error ("Invalid options ", e);
			return;
		}
		if ( block != null )
		{
			try
			{
				Block b = api.getBlock (block);
				System.out.println (b.toWireDump ());
			}
			catch ( BCSAPIException e )
			{
				log.error ("Can not retrieve block " + block, e);
			}
		}
	}
}
