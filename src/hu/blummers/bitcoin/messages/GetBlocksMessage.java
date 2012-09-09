package hu.blummers.bitcoin.messages;

import java.util.ArrayList;
import java.util.List;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.WireFormat;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

public class GetBlocksMessage extends BitcoinPeer.Message {
	long version;
	
	public GetBlocksMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("getblocks");
		version = bitcoinPeer.getVersion();
	}

	private List<String> hashes = new ArrayList<String>();
	
	@Override
	public void toWire(Writer writer) {
		writer.writeUint32(version);
		writer.writeVarInt(hashes.size());
		for ( String h : hashes )
			writer.writeHash(new Hash (h));
		writer.writeBytes(new byte [32]);
	}

	@Override
	public void fromWire(Reader reader) {
		version = reader.readUint32();
		long n = reader.readVarInt();
		for ( long i = 0; i < n; ++i )
		{
			hashes.add(reader.readHash().toString());
		}
	}

	public List<String> getHashes() {
		return hashes;
	}

	public void setHashes(List<String> hashes) {
		this.hashes = hashes;
	}
}
