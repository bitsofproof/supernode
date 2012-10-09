package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.Block;

public interface Chain {

	public Block getGenesis();

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