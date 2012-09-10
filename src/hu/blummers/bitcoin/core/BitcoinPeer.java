package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.messages.AddrMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.BlockMessage;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetDataMessage;
import hu.blummers.bitcoin.messages.InvMessage;
import hu.blummers.bitcoin.messages.VersionMessage;
import hu.blummers.p2p.P2P;
import hu.blummers.p2p.P2P.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Arrays;
import edu.emory.mathcs.backport.java.util.Collections;

public class BitcoinPeer extends P2P.Peer {
	private static final Logger log = LoggerFactory.getLogger(BitcoinPeer.class);

	private BitcoinNetwork network;
	private String agent;
	private long height;
	private long version;
	private boolean ready = false;
	
	public class Message implements P2P.Message {
		private final String command;
		
		public Message (String command)
		{
			this.command = command;
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
	
	@SuppressWarnings("unchecked")
	public Map<String, List<BitcoinMessageListener>>listener = Collections.synchronizedMap(new HashMap<String, ArrayList<BitcoinMessageListener>> ());

	public BitcoinPeer(P2P p2p, InetSocketAddress address) {
		p2p.super(address);
		network = (BitcoinNetwork)p2p;
		
		// this will be overwritten by the first version message we get
		version = network.getChain().getVersion(); 
		
		addListener("version", new BitcoinMessageListener () {
			public void process(BitcoinPeer.Message m, BitcoinPeer peer) {
				VersionMessage v = (VersionMessage)m;
				agent = v.getAgent();
				height = v.getHeight();
				version = v.getVersion();
				log.info("connected to " +v.getAgent());
				peer.send (peer.createMessage("verack"));
			}});
		
		addListener("verack", new BitcoinMessageListener () {
			public void process(BitcoinPeer.Message m, BitcoinPeer peer) {
				ready = true;
				log.info("Connection to " + getAgent () + " acknowledged");
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
		return version;
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
		try {
			m.setHeight(network.getStore().get(network.getStore().getHeadHash()).getHeight());
			m.setPeer(getAddress().getAddress());
			m.setRemotePort(getAddress().getPort());
			send(m);
		} catch (ChainStoreException e) {
			log.error("Error accessing chain store", e);
		}
	}

	@Override
	public void receive(P2P.Message m) {
		Message bm = (Message) m;
		try {
			bm.validate ();
			
			List<BitcoinMessageListener> classListener = listener.get(bm.getCommand());
			if ( classListener != null )
				for ( BitcoinMessageListener l : classListener )
					l.process(bm, this);
			
		} catch (ValidationException e) {
			log.error("Invalid message ", e);
			disconnect ();
		}
	}
	
	public void addListener (String type, BitcoinMessageListener l)
	{
		List<BitcoinMessageListener> ll = listener.get(type);
		if ( ll == null )
		{
			ll = new ArrayList<BitcoinMessageListener> ();
			listener.put(type, ll);
		}
		if ( !ll.contains(l) )
			ll.add(l);
	}

}
