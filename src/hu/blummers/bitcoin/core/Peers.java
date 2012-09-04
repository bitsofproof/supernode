package hu.blummers.bitcoin.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
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
		private ConcurrentLinkedQueue<byte []> writes = new ConcurrentLinkedQueue<byte []>();		
		private LinkedBlockingQueue<Message> incoming = new LinkedBlockingQueue<Message>();

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
				log.info("Disconnecting a misbehaving peer " + channel);
				disconnect();
				if (!unsolicited) // do not forgive unsolicited
					trust = 0;
			}
		}
		
		public void readWholeMessage ()
		{
			try
			{
				ByteBuffer head = ByteBuffer.allocate(24);
				if ( ((SocketChannel)channel).read(head) != 24 )
					throw new ValidationException ("No package head");
				
				WireFormat.Reader reader = new WireFormat.Reader(head.array());
				if ( reader.readUint32() != chain.getMagic() )
					throw new ValidationException ("Wrong magic for this chain");
				
				String command = reader.readZeroDelimitedString(12);
				long length = reader.readUint32();
				byte [] checksum = reader.readBytes(4);
				ByteBuffer buf = ByteBuffer.allocate(Math.min(Math.abs((int)length), 1024));
				ByteArrayOutputStream out = new ByteArrayOutputStream ();
				MessageDigest sha = MessageDigest.getInstance("SHA-256");
				int len = 0;
				long s = 0;
				while ( (len = ((SocketChannel)channel).read(buf)) > 0 )
				{
					out.write(buf.array(), 0, len);
					sha.update(buf.array());
					s += len;
				}
				if ( s != length )
					throw new ValidationException ("Message length mismatch");
				byte [] cs = new byte [4];
				System.arraycopy(sha.digest(sha.digest()), 0, cs, 0, 4);
				if ( !Arrays.equals(cs, checksum) )
					throw new ValidationException ("Checksum mismatch");

				incoming.add(MessageFactory.getMessage(chain, command, new WireFormat.Reader(out.toByteArray())));
			}
			catch ( Exception e)
			{
				log.error("Malformed package from " + channel, e);
				decreaseTrust ();
				disconnect ();
			}
		}
		
		public ByteBuffer getBuffer ()
		{
			byte [] next;
			if ( (next = writes.poll()) != null )
				return ByteBuffer.wrap(next);
			
			return null;
		}

		public void disconnect() {
			connectedPeers.remove(this);
			try {
				channel.close();
			} catch (IOException e) {
			}
			if ( unsolicited )
				unsolicitedCounter.incrementAndGet();
			else
				connectionsCounter.incrementAndGet();
			
			log.info("Disconnected from " + channel);
		}

		public void introduce ()
		{
			
		}
		
		public List<Message> receive ()
		{
			List<Message> all = new ArrayList<Message> ();
			incoming.drainTo(all);
			return all;
		}
	
		public void send (Message m)
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream ();
			WireFormat.Writer writer = new WireFormat.Writer(out);
			writer.writeUint32(chain.getMagic());
			writer.writeZeroDelimitedString(m.getCommand(), 12);
			WireFormat.Writer payload = new WireFormat.Writer(new ByteArrayOutputStream ());
			m.toWire(payload);
			byte [] data = payload.toByteArray();
			writer.writeUint32(data.length);
			
			byte [] checksum = new byte [4];
			MessageDigest sha;
			try {
				sha = MessageDigest.getInstance("SHA-256");
				System.arraycopy(sha.digest(sha.digest(data)), 0, checksum, 0, 4);
			} catch (NoSuchAlgorithmException e) {
			}
			writer.writeBytes(checksum);					
			
			writer.writeBytes(data);

			writes.add(out.toByteArray());
			selectorChanges.add(new ChangeRequest ((SocketChannel)channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_READ|SelectionKey.OP_WRITE));
			selector.wakeup();
		}

	}

	public void addPeer(InetAddress addr, int port) {
		Peer peer = new Peer(new AvailableChannel(new InetSocketAddress(addr, port)));
		synchronized (knownPeers) {
			Peer storedPeer = knownPeers.get(peer);
			if (storedPeer == null) {
				knownPeers.put(peer, peer); // add peer only if not already known.
			}
		}
		synchronized (connectedPeers) {
			if (!connectedPeers.containsKey(peer))
				runqueue.add(peer); // if not already connected add to the run queue
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

	// peers we have seen, the key is Peer, but that compares on internet address+port for SocketChannel (and also for AvailableChannel)
	private Map<Peer, Peer> knownPeers = Collections.synchronizedMap(new HashMap<Peer, Peer>());
	
	// currently connected peers by channel, since selector provides us channel to locate the subject
	private Map<Channel, Peer> connectedPeers = new HashMap<Channel, Peer>();
	
	// list of peers waiting to be connected ordered by the "natural" order of Peers see Peer.compareTo
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
	private static final int CONNECTIONTIMEOUT = 5; // seconds
	
	// try this many peers to connect in a batch (faster wins, slower will be cancelled) 
	private static final int CONNECTBATCH = 50;

	// the blockchain
	private final Chain chain;
	
	// keep track with number of connections we asked for here
	private final AtomicInteger connectionsCounter = new AtomicInteger (0);
	
	// keep track of unsolicited connections here
	private final AtomicInteger unsolicitedCounter = new AtomicInteger (0);

	private class ChangeRequest {
		  public static final int REGISTER = 1;
		  public static final int CHANGEOPS = 2;
		  
		  public SocketChannel socket;
		  public int type;
		  public int ops;
		  
		  public ChangeRequest(SocketChannel socket, int type, int ops) {
		    this.socket = socket;
		    this.type = type;
		    this.ops = ops;
		  }
		}
	
	private final ConcurrentLinkedQueue<ChangeRequest> selectorChanges = new ConcurrentLinkedQueue<ChangeRequest>();

	final Selector selector = Selector.open(); // selector we register events to listen for
	
	
	public Peers(Chain chain) throws IOException {
		this.chain = chain;

	}

	public void start () throws IOException {
		
		// create a pool of threads
		final ExecutorService peerThreads = Executors.newFixedThreadPool(PEERTHREADS, new ThreadFactory() {
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
				peerThread.setPriority(Math.max(Thread.currentThread().getPriority() - 1, Thread.MIN_PRIORITY)); // lower than background priority
				return peerThread;
			}
		});

		// need this to cancel what takes too long
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		// this thread waits on the selector above and acts on events
		peerThreads.execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						ChangeRequest cr;
						while ( (cr = selectorChanges.poll()) != null )
						{
							if ( !cr.socket.isOpen() )
								continue;
							if ( cr.type == ChangeRequest.REGISTER )
								cr.socket.register(selector, cr.ops);
							else if ( cr.type == ChangeRequest.CHANGEOPS )
							{
								SelectionKey key = cr.socket.keyFor(selector);
								key.interestOps(cr.ops);
							}
						}						
						selector.select(); // wait for events
						
						Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
						while (keys.hasNext()) { // for all events
							SelectionKey key = keys.next(); // take
							keys.remove(); // mark as handled 
							
							if (key.isAcceptable()) {
								// unsolicited request to connect
								final SocketChannel client = ((ServerSocketChannel) key.channel()).accept(); // accept client
								if ( unsolicitedCounter.incrementAndGet() <= UNSOLICITED ) {
									// if we have interest ...
									client.configureBlocking(false); // switch to non-blocking
									final SelectionKey saywhat = client.register(selector, SelectionKey.OP_READ); // let them tell what they want									
									Peer peer;
									if (!knownPeers.containsKey(client)) { // never seen before ?
										peer = knownPeers.get(client);
										if (peer.getChannel() instanceof AvailableChannel) // but were to connect ?
											peer = new Peer(client);  // create new client with real channel
									}
									else
										peer = new Peer(client); // create new client with real channel
									peer.setUnsolicited (true); // note that this was unsolicited
									connectedPeers.put(client, peer); // keep track

									log.info("Unsolicited connection to  " + client);
									
									final Peer rememberPeer = peer;
									scheduler.schedule(new Runnable (){
										@Override
										public void run() {
											saywhat.cancel();
											rememberPeer.disconnect(); // you had your chance but said nothing
										}}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
								} else
									client.close (); // no interest
							}
							if (key.isConnectable()) {
								// we asked for connection here
								if ( connectionsCounter.incrementAndGet() <= MINCONNECTIONS ) {
									// if we still have interest
									SocketChannel client = (SocketChannel) key.channel();
									client.finishConnect(); // finish protocol
									Peer peer;
									if (!knownPeers.containsKey(client)) { // see above
										peer = knownPeers.get(client);
										if (peer.getChannel() instanceof AvailableChannel)
											peer = new Peer(client);
									} else
										peer = new Peer(client);
									connectedPeers.put(client, peer);
									peer.introduce ();

									log.info("Connected to " + client);
								} else
									key.channel().close(); // no interest
							}
							if (key.isReadable()) {
								SocketChannel client = (SocketChannel) key.channel();
								final Peer peer = connectedPeers.get(client); // it must be connected ...
								if (peer != null) {
									peer.readWholeMessage();
								}
							}
							if (key.isWritable()) {
								SocketChannel client = (SocketChannel) key.channel();
								Peer peer = connectedPeers.get(client); // it must be connected ...
								if ( peer != null )
								{
									ByteBuffer b;
									if ( ( b = peer.getBuffer() )!= null )
										client.write(b);
									else
										key.interestOps(SelectionKey.OP_READ); // done with writes, only interested in reads
								}
							}
						}
					} catch (Exception e) {
						log.error("Unhandled Exception in selector thread", e);
					}
				}
			}
		});

		// create a server channel for the chain's port, work non-blocking and wait for accept events
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.socket().bind(new InetSocketAddress(chain.getPort()));
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		serverChannel.configureBlocking(false);

		// this thread keeps looking for new connections
		peerThreads.execute(new Runnable() {
			@Override
			public void run() {
				while (true) { // forever
					try {
						List<Peer> batch = new ArrayList<Peer>();
						if ( connectionsCounter.get() < MINCONNECTIONS ) { // do we need connections?
							runqueue.drainTo(batch, CONNECTBATCH); // get some from the waiting queue
							// for all in this batch
							for (Peer peer : batch) {
								// connect non-bloking manner, registering for connect notifications
								final SocketChannel channel = SocketChannel.open();
								channel.configureBlocking(false);
								final SelectionKey connectKey = channel.register(selector, SelectionKey.OP_CONNECT);
								log.info("Trying to connect " + channel);
								channel.connect(((AvailableChannel) peer.getChannel()).getSocketAddress());
								
								// schedule a cancel for all. Those not fast enough to open will be cancelled
								scheduler.schedule(new Runnable() {
									@Override
									public void run() {
										if (!channel.isOpen()) {
											connectKey.cancel(); // still not open, forget it.
											log.info("No answer from " + channel);
											try {
												channel.close();
											} catch (IOException e) {
											}
										}
									}
								}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
							}
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
