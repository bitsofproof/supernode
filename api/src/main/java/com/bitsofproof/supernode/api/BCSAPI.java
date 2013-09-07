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

import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI
{
	/**
	 * returns nounce while doing a full roundtrip to the server
	 * 
	 * @param nonce
	 * @return
	 * @throws BCSAPIException
	 */
	public long ping (long nonce) throws BCSAPIException;

	/**
	 * sets the alert listener for the connections
	 * 
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void addAlertListener (AlertListener listener) throws BCSAPIException;

	public void removeAlertListener (AlertListener listener);

	/**
	 * Are we talking to production?
	 * 
	 * @return
	 * @throws BCSAPIException
	 */
	public boolean isProduction () throws BCSAPIException;

	/**
	 * get block header for the hash
	 * 
	 * @param hash
	 * @return block header or null if hash is unknown
	 * @throws BCSAPIException
	 */
	public Block getBlockHeader (String hash) throws BCSAPIException;

	/**
	 * get block for the hash
	 * 
	 * @param hash
	 * @return block or null if hash is unknown
	 * @throws BCSAPIException
	 */
	public Block getBlock (String hash) throws BCSAPIException;

	/**
	 * get the transaction identified by the hash on the trunk
	 * 
	 * @param hash
	 * @return transaction or null if no transaction with that hash on the trunk
	 * @throws BCSAPIException
	 */
	public Transaction getTransaction (String hash) throws BCSAPIException;

	/**
	 * send a signed transaction
	 * 
	 * @param transaction
	 * @throws BCSAPIException
	 */
	public void sendTransaction (Transaction transaction) throws BCSAPIException;

	/**
	 * send a mined block
	 * 
	 * @param block
	 * @throws BCSAPIException
	 */
	public void sendBlock (Block block) throws BCSAPIException;

	/**
	 * Register a transactions listener
	 * 
	 * @param listener
	 *            will be called for every validated transaction
	 * @throws BCSAPIException
	 */
	public void registerTransactionListener (TransactionListener listener) throws BCSAPIException;

	/**
	 * remove a listener for validated transactions
	 * 
	 * @param listener
	 */
	public void removeTransactionListener (TransactionListener listener);

	/**
	 * Register a block listener
	 * 
	 * @param listener
	 *            will be called for every validated new block
	 * @throws BCSAPIException
	 */
	public void registerTrunkListener (TrunkListener listener) throws BCSAPIException;

	/**
	 * remove a trunk listener previously registered
	 * 
	 * @param listener
	 */
	public void removeTrunkListener (TrunkListener listener);

	/**
	 * Scan transactions using and address or outpoint in match.
	 * 
	 * @param match
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void scanTransactions (Collection<byte[]> match, UpdateMode mode, long after, TransactionListener listener) throws BCSAPIException;

	/**
	 * Scan transactions for an account
	 * 
	 * @param master
	 *            - public master key
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void scanTransactions (ExtendedKey master, int firstIndex, int lookAhead, long after, TransactionListener listener) throws BCSAPIException;

	/**
	 * Scan unspent transactions using and address or outpoint in match.
	 * 
	 * @param match
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void scanUTXO (Collection<byte[]> match, UpdateMode mode, long after, TransactionListener listener) throws BCSAPIException;

	/**
	 * Scan unspent transactions for an account
	 * 
	 * @param master
	 *            - public master key
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void scanUTXO (ExtendedKey master, int firstIndex, int lookAhead, long after, TransactionListener listener) throws BCSAPIException;

	/**
	 * Generate a trunk update to cach up from a last seen block
	 * 
	 * @param hash
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void catchUp (String hash, TrunkListener listener) throws BCSAPIException;
}
