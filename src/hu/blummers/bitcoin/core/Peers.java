package hu.blummers.bitcoin.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.purser.server.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Arrays;

public class Peers {
	private static final Logger log = LoggerFactory.getLogger(Peers.class);

	public class Peer implements Comparable<Peer> {
		private Channel channel;
		private boolean unsolicited;
		private int trust = 0;
		private ConcurrentLinkedQueue<byte[]> writes = new ConcurrentLinkedQueue<byte[]>();
		private LinkedBlockingQueue<byte[]> reads = new LinkedBlockingQueue<byte[]>();
		private ByteArrayInputStream currentRead = null;
		private Semaphore interruptable = new Semaphore (0);
		private InputStream readIn = new InputStream() {
			@Override
			public int available() throws IOException {
				if ( currentRead != null )
					return currentRead.available();
				return 0;
			}

			@Override
			public boolean markSupported() {
				return false;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				int i = 0;
				for ( ; i < len; ++i)
				{
					int c = read ();
					if ( c < 0 )
						return c;
					b [off+i] = (byte)c;
				}
				return i;
			}

			@Override
			public int read(byte[] b) throws IOException {
				return read (b, 0, b.length);
			}

			@Override
			public int read() throws IOException {
				int b;
				while (currentRead == null || (b = currentRead.read()) < 0)
				{
					byte [] buf = null;
					try
					{
						interruptable.release();
						buf = reads.take();
					}
					catch (InterruptedException e) {
						throw new IOException(e);
					}
					finally	{
						interruptable.acquireUninterruptibly();
					} 
					currentRead = new ByteArrayInputStream(buf);
				}
				return b;
			}
		};

		public Peer(Channel channel) {
			this.channel = channel;
			unsolicited = true;
		}

		public Channel getChannel() {
			return channel;
		}

		public boolean isUnsolicited() {
			return unsolicited;
		}

		public void setUnsolicited(boolean unsolicited) {
			this.unsolicited = unsolicited;
		}
		
		public int getPort ()
		{
			return ((SocketChannel) channel).socket().getPort();
		}

		@Override
		public boolean equals(Object obj) {
			if (channel instanceof SocketChannel && obj instanceof SocketChannel) {
				return ((SocketChannel) channel).socket().getInetAddress().equals(((SocketChannel) obj).socket().getInetAddress());
			}
			return channel.equals(obj);
		}

		@Override
		public int hashCode() {
			if (channel instanceof SocketChannel) {
				return ((SocketChannel) channel).socket().getInetAddress().hashCode();
			}
			return channel.hashCode();
		}

		@Override
		public int compareTo(Peer other) {
			return other.trust - trust;
		}

		public int getTrust() {
			return trust;
		}

		public void increaseTrust() {
			if (trust < MAX_TRUST)
				++trust;
		}

		public void decreaseTrust() {
			if (trust > MIN_TRUST) {
				--trust;
			}
			if (trust <= MIN_TRUST) {
				disconnect("distrust");
				if (!unsolicited) // do not forgive unsolicited
					trust = 0;
			}
		}

		public void process(ByteBuffer buffer, int len) {
			if (len > 0) {
				byte[] b = new byte[len];
				System.arraycopy(buffer.array(), 0, b, 0, len);
				reads.add(b);
			}
		}

		public ByteBuffer getBuffer() {
			byte[] next;
			if ((next = writes.poll()) != null)
				return ByteBuffer.wrap(next);

			return null;
		}

		public void disconnect(String why) {
			connectedPeers.remove(this);
			try {
				channel.close();
			} catch (IOException e) {
			}
			if (unsolicited)
				unsolicitedCounter.incrementAndGet();
			else
				connectionsCounter.incrementAndGet();

			log.info("Disconnect [" + why + "] " + channel);
		}

