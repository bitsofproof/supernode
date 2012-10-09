package com.bitsofproof.supernode.messages;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;


public class GetBlocksMessage extends BitcoinPeer.Message {
	
	public GetBlocksMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("getblocks");
	}

	private List<byte []> hashes = new ArrayList<byte []>();
	private byte [] lastHash = new byte [32];
	
	@Override
	public void toWire(Writer writer) {
		writer.writeUint32(getVersion ());
		writer.writeVarInt(hashes.size());
		for ( byte [] h : hashes )
			writer.writeBytes(h);
		writer.writeBytes(new byte [32]);
	}

	@Override
	public void fromWire(Reader reader) {
		setVersion (reader.readUint32());
		long n = reader.readVarInt();
		for ( long i = 0; i < n; ++i )
		{
			hashes.add(reader.readBytes(32));
		}
	}

	public List<byte []> getHashes() {
		return hashes;
	}

	public void setHashes(List<byte []> hashes) {
		this.hashes = hashes;
	}

	public byte[] getLastHash() {
		return lastHash;
	}

	public void setLastHash(byte[] lastHash) {
		this.lastHash = lastHash;
	}
	
}
