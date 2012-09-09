package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.Chain;

public class AlertMessage extends BitcoinMessage {

	public AlertMessage(Chain chain) {
		super(chain, "alert");
	}

}
