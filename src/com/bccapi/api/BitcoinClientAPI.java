package com.bccapi.api;

import java.io.IOException;

import com.bitsofproof.supernode.model.Tx;

/**
 * The Bitcoin Client API interface. This interface describes all the functions implemented by the BCCAPI server.
 */
public interface BitcoinClientAPI
{

	/**
	 * Get the network used, test network or production network.
	 * 
	 * @return The network used, test network or production network.
	 */
	public Network getNetwork ();

	/**
	 * Get a login challenge from the BccServer for an account identified by its public key. The challenge is used with the login function, and is valid for a
	 * limited time.
	 * 
	 * @param accountPublicKey
	 *            The public key of the account to get a challenge for.
	 * @return A 20-byte login challenge.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public byte[] getLoginChallenge (byte[] accountPublicKey) throws IOException, APIException;

	/**
	 * Login with the public key of an account and a signed response of the challenge obtained from getLoginChallenge. If this is the first successful login
	 * with an account public key a new account is created. If the account is left idle for a significant amount of time and its associated wallet left empty,
	 * then the account may get deleted.
	 * 
	 * @param accountPublicKey
	 *            The public key of the account to login with.
	 * @param challengeResponse
	 *            The challenge obtained from a call to getLoginChallenge signed with the account private key.
	 * @return A session ID providing access to other functions in the Bitcoin Client API.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public String login (byte[] accountPublicKey, byte[] challengeResponse) throws IOException, APIException;

	/**
	 * Get {@link AccountInfo} for this account.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @return {@link AccountInfo} for this account.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public AccountInfo getAccountInfo (String sessionID) throws IOException, APIException;

	/**
	 * Get {@link AccountStatement} for this account.
	 * <p>
	 * Call this function to get the {@link AccountStatement} for an account, which includes transaction records. The function allows the caller to specify
	 * which records should be obtained. This enables a client to minimize bandwidth by only requesting records not seen previously.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @param startIndex
	 *            The first record index to retrieve. The first record registered with an account has index 0.
	 * @param count
	 *            The number of records to retrieve. If the number supplied is larger than what is available, only the available records are returned.
	 * @return {@link AccountStatement} for this account.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public AccountStatement getAccountStatement (String sessionID, int startIndex, int count) throws IOException, APIException;

	/**
	 * Get an {@link AccountStatement} for this account containing a specified number of recent entries.
	 * <p>
	 * Call this function to get the {@link AccountStatement} for an account, which includes recent transaction records. The function allows the caller to
	 * specify how many recent records should be obtained.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @param count
	 *            The number of records to retrieve. If the number supplied is larger than what is available, only the available records are returned.
	 * @return {@link AccountStatement} for this account.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public AccountStatement getRecentTransactionSummary (String sessionID, int count) throws IOException, APIException;

	/**
	 * Add a new public key to the wallet. Any future blocks containing transaction outputs for this key are included in the wallet.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @param publicKey
	 *            The public key to add to the wallet.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public void addKeyToWallet (String sessionID, byte[] publicKey) throws IOException, APIException;

	/**
	 * Get a {@link SendCoinForm} for sending bitcoins to a bitcoin address. The form returned contains a transaction that needs to be signed and submitted with
	 * {@link BitcoinClientAPI#submitTransaction}. The transaction needs to be signed with the private keys corresponding to the public keys that have been
	 * added to the wallet using {@link BitcoinClientAPI#addKeyToWallet}. The {@link SendCoinForm} contains the indexes of each public keys that needs to sign
	 * the various inputs of the transaction. Those indexes refer to the private keys in the order they were added to the server.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @param receivingAddressString
	 *            The bitcoin address the the receiver
	 * @param amount
	 *            The number of bitcoins to send in satoshis (1 satoshi = 0.000000001 BTC)
	 * @param fee
	 *            The size of the fee to include in the transaction in satoshis (1 satoshi = 0.000000001 BTC)
	 * @return a {@link SendCoinForm} for sending bitcoins to a bitcoin address.
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public SendCoinForm getSendCoinForm (String sessionID, String receivingAddressString, long amount, long fee) throws APIException, IOException;

	/**
	 * Submit a signed transaction. The server will validate that the transaction has been signed appropriately and is valid according to available funds, and
	 * broadcast it to the bitcoin network.
	 * 
	 * @param sessionID
	 *            The session ID.
	 * @param tx
	 *            The signed transaction to submit
	 * @throws IOException
	 *             If communication with server fails
	 * @throws APIException
	 *             If the server responds with an error
	 */
	public void submitTransaction (String sessionID, Tx tx) throws IOException, APIException;
}
