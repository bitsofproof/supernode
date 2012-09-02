package hu.blummers.bitcoin.core;

import org.purser.server.ValidationException;

public interface Message {
	public String getCommand ();	
	public void toWire (WireFormat.Writer writer);
	public void fromWire (WireFormat.Reader reader);
	public void validate (Chain chain) throws ValidationException;
}
