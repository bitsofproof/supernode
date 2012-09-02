package hu.blummers.bitcoin.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

import org.purser.server.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Peers {
	private static final Logger log = LoggerFactory.getLogger(Peers.class);

	private static final int CONNECTTMEOUT = 5 * 1000;

	public class Peer implements Comparable<Peer> {
		private final InetSocketAddress soa;
		private OutputStream out;
		private final Socket socket;
		private boolean unsolicited;
		private int trust = 0;

		public Peer(Socket socket) {
			this.soa = new InetSocketAddress(socket.getInetAddress(),
					socket.getPort());
			this.socket = socket;
			unsolicited = true;
		}

		public Peer(InetSocketAddress soa) {
			this.soa = soa;
			socket = new Socket();
			unsolicited = false;
		}

		@Override
		public boolean equals(Object obj) {
			return soa.equals(obj);
		}

		@Override
		public int hashCode() {
			return soa.hashCode();
		}

		private void listen() {
			try {
				socket.connect(soa, CONNECTTMEOUT);
				out = socket.getOutputStream();
				InputStream in = socket.getInputStream();

				// handshake...

				log.info("Connected to" + soa);
				connected.add(this);
				Message m;
				while ((m = Envelope.read(in, chain)) != null) {
					incoming.add(m);
				}
			} catch (ValidationException e) {
				log.debug("Invalid message from" + soa, e);
				decreaseTrust();
			} catch (IOException e) {
				log.debug("Can not connect to" + soa, e);
				decreaseTrust();
			} finally {
				disconnect();
			}
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
				log.info("Disconnecting a misbehaving peer " + soa);
				disconnect();
				if (!unsolicited) // do not forgive unsolicited
					trust = 0;
			}
		}

		public void disconnect() {
			log.info("Disconnected from " + soa);
			if (unsolicited)
				unsolicitedConnections.release();
			try {
				socket.close();
			} catch (IOException e) {
			}
		}

		public void send(Envelope m) {
			try {
				synchronized (out) {
					out.write(new byte[1]);
				}
			} catch (IOException e) {
				disconnect();
				decreaseTrust();
			}
		}

		@Override
		public int compareTo(Peer other) {
			return other.trust - trust;
		}
	}

	private Map<Peer, Peer> knownPeers = Collections
			.synchronizedMap(new HashMap<Peer, Peer>());
	private PriorityBlockingQueue<Peer> runqueue = new PriorityBlockingQueue<Peer>();
	private List<Peer> connected = Collections
			.synchronizedList(new LinkedList<Peer>());
	private LinkedBlockingQueue<Message> incoming = new LinkedBlockingQueue<Message>();

	private static final int MAXCONNECTIONS = 10;
	private static final int MIN_TRUST = -20;
	private static final int MAX_TRUST = 100;

	private final ExecutorService peerListener;
	private final ExecutorService peerSender;

	private final Thread serverThread;
	private final ServerSocket server;
	private final Semaphore unsolicitedConnections = new Semaphore(
			MAXCONNECTIONS / 2);
	private final ExecutorService unsolicitedListener;

	private final Chain chain;

	public void broadcast(final Envelope m) {
		synchronized (connected) {
			for (final Peer peer : connected)
				peerSender.execute(new Runnable() {
					@Override
					public void run() {
						peer.send(m);
					}
				});
		}
	}

	public Peers(Chain chain) throws IOException {
		this.chain = chain;
		server = new ServerSocket(chain.getPort());

		unsolicitedListener = Executors.newFixedThreadPool(MAXCONNECTIONS / 2,
				new ThreadFactory() {
					@Override
					public Thread newThread(final Runnable runnable) {
						Thread peerThread = new Thread() {
							@Override
							public void run() {
								runnable.run();
							}
						};
						peerThread.setDaemon(true);
						peerThread.setName("Unsolicited Listener");
						peerThread.setPriority(Thread.MIN_PRIORITY);
						return peerThread;
					}
				});

		serverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Socket clientSocket;
				try {
					unsolicitedConnections.acquireUninterruptibly();
					clientSocket = server.accept();
					do {
						Peer peer = new Peer(clientSocket);
						final Peer runPeer;
						synchronized (knownPeers) {
							Peer storedPeer = knownPeers.get(peer);
							if (storedPeer != null)
								runPeer = storedPeer;
							else {
								runPeer = peer;
								knownPeers.put(runPeer, runPeer);
							}
						}
						if (runPeer.getTrust() > MIN_TRUST) {
							unsolicitedListener.execute(new Runnable() {
								@Override
								public void run() {
									runPeer.listen();
								}
							});
						} else {
							clientSocket.close();
						}
					} while ((clientSocket = server.accept()) != null);
				} catch (IOException e) {
					log.error("Exception in Server thread", e);
				}
			}
		});
		serverThread.setName("Peer Server");
		serverThread.setDaemon(true);

		peerListener = Executors.newFixedThreadPool(MAXCONNECTIONS,
				new ThreadFactory() {

					@Override
					public Thread newThread(final Runnable runnable) {
						Thread peerThread = new Thread() {
							@Override
							public void run() {
								runnable.run();
							}
						};
						peerThread.setDaemon(true);
						peerThread.setName("Listener");
						return peerThread;
					}
				});
		peerSender = Executors.newFixedThreadPool(MAXCONNECTIONS,
				new ThreadFactory() {

					@Override
					public Thread newThread(final Runnable runnable) {
						Thread peerThread = new Thread() {
							@Override
							public void run() {
								runnable.run();
							}
						};
						peerThread.setDaemon(true);
						peerThread.setName("Sender");
						return peerThread;
					}
				});
	}

	public void addPeer(InetAddress addr, int port) {
		Peer peer = new Peer(new InetSocketAddress(addr, port));
		synchronized (knownPeers) {
			Peer storedPeer = knownPeers.get(peer);
			if (storedPeer == null) {
				knownPeers.put(peer, peer);
			}
		}
		runqueue.add(peer);
	}

	public void discover(Chain chain) {
		for (String hostName : chain.getSeedHosts()) {
			try {
				InetAddress[] hostAddresses = InetAddress
						.getAllByName(hostName);

				for (InetAddress inetAddress : hostAddresses) {
					addPeer(inetAddress, chain.getPort());
				}
			} catch (Exception e) {
				log.info("DNS lookup for " + hostName + " failed.");
			}
		}
	}

	public void listen() {
		for (int i = 0; i < MAXCONNECTIONS; ++i)
			peerListener.execute(new Runnable() {
				@Override
				public void run() {
					try {
						Peer peer;
						while (true) {
							peer = runqueue.take();
							runqueue.add(peer);
						}
					} catch (Exception e) {
						log.error("Exception in a peer listener thread", e);
					}
				}
			});

		serverThread.start();

		Message m;
		try {
			while ((m = incoming.take()) != null)
				;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
		}
	}
}
