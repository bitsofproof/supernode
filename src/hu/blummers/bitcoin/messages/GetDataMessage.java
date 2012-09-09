package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;

public class GetDataMessage extends BitcoinPeer.Message {

	public GetDataMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("getdata");
	}

}
