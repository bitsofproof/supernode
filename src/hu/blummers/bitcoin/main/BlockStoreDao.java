package hu.blummers.bitcoin.main;
import hu.blummers.bitcoin.core.Chain;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

public interface BlockStoreDao extends BlockStore {
	public void setNetworkParams (NetworkParameters params);
	public void setChain (Chain chain);
	public void resetStore() throws BlockStoreException;
}
