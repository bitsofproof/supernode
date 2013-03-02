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

import java.util.List;

import javax.jms.JMSException;

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI
{
	/**
	 * get block for the hash
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
	 * send a signed transaction
	 * 
	 * @param transaction
	 */
	public void sendTransaction (Transaction transaction);

	/**
	 * send a mined block
	 * 
	 * @param block
	 */
	public void sendBlock (Block block);

	/**
	 * Register a transactions listener
	 * 
	 * @param listener
	 *            will be called for every validated transaction
	 * @throws JMSException
	 */
	public void registerTransactionListener (TransactionListener listener) throws JMSException;

	/**
	 * Register a block listener
	 * 
	 * @param listener
	 *            will be called for every validated new block
	 * @throws JMSException
	 */
	public void registerTrunkListener (TrunkListener listener) throws JMSException;

	/**
	 * Register a block template listener
	 * 
	 * @param listener
	 *            will be called with work suggestions
	 * @throws JMSException
	 */
	public void registerBlockTemplateListener (TemplateListener listener) throws JMSException;

	/**
	 * Get account statement
	 * 
	 * @param addresses
	 * @param from
	 *            - unix time point the account statement should start from
	 * @return
	 */
	public AccountStatement getAccountStatement (List<String> addresses, long from);

	/**
	 * Register listener for new transactions for the given addresses. listener.received will be called for new transactions
	 * 
	 * @param addresses
	 * @param listener
	 */
	public void registerAddressListener (List<String> addresses, TransactionListener newTransactions);

	/**
	 * register listener for spend of given set of transactions listener.spent will be called if an output of the transactions is spent
	 * 
	 * @param hashes
	 * @param listener
	 */
	public void registerTransactionListener (List<String> hashes, TransactionListener spentTransactions);

	/**
	 * register listener for confirmations listener.confirmation will be called if the transaction is confirmed upto depth 10
	 * 
	 * @param hashes
	 * @param listener
	 */
	public void registerConfirmationListener (List<String> hashes, TransactionListener confirmedTransaction);
}
