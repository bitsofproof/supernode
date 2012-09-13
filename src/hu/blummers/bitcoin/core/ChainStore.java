package hu.blummers.bitcoin.core;

import java.util.List;

import hu.blummers.bitcoin.jpa.JpaBlock;
import hu.blummers.bitcoin.jpa.JpaTransaction;

public interface ChainStore {
	
	public void resetStore (Chain chain) throws ChainStoreException;
	public String getHeadHash ()  throws ChainStoreException;	
	public boolean store (JpaBlock block)  throws ChainStoreException;	
	public void store (JpaTransaction transaction)  throws ChainStoreException;	
	public JpaBlock get (String hash)  throws ChainStoreException;
	public List<JpaTransaction> getTransactions (String hash) throws ChainStoreException;
}
