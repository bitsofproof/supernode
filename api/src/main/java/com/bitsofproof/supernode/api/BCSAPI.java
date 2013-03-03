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

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI
{
	/**
	 * get the chain hash list from genesis to most recent
	 * 
	 * @return block hashes
	 * @throws JMSException
	 * @throws InvalidProtocolBufferException
	 */
	List<String> getBlocks () throws JMSException, InvalidProtocolBufferException;

	/**
	 * get block for the hash
	 * 
	 * @param hash
	 * @return block or null if hash is unknown
	 * @throws JMSException
	 * @throws InvalidProtocolBufferException
	 */
	public Block getBlock (String hash) throws JMSException, InvalidProtocolBufferException;

	/**
	 * get the transaction identified by the hash on the trunk
	 * 
	 * @param hash
	 * @return transaction or null if no transaction with that hash on the trunk
	 * @throws JMSException
	 * @throws InvalidProtocolBufferException
	 */
	public Transaction getTransaction (String hash) throws JMSException, InvalidProtocolBufferException;

	/**
	 * send a signed transaction
	 * 
	 * @param transaction
	 * @throws JMSException
	 */
	public void sendTransaction (Transaction transaction) throws JMSException;

	/**
	 * send a mined block
	 * 
	 * @param block
	 * @throws JMSException
	 */
	public void sendBlock (Block block) throws JMSException;

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
	 * Get account statement
	 * 
	 * @param addresses
	 * @param from
	 *            - unix time point the account statement should start from
	 * @return
	 * @throws JMSException
	 * @throws InvalidProtocolBufferException
	 */
	public AccountStatement getAccountStatement (List<String> addresses, long from) throws JMSException, InvalidProtocolBufferException;

	/**
	 * Register listener for new transactions for the given addresses. listener.received will be called for new transactions
	 * 
	 * @param addresses
	 * @param listener
	 * @throws JMSException
	 */
	public void registerAddressListener (List<String> addresses, TransactionListener newTransactions) throws JMSException;

	/**
	 * register listener for spend of given set of transactions listener.spent will be called if an output of the transactions is spent
	 * 
	 * @param hashes
	 * @param listener
	 * @throws JMSException
	 */
	public void registerTransactionListener (List<String> hashes, TransactionListener spentTransactions) throws JMSException;

	/**
	 * register listener for confirmations listener.confirmation will be called if the transaction is confirmed upto depth 10
	 * 
	 * @param hashes
	 * @param listener
	 * @throws JMSException
	 * @throws InvalidProtocolBufferException
	 */
	public void registerConfirmationListener (List<String> hashes, TransactionListener confirmedTransactions) throws JMSException,
			InvalidProtocolBufferException;
}
