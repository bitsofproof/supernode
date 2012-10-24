package com.bitsofproof.supernode.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.messages.AddrMessage;
import com.bitsofproof.supernode.messages.AlertMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.VersionMessage;

public class BitcoinPeer extends P2P.Peer
{
	private static final Logger log = LoggerFactory.getLogger (BitcoinPeer.class);

	private final TransactionTemplate transactionTemplate;
	private BitcoinNetwork network;

	private String agent;
	private long height;
	private long peerVersion;
	private long peerServices;

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);
	private static final long CONNECTIONTIMEOUT = 30;

	public class Message implements P2P.Message
	{
		private final String command;
		private long version;

		public Message (String command)
		{
			this.command = command;
			version = peerVersion;
		}

		public long getVersion ()
		{
			return version;
		}

		public void setVersion (long version)
		{
			this.version = version;
		}

		public String getCommand ()
		{
			return command;
		}

		@Override
		public byte[] toByteArray () throws NoSuchAlgorithmException
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream ();
			WireFormat.Writer writer = new WireFormat.Writer (out);
			writer.writeUint32 (network.getChain ().getMagic ());
			writer.writeZeroDelimitedString (getCommand (), 12);
			WireFormat.Writer payload = new WireFormat.Writer ();
			toWire (payload);
			byte[] data = payload.toByteArray ();
			writer.writeUint32 (data.length);

			byte[] checksum = new byte[4];
			MessageDigest sha;
			sha = MessageDigest.getInstance ("SHA-256");
			System.arraycopy (sha.digest (sha.digest (data)), 0, checksum, 0, 4);
			writer.writeBytes (checksum);

			writer.writeBytes (data);
			return writer.toByteArray ();
		}

		@Override
		public String dump ()
		{
			try
			{
				return new String (Hex.encode (toByteArray ()), "UTF-8");
			}
			catch ( UnsupportedEncodingException e )
			{
			}
			catch ( NoSuchAlgorithmException e )
			{
			}
			return null;
		}

		public void validate () throws ValidationException
		{
		}

		public void toWire (WireFormat.Writer writer)
		{
		};

		public void fromWire (WireFormat.Reader reader)
		{
		};
	}

	public Message createMessage (String command)
	{
		if ( command.equals ("version") )
		{
			return new VersionMessage (this);
		}
		else if ( command.equals ("inv") )
		{
			return new InvMessage (this);
		}
		else if ( command.equals ("addr") )
		{
			return new AddrMessage (this);
		}
		else if ( command.equals ("getdata") )
		{
			return new GetDataMessage (this);
		}
		else if ( command.equals ("getblocks") )
		{
			return new GetBlocksMessage (this);
		}
		else if ( command.equals ("block") )
		{
			return new BlockMessage (this);
		}
		else if ( command.equals ("alert") )
		{
			return new AlertMessage (this, this.getNetwork ().getChain ().getAlertKey ());
		}

		return new Message (command);
	}

	public Map<String, ArrayList<BitcoinMessageListener>> listener = Collections.synchronizedMap (new HashMap<String, ArrayList<BitcoinMessageListener>> ());

	public BitcoinPeer (P2P p2p, TransactionTemplate transactionTemplate, InetSocketAddress address)
	{
		p2p.super (address);
		network = (BitcoinNetwork) p2p;
		this.transactionTemplate = transactionTemplate;

		// this will be overwritten by the first version message we get
		peerVersion = network.getChain ().getVersion ();

		addListener ("version", new BitcoinMessageListener ()
		{
			@Override
			public void process (BitcoinPeer.Message m, BitcoinPeer peer) throws Exception
			{
				VersionMessage v = (VersionMessage) m;
				agent = v.getAgent ();
				height = v.getHeight ();
				peerVersion = v.getVersion ();
				peerServices = v.getServices ();
				peer.send (peer.createMessage ("verack"));
			}
		});

		addListener ("verack", new BitcoinMessageListener ()
		{
			@Override
			public void process (BitcoinPeer.Message m, BitcoinPeer peer)
			{
				log.info ("Connection to '" + getAgent () + "' at " + getAddress () + " Open connections: " + getNetwork ().getNumberOfConnections ());
				network.addPeer (peer);
				network.notifyPeerAdded (peer);
			}
		});
	}

	public BitcoinNetwork getNetwork ()
	{
		return network;
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	public long getVersion ()
	{
		return peerVersion;
	}

	public long getServices ()
	{
		return peerServices;
	}

	public long getHeight ()
	{
		return height;
	}

	public String getAgent ()
	{
		return agent;
	}

	@Override
	public void onDisconnect ()
	{
		network.notifyPeerRemoved (this);
		log.info ("Disconnected '" + getAgent () + "' at " + getAddress () + ". Open connections: " + getNetwork ().getNumberOfConnections ());
	}

	public static final int MAX_BLOCK_SIZE = 1000000;

	@Override
	public Message parse (InputStream readIn) throws IOException
	{
		try
		{
			byte[] head = new byte[24];
			if ( readIn.read (head) != head.length )
			{
				throw new ValidationException ("Read timeout for " + getAddress ());
			}
			WireFormat.Reader reader = new WireFormat.Reader (head);
			long mag = reader.readUint32 ();
			if ( mag != network.getChain ().getMagic () )
			{
				throw new ValidationException ("Wrong magic for this chain " + getAddress ());
			}

			String command = reader.readZeroDelimitedString (12);
			Message m = createMessage (command);
			long length = reader.readUint32 ();
			byte[] checksum = reader.readBytes (4);
			if ( length < 0 || length >= MAX_BLOCK_SIZE )
			{
				throw new ValidationException ("Block size limit exceeded " + getAddress ());
			}
			else
			{
				byte[] buf = new byte[(int) length];
				if ( readIn.read (buf) != buf.length )
				{
					throw new ValidationException ("Package length mismatch " + getAddress ());
				}
				byte[] cs = new byte[4];
				MessageDigest sha;
				try
				{
					sha = MessageDigest.getInstance ("SHA-256");
					System.arraycopy (sha.digest (sha.digest (buf)), 0, cs, 0, 4);
				}
				catch ( NoSuchAlgorithmException e )
				{
					throw new ValidationException ("SHA-256 implementation missing " + getAddress ());
				}
				if ( !Arrays.equals (cs, checksum) )
				{
					throw new ValidationException ("Checksum mismatch " + getAddress ());
				}

				if ( m != null )
				{
					m.fromWire (new WireFormat.Reader (buf));
					if ( m instanceof AlertMessage )
					{
						m.validate ();
						log.warn (((AlertMessage) m).getPayload ());
					}
				}
			}
			return m;
		}
		catch ( ValidationException e )
		{
			throw new IOException (e);
		}
	}

	@Override
	public void onConnect ()
	{
		try
		{
			VersionMessage m = (VersionMessage) createMessage ("version");
			m.setHeight (network.getChainHeight ());
			m.setPeer (getAddress ().getAddress ());
			m.setRemotePort (getAddress ().getPort ());
			send (m);
			final BitcoinPeer peer = this;
			scheduler.schedule (new Runnable ()
			{
				@Override
				public void run ()
				{
					if ( !network.isConnected (peer) )
					{
						peer.disconnect ();
					}
				}
			}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
		}
		catch ( Exception e )
		{
			log.error ("Can not connect peer " + getAddress (), e);
		}
	}

	@Override
	public void receive (P2P.Message m)
	{
		final BitcoinPeer self = this;
		final Message bm = (Message) m;
		transactionTemplate.execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus arg0)
			{
				try
				{
					bm.validate ();

					List<BitcoinMessageListener> classListener = listener.get (bm.getCommand ());
					if ( classListener != null )
					{
						for ( BitcoinMessageListener l : classListener )
						{
							l.process (bm, self);
						}
					}

				}
				catch ( Exception e )
				{
					arg0.setRollbackOnly ();
					log.error ("Failed to process " + bm.getCommand (), e);
					disconnect ();
				}
			}
		});
	}

	public void addListener (String type, BitcoinMessageListener l)
	{
		ArrayList<BitcoinMessageListener> ll = listener.get (type);
		if ( ll == null )
		{
			ll = new ArrayList<BitcoinMessageListener> ();
			listener.put (type, ll);
		}
		if ( !ll.contains (l) )
		{
			ll.add (l);
		}
	}

	public void removeListener (String type, BitcoinMessageListener l)
	{
		ArrayList<BitcoinMessageListener> ll = listener.get (type);
		if ( ll != null )
		{
			ll.remove (l);
		}
	}
}
