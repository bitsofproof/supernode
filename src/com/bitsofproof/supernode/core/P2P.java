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
package com.bitsofproof.supernode.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class P2P
{
	private static final Logger log = LoggerFactory.getLogger (P2P.class);

	private final static int BUFFSIZE = 8 * 1024;

	public interface Message
	{
		public byte[] toByteArray ();
	}

	protected abstract class Peer
	{
		private final InetSocketAddress address;
		private SocketChannel channel;

		private final LinkedBlockingQueue<byte[]> writes = new LinkedBlockingQueue<byte[]> ();
		private final LinkedBlockingQueue<byte[]> reads = new LinkedBlockingQueue<byte[]> ();
		private final Semaphore notListened = new Semaphore (1);
		private ByteBuffer pushBackBuffer = null;
		private final byte[] closedMark = new byte[0];

		private final InputStream readIn = new InputStream ()
		{
			private ByteArrayInputStream currentRead = null;

			@Override
			public synchronized int available () throws IOException
			{
				int a = 0;
				if ( currentRead != null )
				{
					a = currentRead.available ();
					if ( a > 0 )
					{
						return a;
					}
				}
				byte[] next = reads.peek ();
				if ( next != null && next != closedMark )
				{
					return next.length;
				}
				return 0;
			}

			@Override
			public synchronized int read (byte[] b, int off, int len) throws IOException
			{
				int need = len;
				if ( need <= 0 )
				{
					return need;
				}
				do
				{
					if ( currentRead != null )
					{
						int r = currentRead.read (b, off, need);
						if ( r > 0 )
						{
							off += r;
							need -= r;
						}
					}
					if ( need == 0 )
					{
						return len;
					}
					byte[] buf = null;
					try
					{
						buf = reads.poll (READTIMEOUT, TimeUnit.SECONDS);
						if ( buf == null || buf == closedMark )
						{
							return -1;
						}
					}
					catch ( InterruptedException e )
					{
						throw new IOException (e);
					}
					currentRead = new ByteArrayInputStream (buf);
				} while ( need > 0 );
				return len;
			}

			@Override
			public int read (byte[] b) throws IOException
			{
				return read (b, 0, b.length);
			}

			@Override
			public int read () throws IOException
			{
				byte[] b = new byte[1];
				return read (b, 0, 1);
			}
		};

		protected Peer (InetSocketAddress address)
		{
			this.address = address;
		}

		private void connect ()
		{
			try
			{
				channel = SocketChannel.open ();
				connectedPeers.put (channel, this);
				channel.configureBlocking (false);
				channel.connect (address);
				selectorChanges.add (new ChangeRequest (channel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
				selector.wakeup ();
				log.trace ("Trying to connect " + address);
			}
			catch ( IOException e )
			{
			}
		}

		@Override
		public boolean equals (Object obj)
		{
			return address.equals (((Peer) obj).address);
		}

		@Override
		public int hashCode ()
		{
			return address.hashCode ();
		}

		private void process (ByteBuffer buffer, int len)
		{
			if ( len > 0 )
			{
				byte[] b = new byte[len];
				System.arraycopy (buffer.array (), 0, b, 0, len);
				reads.add (b);
				if ( notListened.tryAcquire () )
				{
					listen ();
				}
			}
		}

		private ByteBuffer getBuffer ()
		{
			if ( pushBackBuffer != null )
			{
				return pushBackBuffer;
			}

			byte[] next;

			if ( (next = writes.poll ()) != null )
			{
				return ByteBuffer.wrap (next);
			}
			return null;
		}

		private void pushBack (ByteBuffer b)
		{
			pushBackBuffer = b;
		}

		public InetSocketAddress getAddress ()
		{
			return address;
		}

		public void disconnect ()
		{
			try
			{
				// note that no other reference to peer is stored here
				// it might be garbage collected
				// somebody else however might have retained a reference, so reduce size.
				writes.clear ();
				reads.clear ();
				reads.add (closedMark);
				connectedPeers.remove (channel);
				if ( channel.isConnected () )
				{
					connectSlot.release ();
					channel.close ();
					peerThreads.execute (new Runnable ()
					{
						@Override
						public void run ()
						{
							onDisconnect ();
						}
					});
				}
				selectorChanges.add (new ChangeRequest (channel, ChangeRequest.CANCEL, SelectionKey.OP_ACCEPT));
				selector.wakeup ();
			}
			catch ( IOException e )
			{
			}
		}

		private void listen ()
		{
			peerThreads.execute (new Runnable ()
			{
				@Override
				public void run ()
				{
					Message m = null;
					try
					{
						m = parse (readIn);
						receive (m);
						if ( readIn.available () > 0 )
						{
							peerThreads.execute (this);
						}
						else
						{
							notListened.release ();
						}
					}
					catch ( Exception e )
					{
						log.debug ("Exception in message processing", e);
						disconnect ();
					}
				}
			});
		}

		public void send (Message m)
		{
			writes.add (m.toByteArray ());
			selectorChanges.add (new ChangeRequest (channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE | SelectionKey.OP_READ));
			selector.wakeup ();
		}

		protected abstract Message parse (InputStream readIn) throws IOException;

		protected abstract void receive (Message m);

		protected abstract void onConnect ();

		protected abstract void onDisconnect ();

	}

	protected abstract Peer createPeer (InetSocketAddress address, boolean active);

	protected abstract boolean discover ();

	protected void addPeer (InetAddress addr, int port)
	{
		InetSocketAddress address = new InetSocketAddress (addr, port);
		if ( !runqueue.contains (address) )
		{
			runqueue.add (address);
			log.trace ("added new peer to runqueue " + address);
		}
	}

	public int getNumberOfConnections ()
	{
		return connectedPeers.size ();
	}

	// peers connected
	private final Map<SocketChannel, Peer> connectedPeers = Collections.synchronizedMap (new HashMap<SocketChannel, Peer> ());

	// peers waiting to be connected
	private final LinkedBlockingQueue<InetSocketAddress> runqueue = new LinkedBlockingQueue<InetSocketAddress> ();

	// number of connections we try to maintain
	private final int desiredConnections;

	// we want fast answering nodes
	private static final int CONNECTIONTIMEOUT = 5;

	// number of seconds to wait until giving up on connections
	private static final int READTIMEOUT = 60; // seconds

	// keep track with number of connections we asked for here
	private final Semaphore connectSlot;

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);

	private int port;

	public void setPort (int port)
	{
		this.port = port;
	}

	public int getPort ()
	{
		return port;
	}

	protected ScheduledExecutorService getScheduler ()
	{
		return scheduler;
	}

	private static class ChangeRequest
	{
		public static final int REGISTER = 1;
		public static final int CHANGEOPS = 2;
		public static final int STOP = 3;
		public static final int CANCEL = 4;

		public SelectableChannel socket;
		public int type;
		public int ops;

		public ChangeRequest (SelectableChannel socket, int type, int ops)
		{
			this.socket = socket;
			this.type = type;
			this.ops = ops;
		}
	}

	private final ConcurrentLinkedQueue<ChangeRequest> selectorChanges = new ConcurrentLinkedQueue<ChangeRequest> ();

	private final Selector selector;

	private final ThreadPoolExecutor peerThreads;

	private final Thread selectorThread;

	private final Thread connector;

	public P2P (int connections) throws IOException
	{
		desiredConnections = connections;
		connectSlot = new Semaphore (desiredConnections);
		// create a pool of threads
		peerThreads =
				(ThreadPoolExecutor) Executors.newFixedThreadPool (Math.min (desiredConnections, Runtime.getRuntime ().availableProcessors () * 2),
						new ThreadFactory ()
						{
							@Override
							public Thread newThread (final Runnable r)
							{
								Thread peerThread = new Thread ()
								{
									@Override
									public void run ()
									{
										r.run ();
									}
								};
								peerThread.setDaemon (false);
								peerThread.setName ("Peer");
								return peerThread;
							}
						});

		selector = Selector.open ();
		// this thread waits on the selector above and acts on events
		selectorThread = new Thread (new Runnable ()
		{
			@Override
			public void run ()
			{
				try
				{
					ByteBuffer readBuffer = ByteBuffer.allocate (BUFFSIZE);
					while ( true )
					{
						ChangeRequest cr;
						while ( (cr = selectorChanges.poll ()) != null )
						{
							if ( cr.type == ChangeRequest.STOP )
							{
								return;
							}
							if ( cr.socket == null )
							{
								continue;
							}
							if ( cr.type == ChangeRequest.REGISTER )
							{
								try
								{
									cr.socket.register (selector, cr.ops);
								}
								catch ( ClosedChannelException e )
								{
									continue;
								}
							}
							else if ( cr.type == ChangeRequest.CHANGEOPS )
							{
								SelectionKey key = cr.socket.keyFor (selector);
								if ( key != null )
								{
									key.interestOps (cr.ops);
								}
							}
							else if ( cr.type == ChangeRequest.CANCEL )
							{
								SelectionKey key = cr.socket.keyFor (selector);
								if ( key != null )
								{
									key.cancel ();
								}
							}
						}
						selector.select ();
						Iterator<SelectionKey> keys = selector.selectedKeys ().iterator ();
						while ( keys.hasNext () )
						{
							SelectionKey key = keys.next ();
							keys.remove ();
							if ( !key.isValid () )
							{
								continue;
							}
							if ( key.isAcceptable () )
							{
								SocketChannel client;
								try
								{
									client = ((ServerSocketChannel) key.channel ()).accept ();
									client.configureBlocking (false);
									InetSocketAddress address = (InetSocketAddress) client.socket ().getRemoteSocketAddress ();
									final Peer peer;
									peer = createPeer (address, false);
									peer.channel = client;
									client.register (selector, SelectionKey.OP_READ);
									connectedPeers.put (client, peer);
								}
								catch ( IOException e )
								{
									log.trace ("unsuccessful unsolicited connection ", e);
								}
							}
							else
							{
								final Peer peer = connectedPeers.get (key.channel ());
								if ( peer == null )
								{
									key.cancel ();
									continue;
								}
								if ( key.isConnectable () )
								{
									// we asked for connection here
									try
									{
										SocketChannel client = (SocketChannel) key.channel ();
										client.finishConnect ();
										key.interestOps (SelectionKey.OP_READ);
										peerThreads.execute (new Runnable ()
										{
											@Override
											public void run ()
											{
												peer.onConnect ();
											}
										});
									}
									catch ( IOException e )
									{
										connectedPeers.remove (peer.channel);
										connectSlot.release ();
										key.cancel ();
									}
								}
								else
								{
									try
									{
										if ( key.isReadable () )
										{
											SocketChannel client = (SocketChannel) key.channel ();
											int len;
											len = client.read (readBuffer);
											if ( len > 0 )
											{
												peer.process (readBuffer, len);
												readBuffer.clear ();
											}
											else
											{
												peer.disconnect ();
											}
										}
										if ( key.isWritable () )
										{
											SocketChannel client = (SocketChannel) key.channel ();
											ByteBuffer b;
											if ( (b = peer.getBuffer ()) != null )
											{
												client.write (b);
												int rest = b.remaining ();
												if ( rest != 0 )
												{
													peer.pushBack (b);
												}
												else
												{
													peer.pushBack (null);
												}
											}
											else
											{
												key.interestOps (SelectionKey.OP_READ);
											}
										}
									}
									catch ( IOException e )
									{
										peer.disconnect ();
									}
								}
							}
						}
					}
				}
				catch ( Exception e )
				{
					log.error ("Fatal selector failure ", e);
				}
			}
		});
		selectorThread.setDaemon (false);
		selectorThread.setPriority (Thread.NORM_PRIORITY - 1);
		selectorThread.setName ("Peer selector");

		// this thread keeps looking for new connections
		connector = new Thread (new Runnable ()
		{
			@Override
			public void run ()
			{
				while ( true )
				{ // forever
					try
					{
						connectSlot.acquireUninterruptibly ();

						InetSocketAddress address = runqueue.poll ();
						if ( address != null )
						{
							final Peer peer = createPeer (address, true);
							peer.connect ();
							scheduler.schedule (new Runnable ()
							{
								@Override
								public void run ()
								{
									if ( peer.channel.isConnectionPending () )
									{
										try
										{
											log.trace ("Give up connect on " + peer.channel);
											peer.channel.close ();
											connectedPeers.remove (peer);
											connectSlot.release ();
										}
										catch ( IOException e )
										{
										}
									}
								}
							}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
						}
						else
						{
							if ( connectedPeers.size () < desiredConnections )
							{
								log.trace ("Need to discover new adresses.");
								if ( !discover () )
								{
									break; // testing
								}
								if ( runqueue.size () == 0 )
								{
									log.trace ("Can not find new adresses");
									Thread.sleep (60 * 1000);
								}
							}
						}
					}
					catch ( Exception e )
					{
						log.debug ("Unhandled exception in peer queue", e);
					}
				}
			}
		});
		connector.setDaemon (true);
		connector.setName ("Peer connector");
	}

	public void stop ()
	{
		peerThreads.shutdown ();
		selectorChanges.add (new ChangeRequest (null, ChangeRequest.STOP, SelectionKey.OP_ACCEPT));
		selector.wakeup ();
	}

	public void start () throws IOException
	{
		final ServerSocketChannel serverChannel = ServerSocketChannel.open ();
		serverChannel.socket ().bind (new InetSocketAddress (port));
		serverChannel.configureBlocking (false);

		selectorChanges.add (new ChangeRequest (serverChannel, ChangeRequest.REGISTER, SelectionKey.OP_ACCEPT));

		selectorThread.start ();

		connector.start ();
	}
}
