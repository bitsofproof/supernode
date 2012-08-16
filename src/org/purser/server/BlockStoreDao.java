package org.purser.server;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

public interface BlockStoreDao extends BlockStore {
	public void setNetworkParams (NetworkParameters params);
	public StoredBlock resetStore() throws BlockStoreException;
}
