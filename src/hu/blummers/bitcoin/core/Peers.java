package hu.blummers.bitcoin.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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

	public class Peer {
		
		private void trace (String s)
		{
			log.info("Peer ["+address+"] " +s);
		}
		
		private void error (String s, Exception e)
		{
			log.error("Peer ["+address+"] " +s, e);			
		}
		
		private InetSocketAddress address;
		private SocketChannel channel;
		private int trust = 0;
		private BigInteger selfCheckNonce;
		
		private ConcurrentLinkedQueue<byte[]> writes = new ConcurrentLinkedQueue<byte[]>();
		private LinkedBlockingQueue<byte[]> reads = new LinkedBlockingQueue<byte[]>();
		private ByteArrayInputStream currentRead = null;
		private Semaphore interruptable = new Semaphore (0);
		private InputStream readIn = new InputStream() {
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
			public int read(byte[] b) throws IOException {
				return read (b, 0, b.length);
			}
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

		public Peer(InetSocketAddress address) {
			this.address = address;
		}
		
		public void connect () throws IOException
		{
			trace ("connecting ...");
			channel = SocketChannel.open();
			channel.configureBlocking(false);
			channel.connect(address);
			selectorChanges.add(new ChangeRequest(channel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
			selector.wakeup();
		}

		@Override
		public boolean equals(Object obj) {
			return address.equals(((Peer)obj).address);
		}

		@Override
		public int hashCode() {
			return address.hashCode();
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
			try {
				channel.close();
			} catch (IOException e) {
			}
			connectionsCounter.decrementAndGet();
			trace ("disconnecting. reason: " + why);
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
						error ("unhandled exception while processing a message ", e);
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
				error("exception in receive", e);
				decreaseTrust();
				disconnect("malformed package " + e.getMessage());
			}
			return null;
		}
		
		public void handshake() {
			VersionMessage m = MessageFactory.createVersionMessage(chain);
			m.setPeer(((SocketChannel)channel).socket().getInetAddress());
			m.setRemotePort(((SocketChannel)channel).socket().getPort());
			trace("sending version");
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
			trace ("received " + m.getCommand());
			if ( m.getCommand().equals("version") )
			{
				trace ("counterparty " + ((VersionMessage)m).getAgent());
				send (MessageFactory.createVerackMessage(chain));
			}
			else if ( m.getCommand().equals("addr") )
			{
				for ( WireFormat.Address a : ((AddrMessage)m).getAddresses() )
				{
					addPeer (a.address, (int)a.port);
				}
			}
			
		}
		
	}

	public void addPeer(InetAddress addr, int port) {
		InetSocketAddress address = new InetSocketAddress(addr, port);
		Peer peer = new Peer (address);
		knownPeers.put(address, peer);
		runqueue.add(peer);
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
	private Map<InetSocketAddress, Peer> knownPeers = Collections.synchronizedMap(new HashMap<InetSocketAddress, Peer>());

	// peers waiting to be connected
	private LinkedBlockingQueue<Peer> runqueue = new LinkedBlockingQueue<Peer>();

	// total number of threads deals with P2P
	private static final int PEERTHREADS = 20;

	// minimum trust - if below disconnect
	private static final int MIN_TRUST = -20;

	// maximum trust, can not get better than this
	private static final int MAX_TRUST = 100;

	// number of connections we try to maintain
	private static final int MAXCONNECTIONS = 20;

	// number of seconds to wait until giving up on connections
	private static final int READTIMEOUT = 30; // seconds

	// try this many peers to connect in a batch (faster wins, slower will be
	// cancelled)
	private static final int CONNECTBATCH = 10;

	// the blockchain
	private final Chain chain;

	// keep track with number of connections we asked for here
	private final AtomicInteger connectionsCounter = new AtomicInteger(0);

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
									if (connectionsCounter.incrementAndGet() <= MAXCONNECTIONS) {
										// if we have interest ...
										client.configureBlocking(false);
										InetSocketAddress address = (InetSocketAddress) client.socket().getRemoteSocketAddress();
										final Peer peer;
										if ( knownPeers.containsKey(address))
										{
											peer = knownPeers.get(address);
											peer.channel = client;
										}
										else
											peer = new Peer(address);
										peerThreads.execute(new Runnable (){
											public void run() {
												peer.handshake();
											}});
									} 
									else
									{
										connectionsCounter.decrementAndGet();
										client.close(); // no interest
									}
								}
								if (key.isConnectable()) {
									// we asked for connection here
									if (connectionsCounter.incrementAndGet() <= MAXCONNECTIONS) {
										// if we still have interest
										try
										{
											SocketChannel client = (SocketChannel) key.channel();
											client.finishConnect(); // finish protocol
											InetSocketAddress address = (InetSocketAddress) client.socket().getRemoteSocketAddress();
											final Peer peer;
											if ( knownPeers.containsKey(address)) { 
												peer = knownPeers.get(address);
											} else
												peer = new Peer((InetSocketAddress) client.socket().getRemoteSocketAddress());
											peerThreads.execute(new Runnable (){
												public void run() {
													peer.handshake();
												}});
										}
										catch ( ConnectException e ) {}
									} 
									else
									{
										connectionsCounter.decrementAndGet();
										key.channel().close(); // no interest
									}
								}
								if (key.isReadable()) {
									SocketChannel client = (SocketChannel) key.channel();
									final Peer peer = knownPeers.get((InetSocketAddress) client.socket().getRemoteSocketAddress());
									if (peer != null) {
										ByteBuffer b = ByteBuffer.allocate(8912);
										try {
											int len = client.read(b);
											if ( len > 0 )
											{
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
									Peer peer = knownPeers.get((InetSocketAddress) client.socket().getRemoteSocketAddress()); 
									if (peer != null) {
										ByteBuffer b;
										try
										{
											if ((b = peer.getBuffer()) != null)
												client.write(b);
											else
												key.interestOps(SelectionKey.OP_READ);
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
						if (connectionsCounter.get() < MAXCONNECTIONS) { 
							// for all in this batch
							for ( int i = 0; i < CONNECTBATCH; ++i )
							{
								Peer peer = runqueue.take(); 
								try{
									peer.connect();
								} catch ( NoRouteToHostException e ) {
								} catch ( ConnectException e ) {
								}
							}
							Thread.sleep(10000);
						}
					} catch (Exception e) {
						log.error("Unhandled exception in peer queue", e);
					}
				}
			}
		});
	}
}
