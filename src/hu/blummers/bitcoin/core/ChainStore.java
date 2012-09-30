package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.model.JpaBlock;
import hu.blummers.bitcoin.model.JpaTransaction;

import java.util.List;

public interface ChainStore {

	public int getChainHeight ();
	public void resetStore (Chain chain);
	public String getHeadHash ();	
	public void store (JpaBlock block) throws ChainStoreException;	
	public void store (JpaTransaction transaction);	
	public JpaBlock get (String hash);
	public List<JpaTransaction> getTransactions (String hash);
}
