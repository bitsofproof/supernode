package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;
import hu.blummers.bitcoin.jpa.JpaBlock;


public class BlockMessage extends BitcoinPeer.Message  {

	public BlockMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("block");
	}

	private JpaBlock block;
	
	public JpaBlock getBlock() {
		return block;
	}

	public void setBlock(JpaBlock block) {
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
