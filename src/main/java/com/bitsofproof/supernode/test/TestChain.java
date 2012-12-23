package com.bitsofproof.supernode.test;

import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;

import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class TestChain implements Chain
{

	public static final byte[] SATOSHI_KEY = Hex
			.decode ("04fc9702847840aaf195de8442ebecedf5b095cdbb9bc716bda9110971b28a49e0ead8564ff0db22209e0374782c093bb899692d524e9d6a6956e7c5ecbcd68284");

	@Override
	public long getMagic ()
	{
		return 0x0709110bL;
	}

	@Override
	public int getPort ()
	{
		return 18333;
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
	public long getRewardForHeight (int height)
	{
		return (50L * Tx.COIN) >> (height / 210000L);
	}

	@Override
	public byte[] getAlertKey ()
	{
		return SATOSHI_KEY;
	}

	@Override
	public long getVersion ()
	{
		return 60001;
	}

	@Override
	public boolean isProduction ()
	{
		return false;
	}

	@Override
	public int getAddressFlag ()
	{
		return 0x6F;
	}

	@Override
	public int getMultisigAddressFlag ()
	{
		return 0xc4;
	}

	@Override
	public int getValidateFrom ()
	{
		return 0;
	}

	@Override
	public Blk getGenesis ()
	{
		Blk block = new Blk ();

		block.setChainWork (1);
		block.setHeight (0);

		block.setVersion (1);
		block.setCreateTime (1296688602L);
		block.setDifficultyTarget (0x1d00ffffL);
		block.setNonce (414098458L);
		block.setPreviousHash (Hash.ZERO_HASH_STRING);

		List<Tx> transactions = new ArrayList<Tx> ();
		block.setTransactions (transactions);
		Tx t = new Tx ();
		transactions.add (t);
		t.setBlock (block);
		t.setVersion (1);

		List<TxIn> inputs = new ArrayList<TxIn> ();
		t.setInputs (inputs);
		TxIn input = new TxIn ();
		input.setTransaction (t);
		inputs.add (input);
		input.setSource (null);
		input.setSourceHash (Hash.ZERO_HASH_STRING);
		input.setIx (0L);
		input.setSequence (0xFFFFFFFFL);
		input.setScript (ByteUtils.fromHex ("04" + "ffff001d" + // difficulty target
				"010445" +
				// "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
				"5468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73"));

		List<TxOut> outputs = new ArrayList<TxOut> ();
		t.setOutputs (outputs);
		TxOut output = new TxOut ();
		output.setTransaction (t);
		outputs.add (output);
		output.setValue (5000000000L);
		output.setScript (Hex
				.decode ("4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac"));
		output.setTxHash (t.getHash ());
		output.setHeight (0);
		block.computeHash ();
		return block;
	}
}
