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

import java.util.List;

public interface AccountManager
{
	public Transaction pay (byte[] receiver, long amount, long fee) throws ValidationException, BCSAPIException;

	public Transaction split (long[] amounts, long fee) throws ValidationException, BCSAPIException;

	public Transaction transfer (byte[] receiver, long units, long fee, Color color) throws ValidationException, BCSAPIException;

	public Transaction createColorGenesis (long quantity, long unitSize, long fee) throws ValidationException, BCSAPIException;

	public Transaction cashIn (ECKeyPair key, long fee) throws ValidationException, BCSAPIException;

	public long getBalance ();

	public long getBalance (Color color);

	public List<String> getColors ();

	public void addAccountListener (AccountListener listener);

	public void removeAccountListener (AccountListener listener);
}