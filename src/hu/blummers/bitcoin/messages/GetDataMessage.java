package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.Chain;

public class GetDataMessage extends BitcoinMessage {

	public GetDataMessage(Chain chain) {
		super(chain, "getdata");
	}

}
