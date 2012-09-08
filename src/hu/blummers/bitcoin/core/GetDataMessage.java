package hu.blummers.bitcoin.core;

public class GetDataMessage extends BitcoinMessage {

	public GetDataMessage(Chain chain, String command) {
		super(chain, command);
	}

}
