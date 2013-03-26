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
package com.bitsofproof.client.demo;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Posting;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;

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
		gnuOptions.addOption ("t", "transaction", true, "Get raw transaction");
		gnuOptions.addOption ("a", "address", true, "Get addres traffic for last 30 days");

		CommandLine cl = null;
		String block = null;
		String transaction = null;
		String address = null;
		try
		{
			cl = parser.parse (gnuOptions, args);
			if ( cl.hasOption ('b') )
			{
				block = cl.getOptionValue ('b');
			}
			if ( cl.hasOption ('t') )
			{
				transaction = cl.getOptionValue ('t');
			}
			if ( cl.hasOption ('a') )
			{
				address = cl.getOptionValue ('a');
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
				if ( b != null )
				{
					System.out.println (b.toWireDump ());
				}
			}
			catch ( BCSAPIException e )
			{
				log.error ("Can not retrieve block " + block, e);
			}
		}
		if ( transaction != null )
		{
			try
			{
				Transaction t = api.getTransaction (transaction);
				if ( t != null )
				{
					System.out.println (t.toWireDump ());
				}
			}
			catch ( BCSAPIException e )
			{
				log.error ("Can not retrieve block " + block, e);
			}
		}
		if ( address != null )
		{
			try
			{
				SimpleDateFormat dateFormat = new SimpleDateFormat ();
				DecimalFormat decimalFormat = new DecimalFormat ("0.00000000 BTC");
				List<String> addresses = new ArrayList<String> ();
				addresses.add (address);
				AccountStatement s = api.getAccountStatement (addresses, System.currentTimeMillis () / 1000 - 30 * 24 * 60 * 60);
				Map<String, HashMap<Long, TransactionOutput>> spendable = new HashMap<String, HashMap<Long, TransactionOutput>> ();
				if ( s != null )
				{
					System.out.println ("Activity of " + address + " as of " + dateFormat.format (new Date (s.getTimestamp () * 1000)));
					System.out.println ("Last block evaluated " + s.getLastBlock ());
					if ( s.getOpening () != null )
					{
						long sum = 0;
						for ( TransactionOutput o : s.getOpening () )
						{
							sum += o.getValue ();
							HashMap<Long, TransactionOutput> outs = spendable.get (o.getTransactionHash ());
							if ( outs == null )
							{
								outs = new HashMap<Long, TransactionOutput> ();
								spendable.put (o.getTransactionHash (), outs);
							}
							outs.put (o.getSelfIx (), o);
						}
						System.out.println ("Opening balance: " + decimalFormat.format (sum / 100000000.0));
					}
					if ( s.getPosting () != null )
					{
						for ( Posting p : s.getPosting () )
						{
							String amount;
							if ( p.getSpent () == null )
							{
								amount = decimalFormat.format (p.getOutput ().getValue () / 100000000.0);
								HashMap<Long, TransactionOutput> outs = spendable.get (p.getOutput ().getTransactionHash ());
								if ( outs == null )
								{
									outs = new HashMap<Long, TransactionOutput> ();
									spendable.put (p.getOutput ().getTransactionHash (), outs);
								}
								outs.put (p.getOutput ().getSelfIx (), p.getOutput ());
							}
							else
							{
								amount = decimalFormat.format (p.getOutput ().getValue () / -100000000.0);
								HashMap<Long, TransactionOutput> outs = spendable.get (p.getOutput ().getTransactionHash ());
								if ( outs != null )
								{
									outs.remove (p.getOutput ().getSelfIx ());
									if ( outs.size () == 0 )
									{
										spendable.remove (p.getOutput ().getTransactionHash ());
									}
								}
							}
							System.out.println (dateFormat.format (new Date (p.getTimestamp () * 1000)) + " " + amount);
						}
					}
					if ( s.getUnconfirmedReceive () != null )
					{
						for ( Transaction t : s.getUnconfirmedReceive () )
						{
							for ( TransactionOutput output : t.getOutputs () )
							{
								if ( output.getAddresses ().contains (address) )
								{
									HashMap<Long, TransactionOutput> outs = spendable.get (output.getTransactionHash ());
									if ( outs == null )
									{
										outs = new HashMap<Long, TransactionOutput> ();
										spendable.put (output.getTransactionHash (), outs);
									}
									outs.put (output.getSelfIx (), output);
									System.out.println ("Unconfirmed receive " + decimalFormat.format (output.getValue () / 100000000.0));
								}
							}
						}
					}
					if ( s.getUnconfirmedSpend () != null )
					{
						for ( Transaction t : s.getUnconfirmedReceive () )
						{
							for ( TransactionInput input : t.getInputs () )
							{
								if ( spendable.containsKey (input.getSourceHash ()) )
								{
									TransactionOutput output = spendable.get (input.getSourceHash ()).get (input.getIx ());

									System.out.println ("Unconfirmed spend " + decimalFormat.format (output.getValue () / -100000000.0));
								}
							}
						}
					}
				}
			}
			catch ( BCSAPIException e )
			{
				log.error ("Can not retrieve block " + block, e);
			}
		}
		System.exit (0);
	}
}
