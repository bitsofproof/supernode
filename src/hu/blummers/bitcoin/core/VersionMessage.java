package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import org.purser.server.ValidationException;

public class VersionMessage extends BitcoinMessage {
	private long version = 31800;
	private BigInteger services = new BigInteger ("1");
	private BigInteger timestamp = new BigInteger (new Long (System.currentTimeMillis()/1000).toString());
	private InetAddress peer;
	private long remotePort;
	private InetAddress me;
	private BigInteger nonce = new BigInteger (64, new Random ());
	private String agent = "Guess 0.1";
	private long height = 0;

	
	@Override
	public void toWire(Writer writer) {
		writer.writeUint32(version);
		writer.writeUint64(services);
		writer.writeUint64(timestamp);
		writer.writeUint64(services);
		WireFormat.Address a = new WireFormat.Address();
		a.address = peer;
		a.port = remotePort;
		writer.writeAddress(a);
		try {
			writer.writeUint64(services);
			a.address = InetAddress.getLocalHost();
			a.port = getChain ().getPort();
			writer.writeAddress(a);
		} catch (UnknownHostException e) {
		}
		writer.writeUint64(nonce);
		writer.writeString(agent);
		writer.writeUint32(height);
	}
	
	@Override
	public void fromWire(Reader reader) {		
		version = reader.readUint32();
		services = reader.readUint64();
		timestamp = reader.readUint64();
		reader.readUint64();
		WireFormat.Address address = reader.readAddress(); // should be me
		reader.readUint64();
		address = reader.readAddress();
		peer = address.address;
		remotePort = address.port;		
		reader.readUint64();
		agent = reader.readString();
		height = reader.readUint32();
	}
	
	public VersionMessage(Chain chain, String command) {
		super(chain, command);
	}
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	public BigInteger getServices() {
		return services;
	}
	public void setServices(BigInteger services) {
		this.services = services;
	}
	public InetAddress getPeer() {
		return peer;
	}
	public void setPeer(InetAddress peer) {
		this.peer = peer;
	}
	public InetAddress getMe() {
		return me;
	}
	public void setMe(InetAddress me) {
		this.me = me;
	}
	public BigInteger getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(BigInteger timestamp) {
		this.timestamp = timestamp;
	}
	public BigInteger getNonce() {
		return nonce;
	}
	public void setNonce(BigInteger nounce) {
		this.nonce = nounce;
	}
	public String getAgent() {
		return agent;
	}
	public void setAgent(String agent) {
		this.agent = agent;
	}
	public long getHeight() {
		return height;
	}
	public void setHeight(long height) {
		this.height = height;
	}
	public long getRemotePort() {
		return remotePort;
	}
	public void setRemotePort(long remotePort) {
		this.remotePort = remotePort;
	}
	
}
