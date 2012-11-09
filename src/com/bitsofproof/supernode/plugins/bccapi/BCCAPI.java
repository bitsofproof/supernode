package com.bitsofproof.supernode.plugins.bccapi;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.bccapi.api.APIException;
import com.bccapi.api.AccountInfo;
import com.bccapi.api.AccountStatement;
import com.bccapi.api.BitcoinClientAPI;
import com.bccapi.api.Network;
import com.bccapi.api.SendCoinForm;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.model.Tx;

public class BCCAPI implements BitcoinClientAPI
{
	private final BitcoinNetwork network;

	private final Map<String, byte[]> challenges = Collections.synchronizedMap (new HashMap<String, byte[]> ());
	private final Map<String, Client> clients = Collections.synchronizedMap (new HashMap<String, Client> ());
	private final SecureRandom rnd = new SecureRandom ();

	public BCCAPI (BitcoinNetwork network)
	{
		this.network = network;
	}

	@Override
	public Network getNetwork ()
	{
		return new Network (network.getChain ());
	}

	@Override
	public byte[] getLoginChallenge (byte[] accountPublicKey) throws IOException, APIException
	{
		String kh = Hash.keyHash (accountPublicKey).toString ();
		byte[] c = new byte[20];
		rnd.nextBytes (c);
		challenges.put (kh, c);
		return c;
	}

	@Override
	public String login (byte[] accountPublicKey, byte[] challengeResponse) throws IOException, APIException
	{
		String kh = Hash.keyHash (accountPublicKey).toString ();
		if ( challenges.containsKey (kh) )
		{
			if ( ECKeyPair.verify (challenges.get (kh), challengeResponse, accountPublicKey) )
			{
				byte[] sk = new byte[20];
				rnd.nextBytes (sk);
				String token = ByteUtils.toHex (sk);
				clients.put (token, new Client (System.currentTimeMillis () / 1000, accountPublicKey));
				return token;
			}
		}
		return null;
	}

	@Override
	public AccountInfo getAccountInfo (String sessionID) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AccountStatement getAccountStatement (String sessionID, int startIndex, int count) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AccountStatement getRecentTransactionSummary (String sessionID, int count) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addKeyToWallet (String sessionID, byte[] publicKey) throws IOException, APIException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public SendCoinForm getSendCoinForm (String sessionID, String receivingAddressString, long amount, long fee) throws APIException, IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void submitTransaction (String sessionID, Tx tx) throws IOException, APIException
	{
		// TODO Auto-generated method stub

	}

}
