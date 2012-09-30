package hu.blummers.bitcoin.core;


public interface WireType {
	public void toWire (WireFormat.Writer writer);
}
