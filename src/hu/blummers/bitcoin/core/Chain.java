package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.model.JpaBlock;

public interface Chain {

	public JpaBlock getGenesis();

	public long getMagic();

	public int getPort();

	public int getAddressType();

	public int getPrivateKeyType();

	public int getDifficultyReviewBlocks();

	public int getTargetBlockTime();

	public byte[] getAlertKey();

	public String[] getSeedHosts();

	public long getVersion();

}