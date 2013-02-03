package com.bitsofproof.supernode.core;

import java.math.BigInteger;

import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class BlocktesterChain implements Chain
{
	@Override
	public BigInteger getMinimumTarget ()
	{
		return BigInteger.valueOf (1).shiftLeft (256).subtract (BigInteger.ONE);
	}

	@Override
	public long getRewardForHeight (int height)
	{
		return 5000000000L;
	}

	@Override
	public int getDifficultyReviewBlocks ()
	{
		return 2016;
	}

	@Override
	public int getTargetBlockTime ()
	{
		return 1209600;
	}

	@Override
	public boolean isProduction ()
	{
		return true;
	}

	@Override
	public int getAddressFlag ()
	{
		return 0;
	}

	@Override
	public int getMultisigAddressFlag ()
	{
		return 0;
	}

	@Override
	public Blk getGenesis ()
	{
		return deserializeBlock ("0100000063cc6100f4019ff178616e26111e076eae72079dce6917ce8a539aeac35b6a106ebe106afb6bf000471916c4826d42e66ed416cfeda9b63951448c75e79bd625d4ccf250ffff7f20030000000101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff020001ffffffff0100f2052a010000002321029a49f2c7aec9d94e9423feda0e4b528eb045cd2e3f91810bdebe8aea0b34769bac00000000");
	}

	private Blk deserializeBlock (String s)
	{
		final Blk gb = Blk.fromWireDump (s);
		gb.parseTransactions ();
		gb.computeHash ();
		for ( Tx t : gb.getTransactions () )
		{
			t.setBlock (gb);
			for ( TxOut out : t.getOutputs () )
			{
				out.setTransaction (t);
			}
			for ( TxIn in : t.getInputs () )
			{
				in.setTransaction (t);
			}
		}
		return gb;
	}

	@Override
	public long getMagic ()
	{
		return 0xDAB5BFFAL;
	}

	@Override
	public int getPort ()
	{
		return 8333;
	}

	@Override
	public byte[] getAlertKey ()
	{
		return null;
	}

	@Override
	public long getVersion ()
	{
		return 1;
	}

	@Override
	public boolean isUnitTest ()
	{
		return true;
	}
}
