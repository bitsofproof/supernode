/*
 * Copyright 2013 bits of proof zrt.
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

import java.util.Collection;
import java.util.List;

import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public interface AccountManager extends TransactionListener
{
	public interface UTXO
	{

		public void add (TransactionOutput out);

		public Collection<TransactionOutput> getUTXO ();

		public TransactionOutput get (String tx, long ix);

		public TransactionOutput remove (String tx, long ix);

		public long getTotal ();

	}

	public int getNumberOfKeys ();

	public Key getNextKey () throws ValidationException;

	public Collection<byte[]> getAddresses ();

	public Transaction pay (byte[] receiver, long amount, long fee) throws ValidationException;

	public Transaction split (long[] amounts, long fee) throws ValidationException;

	public long getBalance ();

	public long getConfirmed ();

	public long getSending ();

	public long getReceiving ();

	public long getChange ();

	public Collection<TransactionOutput> getConfirmedOutputs ();

	public Collection<TransactionOutput> getSendingOutputs ();

	public Collection<TransactionOutput> getReceivingOutputs ();

	public Collection<TransactionOutput> getChangeOutputs ();

	public List<Transaction> getTransactions ();

	public void addAccountListener (AccountListener listener);

	public void removeAccountListener (AccountListener listener);
}