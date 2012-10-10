package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;
import com.bitsofproof.supernode.model.Blk;



public class BlockMessage extends BitcoinPeer.Message  {

	public BlockMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("block");
	}

	private Blk block = new Blk ();
	
	public Blk getBlock() {
		return block;
	}

	public void setBlock(Blk block) {
		this.block = block;
	}

	@Override
	public void toWire(Writer writer) {
		block.toWire(writer);
	}

	@Override
	public void fromWire(Reader reader) {
		block.fromWire(reader);
	}
}
