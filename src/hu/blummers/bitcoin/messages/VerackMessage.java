package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.Chain;

public class VerackMessage extends BitcoinMessage {

	public VerackMessage(Chain chain) {
		super(chain, "ack");
	}

}
