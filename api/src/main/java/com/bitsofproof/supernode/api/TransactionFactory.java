/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

		coinbase.computeHash ();

		return coinbase;
	}
}
