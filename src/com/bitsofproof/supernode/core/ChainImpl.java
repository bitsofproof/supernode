package com.bitsofproof.supernode.core;

import java.math.BigInteger;

import com.bitsofproof.supernode.model.Blk;

public abstract class ChainImpl implements Chain
{

	private final long version;
	private final long magic;
	private final int port;
	private final int addressType;
	private final int privateKeyType;
	private final int difficultyReviewBlocks;
	private final int targetBlockTime;
	private final byte[] alertKey;
	private final String[] seedHosts;

	public static BigInteger difficultyToNumber (long difficulty)
	{
		int size = ((int) (difficulty >> 24)) & 0xFF;
		byte[] b = new byte[size + 4];
		b[3] = (byte) size;
		if ( size >= 1 )
		{
			b[4] = (byte) ((difficulty >> 16) & 0xFF);
		}
		if ( size >= 2 )
		{
			b[5] = (byte) ((difficulty >> 8) & 0xFF);
		}
		if ( size >= 3 )
		{
			b[6] = (byte) ((difficulty >> 0) & 0xFF);
		}
		WireFormat.Reader reader = new WireFormat.Reader (b);
		int length = (int) reader.readUint32 ();
		byte[] buf = new byte[length];
		System.arraycopy (b, 4, buf, 0, length);
		return new BigInteger (buf);
	}

	public ChainImpl (long version, long magic, int port, int addressType, int privateKeyType, int difficultyReviewBlocks, int targetBlockTime,
			byte[] alertKey, String[] seedHosts)
	{
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

	@Override
	public abstract Blk getGenesis ();

	@Override
	public long getMagic ()
	{
		return magic;
	}

	@Override
	public int getPort ()
	{
		return port;
	}

	@Override
	public int getAddressType ()
	{
		return addressType;
	}

	@Override
	public int getPrivateKeyType ()
	{
		return privateKeyType;
	}

	@Override
	public int getDifficultyReviewBlocks ()
	{
		return difficultyReviewBlocks;
	}

	@Override
	public int getTargetBlockTime ()
	{
		return targetBlockTime;
	}

	@Override
	public byte[] getAlertKey ()
	{
		return alertKey;
	}

	@Override
	public String[] getSeedHosts ()
	{
		return seedHosts;
	}

	@Override
	public long getVersion ()
	{
		return version;
	}

}
