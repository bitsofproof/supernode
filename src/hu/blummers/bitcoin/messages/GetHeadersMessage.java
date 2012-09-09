package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

public class GetHeadersMessage  extends BitcoinPeer.Message {


	public GetHeadersMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("getheaders");
	}

	@Override
	public void toWire(Writer writer) {
	}

	@Override
	public void fromWire(Reader reader) {
	}

}
