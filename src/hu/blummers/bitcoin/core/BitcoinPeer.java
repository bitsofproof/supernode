package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.messages.AddrMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.BlockMessage;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetDataMessage;
import hu.blummers.bitcoin.messages.InvMessage;
import hu.blummers.bitcoin.messages.VersionMessage;
import hu.blummers.p2p.P2P;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

public class BitcoinPeer extends P2P.Peer {
	private static final Logger log = LoggerFactory.getLogger(BitcoinPeer.class);

	private final TransactionTemplate transactionTemplate;
	private BitcoinNetwork network;
	
	private String agent;
	private long height;
	private long peerVersion;
	
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static final long CONNECTIONTIMEOUT = 30;
	
	public class Message implements P2P.Message {
		private final String command;
		private long version;
		
		public Message (String command)
		{
			this.command = command;
			version = peerVersion;
		}
		
		
		public long getVersion() {
			return version;
		}


		public void setVersion(long version) {
			this.version = version;
		}

		public String getCommand () {
			return command;
		}

		@Override
		public byte [] toByteArray ()
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			WireFormat.Writer writer = new WireFormat.Writer(out);
			writer.writeUint32(network.getChain().getMagic());
			writer.writeZeroDelimitedString(getCommand(), 12);
			WireFormat.Writer payload = new WireFormat.Writer(new ByteArrayOutputStream());
			toWire(payload);
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
			return writer.toByteArray();
		}
		
		public void validate () throws ValidationException {}
		public void toWire (WireFormat.Writer writer) {};
		public void fromWire (WireFormat.Reader reader) {};
	}

	
	public Message createMessage (String command)
	{
		if ( command.equals("version") )
			return new VersionMessage (this);
		else if ( command.equals("inv") )
			return new InvMessage (this);
		else if ( command.equals("addr") )
			return new AddrMessage (this);
		else if ( command.equals("getdata") )
			return new GetDataMessage(this);
		else if ( command.equals("getblocks") )
			return new GetBlocksMessage (this);
		else if ( command.equals("block") )
			return new BlockMessage (this);

		return new Message (command);
	}
	
	public Map<String, ArrayList<BitcoinMessageListener>> listener = Collections.synchronizedMap(new HashMap<String, ArrayList<BitcoinMessageListener>> ());

	public BitcoinPeer(P2P p2p, TransactionTemplate transactionTemplate, InetSocketAddress address) {
		p2p.super(address);
		network = (BitcoinNetwork)p2p;
		this.transactionTemplate = transactionTemplate;
		
		// this will be overwritten by the first version message we get
		peerVersion = network.getChain().getVersion(); 
		
		addListener("version", new BitcoinMessageListener () {
			public void process(BitcoinPeer.Message m, BitcoinPeer peer) {
				VersionMessage v = (VersionMessage)m;
				agent = v.getAgent();
				height = v.getHeight();
				peerVersion = v.getVersion();
				peer.send (peer.createMessage("verack"));
				network.addPeer(peer);
				network.notifyPeerAdded(peer);
				log.info("Connection to '" + getAgent () + "' at " + getAddress() + " Open connections: " + getNetwork().getNumberOfConnections());
			}});
		
		addListener("verack", new BitcoinMessageListener () {
			public void process(BitcoinPeer.Message m, BitcoinPeer peer) {
			}});
	}

	public BitcoinNetwork getNetwork() {
		return network;
	}

	public void setNetwork(BitcoinNetwork network) {
		this.network = network;
	}

	public long getVersion ()
	{
		return peerVersion;
	}
	
	public long getHeight ()
	{
		return height;
	}
	public String getAgent() {
		return agent;
	}

	@Override
	public void send(final P2P.Message m) {
		super.send(m);
	}

	@Override
	public void onDisconnect() {
		network.notifyPeerRemoved(this);
		log.info("Disconnected '" + getAgent () + "' at " + getAddress() + ". Open connections: " + getNetwork().getNumberOfConnections());
	}

    public static final int MAX_SIZE = 0x02000000;

    @Override
	public Message parse(InputStream readIn) throws IOException {
		try {
			byte[] head = new byte[24];
			if (readIn.read(head) != head.length)
				throw new ValidationException("Invalid package header");
			WireFormat.Reader reader = new WireFormat.Reader(head);
			long mag = reader.readUint32();
			if (mag != network.getChain().getMagic())
				throw new ValidationException("Wrong magic for this chain");

			String command = reader.readZeroDelimitedString(12);
			Message m = createMessage(command);
			long length = reader.readUint32();
			byte[] checksum = reader.readBytes(4);
			if (length > 0 && length < MAX_SIZE) {
				byte[] buf = new byte[(int) length];
				if (readIn.read(buf) != buf.length)
					throw new ValidationException("Package length mismatch");
				byte[] cs = new byte[4];
				MessageDigest sha;
				try {
					sha = MessageDigest.getInstance("SHA-256");
					System.arraycopy(sha.digest(sha.digest(buf)), 0, cs, 0, 4);
				} catch (NoSuchAlgorithmException e) {
				}
				if (!Arrays.equals(cs, checksum))
					throw new ValidationException("Checksum mismatch");

				if (m != null) {
					m.fromWire(new WireFormat.Reader(buf));
				}
			}
			return m;
		} catch (Exception e) {
			log.error("Exception reading message", e);
			disconnect();
		}
		return null;
	}

	@Override
	public void onConnect() {
		VersionMessage m = (VersionMessage) createMessage("version");
		m.setHeight(network.getChainHeight());
		m.setPeer(getAddress().getAddress());
		m.setRemotePort(getAddress().getPort());
		send(m);
		final BitcoinPeer peer = this;
		scheduler.schedule(new Runnable (){
			@Override
			public void run() {
				if ( !network.isConnected(peer) )
					peer.disconnect();
				}
			}, CONNECTIONTIMEOUT,TimeUnit.SECONDS);
	}

	@Override
	public void receive(P2P.Message m) {
		final BitcoinPeer self = this;
		final Message bm = (Message) m;
			transactionTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus arg0) {
					try {
						bm.validate ();
	
						List<BitcoinMessageListener> classListener = listener.get(bm.getCommand());
						if ( classListener != null )
							for ( BitcoinMessageListener l : classListener )
								l.process(bm, self);
						
					} catch (ValidationException e) {
						log.error("Invalid message ", e);
						disconnect ();
					}
				}
			});
	}
	
	public void addListener (String type, BitcoinMessageListener l)
	{
		ArrayList<BitcoinMessageListener> ll = listener.get(type);
		if ( ll == null )
		{
			ll = new ArrayList<BitcoinMessageListener> ();
			listener.put(type, ll);
		}
		if ( !ll.contains(l) )
			ll.add(l);
	}

	public void removeListener (String type, BitcoinMessageListener l)
	{
		ArrayList<BitcoinMessageListener> ll = listener.get(type);
		if ( ll != null )
		{
			ll.remove(l);
		}
	}
}
