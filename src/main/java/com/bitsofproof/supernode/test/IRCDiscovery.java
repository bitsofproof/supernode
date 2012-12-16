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
package com.bitsofproof.supernode.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.Discovery;

public class IRCDiscovery implements Discovery
{
	private static final Logger log = LoggerFactory.getLogger (IRCDiscovery.class);
	private String server;
	private int port;
	private String channel;

	@Override
	public List<InetAddress> discover ()
	{
		List<InetAddress> al = new ArrayList<InetAddress> ();

		try
		{
			log.trace ("Connect to IRC server " + server);
			Socket socket = new Socket (server, port);
			PrintWriter writer = new PrintWriter (new OutputStreamWriter (socket.getOutputStream (), "UTF-8"));
			BufferedReader reader = new BufferedReader (new InputStreamReader (socket.getInputStream (), "UTF-8"));

			String[] answers = new String[] { "Found your hostname", "using your IP address instead", "Couldn't look up your hostname", "ignoring hostname" };
			String line;
			boolean stop = false;
			while ( !stop && (line = reader.readLine ()) != null )
			{
				log.trace ("IRC receive " + line);
				for ( int i = 0; i < answers.length; ++i )
				{
					if ( line.contains (answers[i]) )
					{
						stop = true;
						break;
					}
				}
			}

			String nick = "bop" + new SecureRandom ().nextInt (Integer.MAX_VALUE);
			writer.println ("NICK " + nick);
			writer.println ("USER " + nick + " 8 * : " + nick);
			writer.flush ();
			log.trace ("IRC send: I am " + nick);

			while ( (line = reader.readLine ()) != null )
			{
				log.trace ("IRC receive " + line);
				if ( hasCode (line, new String[] { " 004 ", " 433 " }) )
				{
					break;
				}
			}
			log.trace ("IRC send: joining " + channel);
			writer.println ("JOIN " + channel);
			writer.println ("NAMES");
			writer.flush ();
			while ( (line = reader.readLine ()) != null )
			{
				log.trace ("IRC receive " + line);
				if ( hasCode (line, new String[] { " 353 " }) )
				{
					StringTokenizer tokenizer = new StringTokenizer (line, ":");
					String t = tokenizer.nextToken ();
					if ( tokenizer.hasMoreElements () )
					{
						t = tokenizer.nextToken ();
					}
					tokenizer = new StringTokenizer (t);
					tokenizer.nextToken ();
					while ( tokenizer.hasMoreTokens () )
					{
						String w = tokenizer.nextToken ().substring (1);
						if ( !tokenizer.hasMoreElements () )
						{
							continue;
						}
						try
						{
							byte[] m = AddressConverter.fromBase58WithChecksum (w);
							byte[] addr = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 0, 0, 0, 0 };
							System.arraycopy (m, 0, addr, 12, 4);
							al.add (InetAddress.getByAddress (addr));
						}
						catch ( ValidationException e )
						{
							log.trace (e.toString ());
						}
					}
				}
				if ( hasCode (line, new String[] { " 366 " }) )
				{
					break;
				}
			}
			writer.println ("PART " + channel);
			writer.println ("QUIT");
			writer.flush ();
			socket.close ();
		}
		catch ( UnknownHostException e )
		{
			log.error ("Can not find IRC server " + server, e);
		}
		catch ( IOException e )
		{
			log.error ("Can not use IRC server " + server, e);
		}

		return al;
	}

	private boolean hasCode (String line, String[] patterns)
	{
		for ( int i = 0; i < patterns.length; ++i )
		{
			if ( line.contains (patterns[i]) )
			{
				return true;
			}
		}
		return false;
	}

	public void setServer (String server)
	{
		this.server = server;
	}

	public void setPort (int port)
	{
		this.port = port;
	}

	public void setChannel (String channel)
	{
		this.channel = channel;
	}

}
