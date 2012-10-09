package com.bitsofproof.supernode.core;


import java.math.BigInteger;

import com.bitsofproof.supernode.model.Block;


public abstract class ChainImpl implements Chain {
	
	private final long version;
	private final long magic;
	private final int port;
	private final int addressType;
	private final int privateKeyType;
	private final int difficultyReviewBlocks;
	private final int targetBlockTime;
	private final byte[] alertKey;
	private final String[] seedHosts; 
	
	public static BigInteger difficultyToNumber(long difficulty) {
		int size = ((int) (difficulty >> 24)) & 0xFF;
		byte[] b = new byte[size+4];
		b[3] = (byte) size;
		if (size >= 1)
			b[4] = (byte) ((difficulty >> 16) & 0xFF);
		if (size >= 2)
			b[5] = (byte) ((difficulty >> 8) & 0xFF);
		if (size >= 3)
			b[6] = (byte) ((difficulty >> 0) & 0xFF);
		WireFormat.Reader reader = new WireFormat.Reader(b);
		int length = (int) reader.readUint32();
		byte[] buf = new byte[length];
		System.arraycopy(b, 4, buf, 0, length);
		return new BigInteger(buf);
	}

	public ChainImpl(long version, long magic,
			int port, int addressType, int privateKeyType,
			int difficultyReviewBlocks, int targetBlockTime, byte[] alertKey, String [] seedHosts) {
		this.version = version;
		this.magic = magic;
		this.port = port;
		this.addressType = addressType;
		this.privateKeyType = privateKeyType;
		this.difficultyReviewBlocks = difficultyReviewBlocks;
		this.targetBlockTime = targetBlockTime;
		this.alertKey = alertKey;
		this.seedHosts = seedHosts;
	}
	
	public abstract Block getGenesis ();

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getMagic()
	 */
	public long getMagic() {
		return magic;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getPort()
	 */
	public int getPort() {
		return port;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getAddressType()
	 */
	public int getAddressType() {
		return addressType;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getPrivateKeyType()
	 */
	public int getPrivateKeyType() {
		return privateKeyType;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getDifficultyReviewBlocks()
	 */
	public int getDifficultyReviewBlocks() {
		return difficultyReviewBlocks;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getTargetBlockTime()
	 */
	public int getTargetBlockTime() {
		return targetBlockTime;
	}

	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getAlertKey()
	 */
	public byte[] getAlertKey() {
		return alertKey;
	}


	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getSeedHosts()
	 */
	public String[] getSeedHosts() {
		return seedHosts;
	}


	/* (non-Javadoc)
	 * @see hu.blummers.bitcoin.core.IFChain#getVersion()
	 */
	public long getVersion() {
		return version;
	}
	
	
}
