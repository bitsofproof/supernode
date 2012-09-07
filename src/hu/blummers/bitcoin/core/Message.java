package hu.blummers.bitcoin.core;

import org.purser.server.ValidationException;

public class Message {
	private String command;
	private Chain chain;
	
	public Message (Chain chain, String command)
	{
		this.chain = chain;
		this.command = command;
	}
	
	public String getCommand () {
		return command;
	}
	public Chain getChain() {
		return chain;
	}

	public void toWire (WireFormat.Writer writer) {}
	public void fromWire (WireFormat.Reader reader) {} 
	public void validate (Chain chain) throws ValidationException {}
}
