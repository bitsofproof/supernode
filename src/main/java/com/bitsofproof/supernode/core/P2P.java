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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
		private boolean unsolicited;
		private final Semaphore connected = new Semaphore (0);
		private final Semaphore hasConnectionSlot = new Semaphore (0);

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

		private void connect () throws IOException
		{
			hasConnectionSlot.release ();

			channel = SocketChannel.open ();
			channel.configureBlocking (false);

			selectorChanges.add (new ChangeRequest (channel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT, this));
			selector.wakeup ();
		}

		public void doConnect () throws IOException
		{
			log.trace ("Trying to connect " + address);

			channel.connect (address);
		}

		@Override
		public boolean equals (Object obj)
		{
			if ( obj == this )
			{
				return true;
			}
			if ( obj == null )
			{
				return false;
			}

			return obj instanceof Peer && address.equals (((Peer) obj).address);
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

		protected void disconnect ()
		{
			disconnect (0, 0, null);
		}

		protected void disconnect (final long timeout, final long bannedFor, final String reason)
		{
			try
			{
				if ( hasConnectionSlot.tryAcquire () )
				{
					if ( unsolicited )
					{
						unsolicitedConnectSlot.release ();
					}
					else
					{
						connectSlot.release ();
					}
				}
				if ( connected.tryAcquire () )
				{
					openConnections.decrementAndGet ();
					log.trace ("Number of connections is now " + openConnections.get ());
				}
				if ( channel.isRegistered () )
				{
					selectorChanges.add (new ChangeRequest (channel, ChangeRequest.CANCEL, SelectionKey.OP_ACCEPT, null));
					selector.wakeup ();
				}
				if ( channel.isOpen () )
				{
					channel.close ();
					log.trace ("Disconnect " + channel);

					writes.clear ();
					reads.clear ();
					reads.add (closedMark);
					peerThreads.execute (new Runnable ()
					{
						@Override
						public void run ()
						{
							onDisconnect (timeout, bannedFor, reason);
						}
					});
				}
			}
			catch ( IOException e )
			{
				log.trace ("Exception in disconnect ", e);
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
			selectorChanges.add (new ChangeRequest (channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE | SelectionKey.OP_READ, null));
			selector.wakeup ();
		}

		protected abstract Message parse (InputStream readIn) throws IOException;

		protected abstract void receive (Message m);

		protected abstract void onConnect ();

		protected abstract void onDisconnect (long timeout, long bannedForSeconds, String reason);

		protected abstract boolean isHandshakeSuccessful ();
	}

	protected abstract Peer createPeer (InetSocketAddress address, boolean active);

	protected abstract boolean discover ();

	protected void addPeer (InetAddress addr, int port)
	{
		if ( !addr.isAnyLocalAddress () )
		{
			InetSocketAddress address = new InetSocketAddress (addr, port);
			if ( !runqueue.contains (address) )
			{
				runqueue.add (address);
			}
		}
	}

	public int getNumberOfConnections ()
	{
		return openConnections.get ();
	}

	private final AtomicInteger openConnections = new AtomicInteger (0);

	// peers waiting to be connected
	private final ConcurrentLinkedQueue<InetSocketAddress> runqueue = new ConcurrentLinkedQueue<InetSocketAddress> ();

	// number of connections we try to maintain
	private final int desiredConnections;

	// we want fast answering nodes
	private static final int CONNECTIONTIMEOUT = 5;

	// number of seconds to wait until giving up on connections
	private static final int READTIMEOUT = 5; // seconds

	// keep track of connections we asked for
	private final Semaphore connectSlot;

	// keep track of connections coming unsolicited
	private final Semaphore unsolicitedConnectSlot;

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);

	private int port;

	private boolean listen = true;

	public void setListen (boolean listen)
	{
		this.listen = listen;
	}

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

		public final SelectableChannel socket;
		public final int type;
		public final int ops;
		public final Peer peer;

		public ChangeRequest (SelectableChannel socket, int type, int ops, Peer peer)
		{
			this.socket = socket;
			this.type = type;
			this.ops = ops;
			this.peer = peer;
		}
	}

	private final ConcurrentLinkedQueue<ChangeRequest> selectorChanges = new ConcurrentLinkedQueue<ChangeRequest> ();

	private final Selector selector;

	private final ThreadPoolExecutor peerThreads;

	private final Thread selectorThread;

	private final Thread connector;

	private static class PeerFactory implements ThreadFactory
	{
		final static AtomicInteger peerNumber = new AtomicInteger (0);

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
			peerThread.setName ("Peer-thread-" + peerNumber.getAndIncrement ());
			return peerThread;
		}
	}

	public P2P (int connections) throws IOException
	{
		desiredConnections = connections;
		connectSlot = new Semaphore (desiredConnections);
		unsolicitedConnectSlot = new Semaphore (Math.max (desiredConnections / 2, 1));
		// create a pool of threads
		peerThreads =
				(ThreadPoolExecutor) Executors.newFixedThreadPool (Math.min (desiredConnections, Runtime.getRuntime ().availableProcessors () * 2),
						new PeerFactory ());

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
							try
							{
								if ( cr.type == ChangeRequest.STOP )
								{
									log.trace ("Seclector was asked to stop.");
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
										SelectionKey key = cr.socket.register (selector, cr.ops);
										if ( (cr.ops & SelectionKey.OP_ACCEPT) == 0 )
										{
											key.attach (cr.peer);
											cr.peer.doConnect ();
										}
									}
									catch ( ClosedChannelException e )
									{
										continue;
									}
									catch ( IOException e )
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
							catch ( CancelledKeyException e )
							{
							}
						}
						selector.select ();
						Iterator<SelectionKey> keys = selector.selectedKeys ().iterator ();
						while ( keys.hasNext () )
						{
							SelectionKey key = keys.next ();
							keys.remove ();
							try
							{
								if ( !key.isValid () )
								{
									key.attach (null);
									continue;
								}
								if ( key.isAcceptable () )
								{
									SocketChannel client;
									try
									{
										if ( unsolicitedConnectSlot.tryAcquire () )
										{
											client = ((ServerSocketChannel) key.channel ()).accept ();
											client.configureBlocking (false);
											InetSocketAddress address = (InetSocketAddress) client.socket ().getRemoteSocketAddress ();
											log.trace ("Unsolicited connection from " + address.getAddress ());
											if ( client.isOpen () )
											{
												final Peer peer;
												peer = createPeer (address, false);
												peer.channel = client;
												peer.unsolicited = true;
												peer.hasConnectionSlot.release ();
												SelectionKey reg = client.register (selector, SelectionKey.OP_READ);
												reg.attach (peer);
												openConnections.incrementAndGet ();
												peer.connected.release ();
												scheduler.schedule (new Runnable ()
												{
													@Override
													public void run ()
													{
														if ( !peer.isHandshakeSuccessful () )
														{
															peer.disconnect ();
														}
													}
												}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
											}
										}
									}
									catch ( IOException e )
									{
										log.trace ("unsuccessful unsolicited connection ", e);
									}
								}
								else
								{
									final Peer peer = (Peer) key.attachment ();
									if ( key.isConnectable () )
									{
										// we asked for connection here
										try
										{
											SocketChannel client = (SocketChannel) key.channel ();
											if ( client.finishConnect () )
											{
												openConnections.incrementAndGet ();
												key.interestOps (SelectionKey.OP_READ);
												peer.connected.release ();
												peerThreads.execute (new Runnable ()
												{
													@Override
													public void run ()
													{
														peer.onConnect ();
													}
												});
											}
											else
											{
												peer.disconnect ();
											}
										}
										catch ( IOException e )
										{
											peer.disconnect ();
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
												if ( len < 0 )
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
							catch ( CancelledKeyException e )
							{
								final Peer peer = (Peer) key.attachment ();
								if ( peer != null )
								{
									peer.disconnect ();
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
				{
					try
					{
						connectSlot.acquire ();

						InetSocketAddress address = null;
						do
						{
							if ( (address = runqueue.poll ()) == null )
							{
								log.trace ("Need to discover new adresses.");
								discover ();
								if ( (address = runqueue.poll ()) == null )
								{
									log.warn ("Could not discover peers");
									return;
								}
								log.trace ("Runqueue size " + (runqueue.size () + 1));
							}
						} while ( !address.getAddress ().isReachable (1000) );

						final Peer peer = createPeer (address, true);
						peer.unsolicited = false;
						peer.connect ();
						scheduler.schedule (new Runnable ()
						{
							@Override
							public void run ()
							{
								if ( !peer.channel.isConnected () )
								{
									log.trace ("Give up connect on " + peer.channel);
									peer.disconnect (Integer.MAX_VALUE, 0, null);
								}
							}
						}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
					}
					catch ( Exception e )
					{
						log.debug ("Unhandled exception in peer queue", e);
						connectSlot.release ();
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
		selectorChanges.add (new ChangeRequest (null, ChangeRequest.STOP, SelectionKey.OP_ACCEPT, null));
		selector.wakeup ();
	}

	public void start () throws IOException
	{
		if ( listen )
		{
			final ServerSocketChannel serverChannel = ServerSocketChannel.open ();
			serverChannel.socket ().bind (new InetSocketAddress (port));
			serverChannel.configureBlocking (false);

			selectorChanges.add (new ChangeRequest (serverChannel, ChangeRequest.REGISTER, SelectionKey.OP_ACCEPT, null));
			log.trace ("Listening on port " + port);
		}
		selectorThread.start ();

		connector.start ();
	}
}
