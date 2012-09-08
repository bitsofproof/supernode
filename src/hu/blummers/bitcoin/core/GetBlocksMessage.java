package hu.blummers.bitcoin.core;

import java.util.ArrayList;
import java.util.List;

import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

public class GetBlocksMessage extends BitcoinMessage {
	private long version = 31800;
	private List<String> hashes = new ArrayList<String>();
	
	public GetBlocksMessage(Chain chain) {
		super(chain, "getblocks");
	}

	@Override
	public void toWire(Writer writer) {
		writer.writeUint32(version);
		writer.writeVarInt(hashes.size());
		for ( String h : hashes )
			writer.writeHash(new Hash (h));
		writer.writeBytes(new byte [32]);
	}

	@Override
	public void fromWire(Reader reader, long version) {
		version = reader.readUint32();
		long n = reader.readVarInt();
		for ( long i = 0; i < n; ++i )
		{
			hashes.add(reader.readHash().toString());
		}
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public List<String> getHashes() {
		return hashes;
	}

	public void setHashes(List<String> hashes) {
		this.hashes = hashes;
	}
	
	
}
