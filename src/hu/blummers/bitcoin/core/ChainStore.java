package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.jpa.JpaBlock;

public interface ChainStore {
	
	public void resetStore (Chain chain) throws ChainStoreException;
	public String getHead ()  throws ChainStoreException;
	public void store (JpaBlock block)  throws ChainStoreException;
	public JpaBlock get (String hash)  throws ChainStoreException;
	public JpaBlock getPrevious (JpaBlock block)  throws ChainStoreException;
}