		private void listen ()
		{
			// schedule a listening task
			peerThreads.execute(new Runnable (){
				
				// listening task
				@Override
				public void run() {
					final Thread workingThread = Thread.currentThread();
					
					ScheduledFuture killer = scheduler.schedule(new Runnable (){
						@Override
						public void run() {
							if ( interruptable.tryAcquire())
								workingThread.interrupt();
						}}, READTIMEOUT, TimeUnit.SECONDS);
					Message m = null;
					try {
						m = receive ();
						killer.cancel(false);
					} catch (IOException e) {
						disconnect ("lost");
					}				
					
					try
					{
						if ( m != null )
							processMessage (m);
					}
					catch ( Exception e )
					{
						log.error ("unhandled exception while processing a message ", e);
					}
								
					// listen again in the next available thread
					listen ();			

				}});
		}
		
		private Message receive() throws IOException {
			try {
				byte[] head = new byte[24];
				readIn.read(head);
				WireFormat.Reader reader = new WireFormat.Reader(head);
				long mag = reader.readUint32();
				if ( mag != chain.getMagic() )
					throw new ValidationException("Wrong magic for this chain" + mag + " vs " + chain.getMagic());

				String command = reader.readZeroDelimitedString(12);
				Message m = MessageFactory.createMessage(chain, command);
				long length = reader.readUint32();
				byte[] checksum = reader.readBytes(4);
				if ( length > 0 )
				{
					byte[] buf = new byte[(int) length];
					readIn.read(buf);
					byte[] cs = new byte[4];
					MessageDigest sha;
					try {
						sha = MessageDigest.getInstance("SHA-256");
						System.arraycopy(sha.digest(sha.digest(buf)), 0, cs, 0, 4);
					} catch (NoSuchAlgorithmException e) {
					}
					if (!Arrays.equals(cs, checksum))
						throw new ValidationException("Checksum mismatch");
	
					if ( m != null )
					{  // unknown message
						m.fromWire(new WireFormat.Reader(buf));
						m.validate(chain);
					}
				}
				return m;
			} catch (ValidationException e) {
				log.error("exception in receive", e);
				decreaseTrust();
				disconnect("malformed package " + e.getMessage());
			}
			return null;
		}
		
		public void handshake() {
			VersionMessage m = MessageFactory.createVersionMessage(chain);
			m.setPeer(((SocketChannel)channel).socket().getInetAddress());
			m.setRemotePort(((SocketChannel)channel).socket().getPort());
			log.info("sending version message to " + channel);
			send (m);
			listen ();
		}

		public void send(Message m) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			WireFormat.Writer writer = new WireFormat.Writer(out);
			writer.writeUint32(chain.getMagic());
			writer.writeZeroDelimitedString(m.getCommand(), 12);
			WireFormat.Writer payload = new WireFormat.Writer(new ByteArrayOutputStream());
			m.toWire(payload);
			byte[] data = payload.toByteArray();
			writer.writeUint32(data.length);

			byte[] checksum = new byte[4];
			MessageDigest sha;
			try {
				sha = MessageDigest.getInstance("SHA-256");
				System.arraycopy(sha.digest(sha.digest(data)), 0, checksum, 0, 4);
			} catch (NoSuchAlgorithmException e) {
			}
			writer.writeBytes(checksum);

			writer.writeBytes(data);

			writes.add(out.toByteArray());
			selectorChanges.add(new ChangeRequest((SocketChannel) channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
			selector.wakeup();
		}

		private void processMessage (Message m)
		{
			if ( m.getCommand().equals("version") )
			{
				log.info("counterparty " + ((VersionMessage)m).getAgent());
				send (MessageFactory.createVerackMessage(chain));
			}
			else if ( m.getCommand().equals("verack") )
			{
				log.info("connected to " + channel);
			}
			else if ( m.getCommand().equals("inv") )
			{
			}
			else
			{
				log.info("message ["+ m.getCommand() +"] from " + channel);
			}
		}
		
	}

