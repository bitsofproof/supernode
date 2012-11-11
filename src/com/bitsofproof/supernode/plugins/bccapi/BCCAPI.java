package com.bitsofproof.supernode.plugins.bccapi;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bccapi.api.APIException;
import com.bccapi.api.AccountInfo;
import com.bccapi.api.AccountStatement;
import com.bccapi.api.BitcoinClientAPI;
import com.bccapi.api.Network;
import com.bccapi.api.SendCoinForm;
import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.model.Tx;

public class BCCAPI implements BitcoinClientAPI
{
	private static final Logger log = LoggerFactory.getLogger (BCCAPI.class);

	private final BitcoinNetwork network;

	private final Map<String, byte[]> challenges = Collections.synchronizedMap (new HashMap<String, byte[]> ());
	private final Map<String, Client> clients = Collections.synchronizedMap (new HashMap<String, Client> ());
	private final SecureRandom rnd = new SecureRandom ();

	private int sessionTimeout = 300;

	public BCCAPI (BitcoinNetwork network)
	{
		this.network = network;
		network.scheduleJobWithFixedDelay (new Runnable ()
		{
			@Override
			public void run ()
			{
				synchronized ( clients )
				{
					List<String> forget = new ArrayList<String> ();
					for ( Entry<String, Client> e : clients.entrySet () )
					{
						if ( e.getValue ().getLastSeen () < System.currentTimeMillis () / 1000 - sessionTimeout )
						{
							forget.add (e.getKey ());
						}
					}
					for ( String k : forget )
					{
						clients.remove (k);
					}
				}
			}
		}, 0, 10, TimeUnit.SECONDS);
	}

	public void setSessionTimeout (int sessionTimeout)
	{
		this.sessionTimeout = sessionTimeout;
	}

	@Override
	public Network getNetwork ()
	{
		return new Network (network.getChain ());
	}

	@Override
	public byte[] getLoginChallenge (byte[] accountPublicKey) throws IOException, APIException
	{
		byte[] kh = Hash.keyHash (accountPublicKey);
		byte[] c = new byte[20];
		rnd.nextBytes (c);
		challenges.put (ByteUtils.toHex (kh), c);
		log.trace ("login challenge requested by " + AddressConverter.toSatoshiStyle (kh, false, network.getChain ()));
		return c;
	}

	@Override
	public String login (byte[] accountPublicKey, byte[] challengeResponse) throws IOException, APIException
	{
		byte[] kh = Hash.keyHash (accountPublicKey);
		String khex = ByteUtils.toHex (kh);
		try
		{
			if ( challenges.containsKey (khex) )
			{
				if ( ECKeyPair.verify (challenges.get (khex), challengeResponse, accountPublicKey) )
				{
					byte[] sk = new byte[20];
					rnd.nextBytes (sk);
					String token = ByteUtils.toHex (sk);
					clients.put (token, new Client (System.currentTimeMillis () / 1000, accountPublicKey));
					log.trace ("succesful login by " + AddressConverter.toSatoshiStyle (Hash.keyHash (kh), false, network.getChain ()) + " opening session "
							+ token);
					return token;
				}
			}
		}
		finally
		{
			challenges.remove (khex);
		}
		log.trace ("login failed by " + AddressConverter.toSatoshiStyle (kh, false, network.getChain ()));
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
		Client client = clients.get (sessionID);
		if ( client != null )
		{
			log.trace ("session " + sessionID + " adds key " + AddressConverter.toSatoshiStyle (Hash.keyHash (publicKey), false, network.getChain ()));
			client.addKey (publicKey);
		}
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
