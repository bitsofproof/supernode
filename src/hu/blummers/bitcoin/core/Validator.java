package hu.blummers.bitcoin.core;


public interface Validator {
	void validate (ChainStore store);
}
