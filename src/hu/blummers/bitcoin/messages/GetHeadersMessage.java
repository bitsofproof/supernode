package hu.blummers.bitcoin.messages;

import java.util.ArrayList;
import java.util.List;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

public class GetHeadersMessage  extends BitcoinPeer.Message {

	List<byte[]> locators = new ArrayList<byte[]>();
	byte [] stop = new byte [32];

	public GetHeadersMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("getheaders");
	}

	@Override
	public void toWire(Writer writer) {
		writer.writeUint32(getVersion());
		writer.writeVarInt(locators.size());
		for ( byte [] l : locators )
			writer.writeBytes(l);
		writer.writeBytes(stop);
	}

	@Override
	public void fromWire(Reader reader) {
		setVersion (reader.readUint32());
		long n = reader.readVarInt();
		for ( long i = 0; i < n; ++i )
			locators.add(reader.readBytes(32));
		stop = reader.readBytes(32);
	}

	public List<byte[]> getLocators() {
		return locators;
	}

	public void setLocators(List<byte[]> locators) {
		this.locators = locators;
	}

	public byte[] getStop() {
		return stop;
	}

	public void setStop(byte[] stop) {
		this.stop = stop;
	}
}
