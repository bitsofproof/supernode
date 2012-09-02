package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

import org.purser.server.ValidationException;

public class VersionMessage implements Message 
{
	@Override
	public void toWire(Writer writer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fromWire(Reader reader) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void validate(Chain chain) throws ValidationException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getCommand() {
		return "version";
	}

}