	public void addPeer(InetAddress addr, int port) {
		Peer peer = new Peer(new AvailableChannel(new InetSocketAddress(addr, port)));
		synchronized (knownPeers) {
			Peer storedPeer = knownPeers.get(peer);
			if (storedPeer == null) {
				knownPeers.put(peer, peer); // add peer only if not already
											// known.
			}
		}
		synchronized (connectedPeers) {
			if (!connectedPeers.containsKey(peer))
				runqueue.add(peer); // if not already connected add to the run
									// queue
		}
	}

	public void discover(Chain chain) {
		for (String hostName : chain.getSeedHosts()) {
			try {
				InetAddress[] hostAddresses = InetAddress.getAllByName(hostName);

				for (InetAddress inetAddress : hostAddresses) {
					addPeer(inetAddress, chain.getPort());
				}
			} catch (Exception e) {
				log.info("DNS lookup for " + hostName + " failed.");
			}
		}
	}

	// peers we have seen, the key is Peer, but that compares on internet
	// address+port for SocketChannel (and also for AvailableChannel)
	private Map<Peer, Peer> knownPeers = Collections.synchronizedMap(new HashMap<Peer, Peer>());

	// currently connected peers by channel, since selector provides us channel
	// to locate the subject
	private Map<Channel, Peer> connectedPeers = new HashMap<Channel, Peer>();

	// list of peers waiting to be connected ordered by the "natural" order of
	// Peers see Peer.compareTo
	private PriorityBlockingQueue<Peer> runqueue = new PriorityBlockingQueue<Peer>();

	// total number of threads deals with P2P
	private static final int PEERTHREADS = 20;

	// minimum trust - if below disconnect
	private static final int MIN_TRUST = -20;

	// maximum trust, can not get better than this
	private static final int MAX_TRUST = 100;

	// number of connections we try to maintain
	private static final int MINCONNECTIONS = 20;

	// number of unsolicited incoming connections we accept
	private static final int UNSOLICITED = 10;

	// number of seconds to wait until giving up on connections
	private static final int READTIMEOUT = 30; // seconds

	// try this many peers to connect in a batch (faster wins, slower will be
	// cancelled)
	private static final int CONNECTBATCH = 20;

	// the blockchain
	private final Chain chain;

	// keep track with number of connections we asked for here
	private final AtomicInteger connectionsCounter = new AtomicInteger(0);

	// keep track of unsolicited connections here
	private final AtomicInteger unsolicitedCounter = new AtomicInteger(0);

	private class ChangeRequest {
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

	final ExecutorService peerThreads;
	// need this to cancel what takes too long
	final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


