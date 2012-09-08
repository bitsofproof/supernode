/**
* Copyright 2012 Tamas Blummer
* 
* High performance Peer to Peer client and server using NIO.
* 
* USAGE
* 1. Derive your YourPeer from P2P.Peer, your YourMessage from P2P.Message
* 2. implement their abstract methods (see below)
* 3. Derive your P2PNetwork from P2P
* 4. implement createPeer to create your peer object for an address
* 5. Instantiate P2PNetwork
* 7. add some peer addresses using addPeer ()
* 8. call P2PNetwork.start ()
* 9. enjoy:
* 		P2PNetwork.createPeer will be called to instantiate your Peers
* 		YourPeer.handshake () will be called first send (YourMessage) what is needed to introduce your peer 
* 						no receive available until this returns.
* 		YourMessage YourPeer.createMessage (InputStream in) should instantiate any message from wire
* 						do not do any other processing here
* 		YourPeer.processMassage(Message) should do the message processing. 
* 						This might call send (YourMessage) or addPeer () but nothing else from this framework 
* 
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
package hu.blummers.p2p;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class P2P {
	private static final Logger logger = LoggerFactory.getLogger(P2P.class);
	
	public interface Message
	{
		public byte [] toByteArray ();
	}
	
	public abstract class Peer {
		boolean connected = false;
		private InetSocketAddress address;
		private SocketChannel channel;
		private int trust = 0;
		private ConcurrentLinkedQueue<byte[]> writes = new ConcurrentLinkedQueue<byte[]>();
		private LinkedBlockingQueue<byte[]> reads = new LinkedBlockingQueue<byte[]>();
		private ByteArrayInputStream currentRead = null;
		private InputStream readIn = new InputStream() {
			public int read(byte[] b, int off, int len) throws IOException {
				int need = len;
				if (need <= 0)
					return need;
				do {
					if (currentRead != null) {
						int r = currentRead.read(b, off, need);
						if (r > 0) {
							off += r;
							need -= r;
						}
					}
					if (need == 0) {
						return len;
					}
					byte[] buf = null;
					try {
						buf = reads.poll(READTIMEOUT, TimeUnit.SECONDS);
						if (buf == null)
							return -1;
					} catch (InterruptedException e) {
						throw new IOException(e);
					}
					currentRead = new ByteArrayInputStream(buf);
				} while (need > 0);
				return len;
			}

			public int read(byte[] b) throws IOException {
				return read(b, 0, b.length);
			}

			public int read() throws IOException {
				byte[] b = new byte[1];
				return read(b, 0, 1);
			}
		};

		public Peer(InetSocketAddress address) {
			this.address = address;
		}

		private void connect() {
			try {
				channel = SocketChannel.open();
				channel.configureBlocking(false);
				channel.connect(address);
				selectorChanges.add(new ChangeRequest(channel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
				selector.wakeup();
			} catch (IOException e) {
			}
		}

		@Override
		public boolean equals(Object obj) {
			return address.equals(((Peer) obj).address);
		}

		@Override
		public int hashCode() {
			return address.hashCode();
		}

		public int getTrust() {
			return trust;
		}

		public void increaseTrust() {
			if (trust < MAXIMUMTRUST)
				++trust;
		}

		public void decreaseTrust() {
			if (trust > MINIMUMTRUST) {
				--trust;
			}
			if (trust <= MINIMUMTRUST) {
				disconnect("distrust");
			}
		}

		private void process(ByteBuffer buffer, int len) {
			if (len > 0) {
				byte[] b = new byte[len];
				System.arraycopy(buffer.array(), 0, b, 0, len);
				reads.add(b);
			}
		}

		private ByteBuffer getBuffer() {
			byte[] next;
			if ((next = writes.poll()) != null)
				return ByteBuffer.wrap(next);

			return null;
		}

		public boolean isConnected() {
			return connected;
		}

		public InetSocketAddress getAddress() {
			return address;
		}

		public void disconnect(String why) {
			try {
				connected = false;
				connectedPeers.remove(channel);
				channel.close();
			} catch (IOException e) {
			}
			connectSlot.release();
		}

		private void listen() {
			peerThreads.execute(new Runnable() {
				public void run() {
					Message m = null;
					try {
						m = receive(readIn);
					} catch (IOException e) {
						disconnect("lost");
					}
					try {
						if (m != null) {
							processMessage(m);
							peerThreads.execute(this); // listen again
						}
					} catch (Exception e) {
						logger.error("unhandled exception while processing a message ", e);
					}
				}
			});
		}

		public void send(Message m) {
			writes.add(m.toByteArray());
			selectorChanges.add(new ChangeRequest((SocketChannel) channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
			selector.wakeup();
		}

		public abstract Message receive(InputStream readIn) throws IOException;

		public abstract void handshake();

		public abstract void processMessage(Message m);
		
	}
	
	public abstract Peer createPeer (InetSocketAddress address);
	
	public void addPeer(InetAddress addr, int port) {
		InetSocketAddress address = new InetSocketAddress(addr, port);
		synchronized (knownPeers) {
			if (!knownPeers.containsKey(address)) {
				Peer peer = createPeer (address);
				knownPeers.put(address, peer);
				runqueue.add(peer);
			}
		}
	}

	// peers we have seen, the key is Peer, but that compares on internet
	// address+port for SocketChannel (and also for AvailableChannel)
	private final Map<InetSocketAddress, Peer> knownPeers = Collections.synchronizedMap(new HashMap<InetSocketAddress, Peer>());

	private final Map<SocketChannel, Peer> connectedPeers = Collections.synchronizedMap(new HashMap<SocketChannel, Peer>());

	// peers waiting to be connected
	private final LinkedBlockingQueue<Peer> runqueue = new LinkedBlockingQueue<Peer>();

	// total number of threads deals with P2P
	private static final int NUMBEROFPEERTHREADS = 20;

	// minimum trust - if below disconnect
	private static final  int MINIMUMTRUST = -20;

	// maximum trust, can not get better than this
	private static final int MAXIMUMTRUST = 100;

	// number of connections we try to maintain
	private static final int MAXIMUMCONNECTIONS = 100;

	// number of seconds to wait until giving up on connections
	private static final int READTIMEOUT = 60; // seconds

	// keep track with number of connections we asked for here
	private final Semaphore connectSlot = new Semaphore(MAXIMUMCONNECTIONS);

	private final int port;

	private static class ChangeRequest {
		public static final int REGISTER = 1;
		public static final int CHANGEOPS = 2;

		public SelectableChannel socket;
		public int type;
		public int ops;

		public ChangeRequest(SelectableChannel socket, int type, int ops) {
			this.socket = socket;
			this.type = type;
			this.ops = ops;
		}
	}

	private final ConcurrentLinkedQueue<ChangeRequest> selectorChanges = new ConcurrentLinkedQueue<ChangeRequest>();

	final Selector selector = Selector.open();
	
	private final Executor peerThreads;
	
	public P2P (int port) throws IOException
	{
		this.port = port;
		peerThreads = Executors.newFixedThreadPool(NUMBEROFPEERTHREADS, new ThreadFactory() {
			@Override
			public Thread newThread(final Runnable r) {
				Thread peerThread = new Thread() {
					@Override
					public void run() {
						r.run(); // just delegate
					}
				};
				peerThread.setDaemon(true); // let VM exit if only these remain
				peerThread.setName("Peer"); // name it for log
				peerThread.setPriority(Math.max(Thread.currentThread().getPriority() - 1, Thread.MIN_PRIORITY));
				return peerThread;
			}
		});
	}

	public void start() throws IOException {
		// create a pool of threads
		// create a server channel for the chain's port, work non-blocking and
		// wait for accept events
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.socket().bind(new InetSocketAddress(port));
		serverChannel.configureBlocking(false);

		selectorChanges.add(new ChangeRequest(serverChannel, ChangeRequest.REGISTER, SelectionKey.OP_ACCEPT));
		selector.wakeup();
		
		// this thread keeps looking for new connections
		Thread connector = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) { // forever
					connectSlot.acquireUninterruptibly();
					try {
						runqueue.take().connect();
					} catch (Exception e) {
						logger.error("Unhandled exception in peer queue", e);
					}
				}
			}
		});
		connector.setPriority(Math.max(Thread.currentThread().getPriority() - 1, Thread.MIN_PRIORITY));
		connector.setDaemon(true);
		connector.setName("Peer connector");
		connector.start();
		
		// this thread waits on the selector above and acts on events
		Thread selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						ChangeRequest cr;
						while ((cr = selectorChanges.poll()) != null) {
							if (cr.type == ChangeRequest.REGISTER)
								cr.socket.register(selector, cr.ops);
							else if (cr.type == ChangeRequest.CHANGEOPS) {
								SelectionKey key = cr.socket.keyFor(selector);
								key.interestOps(cr.ops);
							}
						}
						selector.select(); // wait for events
						Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
						while (keys.hasNext()) {
							try {
								SelectionKey key = keys.next();
								keys.remove();

								if (key.isAcceptable()) {
									// unsolicited request to connect
									final SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
									client.configureBlocking(false);
									InetSocketAddress address = (InetSocketAddress) client.socket().getRemoteSocketAddress();
									final Peer peer;

									if (knownPeers.containsKey(address)) {
										peer = knownPeers.get(address);
									} else {
										peer = createPeer (address);
										knownPeers.put(address, peer);
									}
									peer.channel = client;
									if (connectSlot.tryAcquire()) {
										// if we have interest ...										
										connectedPeers.put(client, peer);
										key.interestOps(SelectionKey.OP_WRITE);
										peerThreads.execute(new Runnable() {
											public void run() {
												peer.connected = true;
												peer.handshake();
												peer.listen();
											}
										});
									} else {
										client.close(); 
										runqueue.add(peer); // try later
									}
								}
								if (key.isConnectable()) {
									try {
										// we asked for connection here
										SocketChannel client = (SocketChannel) key.channel();
										client.finishConnect(); // finish
										InetSocketAddress address = (InetSocketAddress) client.socket().getRemoteSocketAddress();
										final Peer peer;
										if (knownPeers.containsKey(address)) {
											peer = knownPeers.get(address);
											if (connectSlot.tryAcquire()) {
												connectedPeers.put(client, peer);
												key.interestOps(SelectionKey.OP_WRITE);
												peerThreads.execute(new Runnable() {
													public void run() {
														peer.connected = true;
														peer.handshake();
														peer.listen();
													}
												});
											} else {
												key.channel().close();
												runqueue.add(peer); // try again
																	// later
											}
										} else {
											key.channel().close(); // do not
																	// know you
										}

									} catch (ConnectException e) {
									}
								}
								if (key.isReadable()) {
									SocketChannel client = (SocketChannel) key.channel();
									final Peer peer = connectedPeers.get(client);
									if (peer != null) {
										ByteBuffer b = ByteBuffer.allocate(8912);
										try {
											int len = client.read(b);
											if (len > 0) {
												peer.process(b, len);
											}
										} catch (IOException e) {
											peer.disconnect("lost");
										}
									}
								}
								if (key.isWritable()) {
									SocketChannel client = (SocketChannel) key.channel();
									Peer peer = connectedPeers.get(client);
									if (peer != null) {
										ByteBuffer b;
										try {
											if ((b = peer.getBuffer()) != null)
												client.write(b);
											else
												key.interestOps(SelectionKey.OP_READ);
										} catch (IOException e) {
											peer.disconnect("lost");
										}
									}
								}
							} catch (CancelledKeyException e) {
							} catch (Exception e) {
								logger.error("Error processing a selector key", e);
							}
						}
					} catch (Exception e) {
						logger.error("Unhandled Exception in selector thread", e);
					}
				}
			}
		});
		selectorThread.setPriority(Math.max(Thread.currentThread().getPriority() - 1, Thread.MIN_PRIORITY));
		selectorThread.setDaemon(true);
		selectorThread.setName("Peer selector");
		selectorThread.start();
	}
}
