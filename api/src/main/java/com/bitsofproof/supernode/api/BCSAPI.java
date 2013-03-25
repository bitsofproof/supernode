/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI
{
	/**
	 * get the chain hash list from genesis to most recent
	 * 
	 * @return block hashes
	 * @throws BCSAPIException
	 */
	List<String> getBlocks () throws BCSAPIException;

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
	 * Get account statement
	 * 
	 * @param addresses
	 * @param from
	 *            - unix time point the account statement should start from
	 * @return
	 * @throws BCSAPIException
	 */
	public AccountStatement getAccountStatement (Collection<String> addresses, long from) throws BCSAPIException;

	/**
	 * Register listener for new transactions for the given addresses. listener.process will be called for new transactions
	 * 
	 * @param addresses
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void registerAddressListener (Collection<String> addresses, TransactionListener newTransactions) throws BCSAPIException;

	/**
	 * register listener for spend of given set of transactions. listener.process will be called if an output of the transactions is spent
	 * 
	 * @param hashes
	 * @param listener
	 * @throws BCSAPIException
	 */
	public void registerOutputListener (Collection<String> hashes, TransactionListener spendingTransaction) throws BCSAPIException;

	/**
	 * remove a listener previously registered for spend, receive or confirmations
	 * 
	 * @param filter
	 */
	public void removeFilteredListener (Collection<String> filter, TransactionListener listener);

	/**
	 * create a key generator
	 * 
	 * @throws BCSAPIException
	 */
	public KeyGenerator createKeyGenerator (int addressFlag, int p2shAddressFlag) throws BCSAPIException;

	/**
	 * get the key generator for a master key and next key sequence
	 * 
	 * @throws BCSAPIException
	 */
	public KeyGenerator getKeyGenerator (ExtendedKey master, int nextKeySequence, int addressFlag, int p2shAddressFlag) throws BCSAPIException;

	/**
	 * create an account manager
	 */
	public AccountManager createAccountManager (KeyGenerator generator) throws BCSAPIException;

	/**
	 * Issue a color
	 * 
	 * @param color
	 */
	public void issueColor (Color color) throws BCSAPIException;

	/**
	 * retrieve color definition
	 * 
	 * @param digest
	 * @return
	 * @throws BCSAPIException
	 */
	public Color getColor (String digest) throws BCSAPIException;
}