	public Peers(Chain chain) throws IOException {
		this.chain = chain;

		// create a pool of threads
		peerThreads = Executors.newFixedThreadPool(PEERTHREADS, new ThreadFactory() {
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

		// this thread waits on the selector above and acts on events
		peerThreads.execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						ChangeRequest cr;
						while ((cr = selectorChanges.poll()) != null) {
							if (!cr.socket.isOpen())
								continue;
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
							try
							{
								SelectionKey key = keys.next(); 
								keys.remove();
	
								if (key.isAcceptable()) {
									// unsolicited request to connect
									final SocketChannel client = ((ServerSocketChannel) key.channel()).accept(); 
									if (unsolicitedCounter.incrementAndGet() <= UNSOLICITED) {
										// if we have interest ...
										client.configureBlocking(false); 
										client.register(selector, SelectionKey.OP_READ); 
										Peer peer;
										if ( knownPeers.containsKey(client)) { 
											peer = knownPeers.get(client);
											if (peer.getChannel() instanceof AvailableChannel) 
												peer = new Peer(client); 
										} else
											peer = new Peer(client);
										peer.setUnsolicited(true); 
										connectedPeers.put(client, peer); 
										log.info("Unsolicited connection to  " + client);
										
										final Peer finalPeer = peer;
										peerThreads.execute(new Runnable (){
											@Override
											public void run() {
												finalPeer.handshake();
											}});
									} else
										client.close(); // no interest
								}
								if (key.isConnectable()) {
									// we asked for connection here
									if (connectionsCounter.incrementAndGet() <= MINCONNECTIONS) {
										// if we still have interest
										try
										{
											SocketChannel client = (SocketChannel) key.channel();
											client.finishConnect(); // finish protocol
											Peer peer;
											if ( knownPeers.containsKey(client)) { 
												peer = knownPeers.get(client);
												if (peer.getChannel() instanceof AvailableChannel)
													peer = new Peer(client);
											} else
												peer = new Peer(client);
											connectedPeers.put(client, peer);
											final Peer finalPeer = peer;
											peerThreads.execute(new Runnable (){
												@Override
												public void run() {
													finalPeer.handshake();
												}});
		
											log.info("Connecting to ... " + client);
										}
										catch ( ConnectException e ) {}
									} else
										key.channel().close(); // no interest
								}
								if (key.isReadable()) {
									SocketChannel client = (SocketChannel) key.channel();
									final Peer peer = connectedPeers.get(client); 
									if (peer != null) {
										ByteBuffer b = ByteBuffer.allocate(8912);
										try {
											int len = client.read(b);
											if ( len > 0 )
											{
												log.info("received " + len + " bytes from " + peer.channel);
												peer.process(b, len);
											}
										}
										catch ( IOException e )
										{
											peer.disconnect("lost");
										}
									}
								}
								if (key.isWritable()) {
									SocketChannel client = (SocketChannel) key.channel();
									Peer peer = connectedPeers.get(client); 
									if (peer != null) {
										ByteBuffer b;
										try
										{
											if ((b = peer.getBuffer()) != null)
												client.write(b);
											else
												key.interestOps(SelectionKey.OP_READ);
											log.info("sent to " + peer.channel);
										}
										catch ( IOException e )
										{
											peer.disconnect ("lost");
										}
									}
								}
							}
							catch ( CancelledKeyException e ) {						
							} 
							catch (Exception e) {
								log.error("Error processing a selector key", e);
							}
						}
					} 
					catch ( Exception e )
					{
						log.error("Unhandled Exception in selector thread", e);
					}
				}
			}
		});

		// create a server channel for the chain's port, work non-blocking and
		// wait for accept events
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.socket().bind(new InetSocketAddress(chain.getPort()));
		serverChannel.configureBlocking(false);
		
		selectorChanges.add(new ChangeRequest(serverChannel, ChangeRequest.REGISTER, SelectionKey.OP_ACCEPT));
		selector.wakeup();

		// this thread keeps looking for new connections
		peerThreads.execute(new Runnable() {
			@Override
			public void run() {
				while (true) { // forever
					try {
						List<Peer> batch = new ArrayList<Peer>();
						if (connectionsCounter.get() < MINCONNECTIONS) { 
							runqueue.drainTo(batch, CONNECTBATCH); 
							// for all in this batch
							for (Peer peer : batch) {
								// connect non-bloking manner, registering for
								// connect notifications
								final SocketChannel channel = SocketChannel.open();
								try{
									log.info("Trying to connect " + peer.getChannel());
									channel.configureBlocking(false);
									channel.connect(((AvailableChannel) peer.getChannel()).getSocketAddress());
									selectorChanges.add(new ChangeRequest(channel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
								} catch ( NoRouteToHostException e ) {
									channel.close();
								}

							}
							if ( batch.size() > 0 )
								selector.wakeup();

							Thread.sleep(5000);

						}
					} catch (Exception e) {
						log.error("Unhandled exception in peer queue", e);
					}
				}
			}
		});
	}

	// a helper class that stores socket address until it gets connected
	private class AvailableChannel implements Channel {
		private InetSocketAddress soa;

		public AvailableChannel(InetSocketAddress soa) {
			this.soa = soa;
		}

		public InetSocketAddress getSocketAddress() {
			return soa;
		}

		@Override
		public boolean equals(Object obj) {
			return soa.equals(obj);
		}

		@Override
		public int hashCode() {
			return soa.hashCode();
		}

		@Override
		public String toString() {
			return soa.toString();
		}

		@Override
		public void close() throws IOException {
		}

		@Override
		public boolean isOpen() {
			return false;
		}

	}
}
