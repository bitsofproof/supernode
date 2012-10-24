package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

@Component ("satoshiChain")
public class SatoshiChain extends ChainImpl
{

	public static final byte[] SATOSHI_KEY = Hex
			.decode ("04fc9702847840aaf195de8442ebecedf5b095cdbb9bc716bda9110971b28a49e0ead8564ff0db22209e0374782c093bb899692d524e9d6a6956e7c5ecbcd68284");

	public SatoshiChain ()
	{
		super (70300, 0xD9B4BEF9L, 8333, 0, 128, 2015, 1209600, SATOSHI_KEY, new String[] { "dnsseed.bluematt.me", // Matt Corallo
				"bitseed.xf2.org", // Jeff Garzik
				"seed.bitcoin.sipa.be", // Pieter Wuille
				"dnsseed.bitcoin.dashjr.org", // Luke Dashjr
		});
	}

	@Override
	public Blk getGenesis ()
	{
		Blk block = new Blk ();

		block.setChainWork (1);
		block.setHeight (1);

		block.setVersion (1);
		block.setCreateTime (1231006505L);
		block.setDifficultyTarget (0x1d00ffffL);
		block.setNonce (2083236893);
		block.setPrevious (null);

		List<Tx> transactions = new ArrayList<Tx> ();
		block.setTransactions (transactions);
		Tx t = new Tx ();
		transactions.add (t);
		t.setVersion (1);

		List<TxIn> inputs = new ArrayList<TxIn> ();
		t.setInputs (inputs);
		TxIn input = new TxIn ();
		input.setTransaction (t);
		inputs.add (input);
		input.setSource (null);
		input.setSequence (0xFFFFFFFFL);
		input.setScript (Hex.decode ("04" + // mimic public key structure
				"ffff001d" + // difficulty target
				"010445" + // ??
				// text: "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
				"5468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73"));

		List<TxOut> outputs = new ArrayList<TxOut> ();
		t.setOutputs (outputs);
		TxOut output = new TxOut ();
		output.setTransaction (t);
		outputs.add (output);
		output.setValue (5000000000L);
		output.setScript (Hex
				.decode ("4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac"));
		block.computeHash ();
		return block;
	}
}
