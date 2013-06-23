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

public interface AccountManager extends AddressToKeyMap
{
	public void setApi (BCSAPI api);

	public String getName ();

	public long getCreated ();

	public boolean updateWithTransaction (Transaction t);

	public List<Transaction> getTransactions ();

	public Collection<byte[]> getAddresses ();

	public ExtendedKey getMasterKey ();

	public Key getNextKey () throws ValidationException;

	public int getNextSequence ();

	public Transaction pay (byte[] receiver, long amount, long fee) throws ValidationException, BCSAPIException;

	public Transaction split (long[] amounts, long fee) throws ValidationException, BCSAPIException;

	public long getBalance ();

	public long getSettled ();

	public long getSending ();

	public long getReceiving ();

	public long getChange ();

	public void addAccountListener (AccountListener listener);

	public void removeAccountListener (AccountListener listener);
}