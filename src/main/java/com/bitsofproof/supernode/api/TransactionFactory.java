package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.List;

public class TransactionFactory
{
	public static Transaction createCoinbase (String receiver, int blockHeight, ChainParameter chain) throws ValidationException
	{
		Transaction coinbase = new Transaction ();
		coinbase.setVersion (1);
		coinbase.setLockTime (0);

		TransactionInput input = new TransactionInput ();
		input.setSourceHash (Hash.ZERO_HASH_STRING);
		input.setSequence (0xFFFFFFFFL);

		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		writer.writeData (new ScriptFormat.Number (blockHeight).toByteArray ());
		input.setScript (writer.toByteArray ());

		List<TransactionInput> inputs = new ArrayList<TransactionInput> ();
		inputs.add (input);
		coinbase.setInputs (inputs);

		TransactionOutput output = new TransactionOutput ();
		output.setScript (ScriptFormat.getPayToAddressScript (AddressConverter.fromSatoshiStyle (receiver, chain)));
		output.setValue (chain.getRewardForHeight (blockHeight));

		List<TransactionOutput> outputs = new ArrayList<TransactionOutput> ();
		outputs.add (output);
		coinbase.setOutputs (outputs);

		return coinbase;
	}
}
