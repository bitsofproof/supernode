package com.bitsofproof.supernode.api;

import java.util.List;

public interface BCSAPIRemoteCalls
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
	 * get account statement
	 * 
	 * @param addresses
	 * @param from
	 *            - unix time point the account statement should start from
	 * @return
	 */
	public AccountStatement getAccountStatement (List<String> addresses, long from);
}
