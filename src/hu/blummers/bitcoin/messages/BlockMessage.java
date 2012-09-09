package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.WireFormat;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;
import hu.blummers.bitcoin.jpa.JpaBlock;


public class BlockMessage extends BitcoinMessage {

	private JpaBlock block;
	
	public BlockMessage(Chain chain) {
		super(chain, "block");
	}

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
	public void fromWire(Reader reader, long version) {
		block.fromWire(reader);
	}
}
