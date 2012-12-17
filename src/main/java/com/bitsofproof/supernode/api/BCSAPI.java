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

import java.util.List;

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI
{
	public long getHeartbeat (long mine);

	/**
	 * get a block for the hash
	 * 
	 * @param hash
	 * @return block or null if hash is unknown
	 */
	public Block getBlock (String hash);

	/**
	 * get the transaction identified by the hash on the trunk
	 * 
	 * @param hash
	 * @return transaction or null if no transaction with that hash on the trunk
	 */
	public Transaction getTransaction (String hash);

	/**
	 * get the hash of the highest block on the trunk
	 * 
	 * @return
	 */
	public String getTrunk ();

	/**
	 * get predecessor of a block identified by the hash
	 * 
	 * @param hash
	 * @return a block or null if the hash is invalid. It returns zero hash for genesis
	 */
	public String getPreviousBlockHash (String hash);

	/**
	 * get transaction outputs that could be spent by the adresses
	 * 
	 * @param address
	 * @return list of outputs, eventually empty
	 */
	public List<TransactionOutput> getBalance (List<String> address);

	/**
	 * send a signed transaction
	 * 
	 * @param transaction
	 * @throws ValidationException
	 *             - if the transaction is invalid for numerous reasons
	 */
	public void sendTransaction (Transaction transaction) throws ValidationException;

	/**
	 * get list of outputs an address ever received
	 * 
	 * @param address
	 * @return list of outputs, eventually empty
	 */
	public List<TransactionOutput> getReceivedTransactions (List<String> address);

	/**
	 * get list of spends for a set of adresses
	 * 
	 * @param address
	 * @return list of spends, eventually empty
	 */
	public List<TransactionInput> getSpentTransactions (List<String> address);
}
