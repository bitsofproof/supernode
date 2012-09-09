package hu.blummers.bitcoin.core;

public interface ChainStore {
	public BitcoinNetwork getNetwork ();
	public String getHead ();
}
