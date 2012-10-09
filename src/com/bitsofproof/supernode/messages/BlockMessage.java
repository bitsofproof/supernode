package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;
import com.bitsofproof.supernode.model.Block;



public class BlockMessage extends BitcoinPeer.Message  {

	public BlockMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("block");
	}

	private Block block = new Block ();
	
	public Block getBlock() {
		return block;
	}

	public void setBlock(Block block) {
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
