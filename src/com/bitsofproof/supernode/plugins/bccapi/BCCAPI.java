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
package com.bitsofproof.supernode.plugins.bccapi;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.bccapi.api.APIException;
import com.bccapi.api.AccountInfo;
import com.bccapi.api.AccountStatement;
import com.bccapi.api.AccountStatement.Record;
import com.bccapi.api.Network;
import com.bccapi.api.SendCoinForm;
import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.TxHandler;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Owner;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class BCCAPI implements ExtendedBitcoinClientAPI
{
	private static final Logger log = LoggerFactory.getLogger (BCCAPI.class);

	private final BitcoinNetwork network;
	private PlatformTransactionManager transactionManager;
	private TxHandler txhandler;

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

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void setSessionTimeout (int sessionTimeout)
	{
		this.sessionTimeout = sessionTimeout;
	}

	public void setTxhandler (TxHandler txhandler)
	{
		this.txhandler = txhandler;
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
					clients.put (token, new Client (System.currentTimeMillis () / 1000, accountPublicKey, network.getChain ()));
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
		final Client client = clients.get (sessionID);
		if ( client != null )
		{
			AccountInfo ai = new TransactionTemplate (transactionManager).execute (new TransactionCallback<AccountInfo> ()
			{
				@Override
				public AccountInfo doInTransaction (TransactionStatus status)
				{
					status.setRollbackOnly ();

					long balance = 0;
					for ( TxOut out : network.getStore ().getUnspentOutput (client.getAddresses ()) )
					{
						balance += out.getValue ();
					}
					long estimate = balance;
					for ( TxIn in : txhandler.getSpentByAddress (client.getAddresses ()) )
					{
						estimate -= in.getSource ().getValue ();
					}
					for ( TxOut out : txhandler.getReceivedByAddress (client.getAddresses ()) )
					{
						estimate += out.getValue ();
					}
					return new AccountInfo (client.getAddresses ().size (), balance, estimate);
				}
			});
			log.trace ("retrieved account info for " + sessionID + "  " + ai);
			return ai;
		}
		return null;
	}

	@Override
	public void addAddress (String sessionID, String address)
	{
		final Client client = clients.get (sessionID);
		if ( client != null )
		{
			client.addAddress (address);
		}
	}

	private AccountStatement getFullAccountStatement (String sessionID) throws IOException, APIException
	{
		final Client client = clients.get (sessionID);
		if ( client != null )
		{
			final AccountInfo ai = getAccountInfo (sessionID);
			AccountStatement as = new TransactionTemplate (transactionManager).execute (new TransactionCallback<AccountStatement> ()
			{
				@Override
				public AccountStatement doInTransaction (TransactionStatus status)
				{
					status.setRollbackOnly ();

					int height = (int) network.getStore ().getChainHeight ();
					List<Record> records = new ArrayList<Record> ();

					for ( TxOut out : network.getStore ().getReceived (client.getAddresses ()) )
					{
						Blk blk = out.getTransaction ().getBlock ();
						int confirmations = height - blk.getHeight ();
						long date = blk.getCreateTime ();
						StringBuffer addresses = new StringBuffer ();
						if ( out.getOwners () != null )
						{
							boolean first = true;
							for ( Owner o : out.getOwners () )
							{
								addresses.append (o.getAddress ());
								if ( !first )
								{
									addresses.append (" ");
								}
								first = false;
							}
						}
						records.add (new Record (0, confirmations, date, Record.Type.Received, addresses.toString (), out.getValue ()));
					}
					for ( TxIn in : network.getStore ().getSpent (client.getAddresses ()) )
					{
						Blk blk = in.getTransaction ().getBlock ();
						int confirmations = height - blk.getHeight ();
						long date = blk.getCreateTime ();
						StringBuffer addresses = new StringBuffer ();
						if ( in.getSource ().getOwners () != null )
						{
							boolean first = true;
							for ( Owner o : in.getSource ().getOwners () )
							{
								addresses.append (o.getAddress ());
								if ( !first )
								{
									addresses.append (" ");
								}
								first = false;
							}
						}
						Record.Type t = Record.Type.Sent;
						if ( client.hasAddress (addresses.toString ()) )
						{
							t = Record.Type.SentToSelf;
						}
						records.add (new Record (0, confirmations, date, t, addresses.toString (), in.getSource ().getValue ()));
					}
					Collections.sort (records, new Comparator<Record> ()
					{
						@Override
						public int compare (Record arg0, Record arg1)
						{
							int cmp = (int) (arg0.getDate () - arg1.getDate ());
							if ( cmp == 0 )
							{
								if ( arg0.getType () == Record.Type.Received && arg1.getType () != Record.Type.Received )
								{
									return -1;
								}
								if ( arg1.getType () == Record.Type.Received && arg0.getType () != Record.Type.Received )
								{
									return 1;
								}
							}
							return cmp;
						}
					});
					for ( int i = 0; i < records.size (); ++i )
					{
						records.get (i).setIndex (i);
					}
					return new AccountStatement (ai, records.size (), records);
				}
			});
			log.trace ("retrieved account statement  for " + sessionID);
			return as;
		}
		return null;
	}

	@Override
	public AccountStatement getAccountStatement (String sessionID, final int startIndex, final int count) throws IOException, APIException
	{
		AccountStatement full = getFullAccountStatement (sessionID);
		if ( full != null )
		{
			if ( startIndex > full.getTotalRecordCount () )
			{
				return new AccountStatement (full.getInfo (), full.getTotalRecordCount (), new ArrayList<Record> ());
			}
			ArrayList<Record> sub = new ArrayList<Record> ();
			// sublist does not serialize
			sub.addAll (full.getRecords ().subList (startIndex, Math.min (count, full.getTotalRecordCount ())));
			return new AccountStatement (full.getInfo (), full.getTotalRecordCount (), sub);
		}
		return null;
	}

	@Override
	public AccountStatement getRecentTransactionSummary (String sessionID, int count) throws IOException, APIException
	{
		AccountStatement full = getFullAccountStatement (sessionID);
		if ( full != null )
		{
			count = Math.min (count, full.getTotalRecordCount ());
			ArrayList<Record> sub = new ArrayList<Record> ();
			// sublist does not serialize
			sub.addAll (full.getRecords ().subList (full.getTotalRecordCount () - count, full.getTotalRecordCount ()));
			return new AccountStatement (full.getInfo (), full.getTotalRecordCount (), sub);
		}
		return null;
	}

	@Override
	public void addKeyToWallet (String sessionID, byte[] publicKey) throws IOException, APIException
	{
		Client client = clients.get (sessionID);
		if ( client != null )
		{
			log.trace ("session " + sessionID + " adds key " + AddressConverter.toSatoshiStyle (Hash.keyHash (publicKey), false, network.getChain ()));
			client.addKey (publicKey, network.getChain ());
		}
	}

	@Override
	public SendCoinForm getSendCoinForm (final String sessionID, final String receivingAddressString, final long amount, final long fee) throws APIException,
			IOException
	{
		final Client client = clients.get (sessionID);
		if ( client != null )
		{
			return new TransactionTemplate (transactionManager).execute (new TransactionCallback<SendCoinForm> ()
			{
				@Override
				public SendCoinForm doInTransaction (TransactionStatus status)
				{
					status.setRollbackOnly ();

					long balance = 0;
					List<TxOut> use = new ArrayList<TxOut> ();
					for ( TxOut out : network.getStore ().getUnspentOutput (client.getAddresses ()) )
					{
						try
						{
							if ( Script.isPayToKey (out.getScript ()) || Script.isPayToAddress (out.getScript ()) )
							{
								balance += out.getValue ();
								use.add (out);
								if ( balance >= (amount + fee) )
								{
									break;
								}
							}
						}
						catch ( ValidationException e )
						{
							// can not re-use nonstandard output
						}
					}
					if ( balance < (amount + fee) )
					{
						log.trace ("Rejected send coins due to insufficient funds for " + sessionID);
						return null;
					}
					Tx tx = new Tx ();
					List<TxIn> inputs = new ArrayList<TxIn> ();
					tx.setInputs (inputs);
					List<Integer> useKey = new ArrayList<Integer> ();
					for ( TxOut out : use )
					{
						TxIn in = new TxIn ();
						in.setSourceHash (out.getTransaction ().getHash ());
						in.setIx (out.getIx ());
						try
						{
							List<Script.Token> tokens = Script.parse (out.getScript ());
							if ( Script.isPayToAddress (out.getScript ()) )
							{
								Script.Writer writer = new Script.Writer ();
								int ki = client.getKeyIndexForKeyHash (tokens.get (2).data);
								useKey.add (ki);
								writer.writeData (client.getKey (ki));
								in.setScript (writer.toByteArray ());
							}
							if ( Script.isPayToKey (out.getScript ()) )
							{
								useKey.add (client.getKeyIndex (tokens.get (0).data));
								in.setScript (new byte[0]);
							}
						}
						catch ( ValidationException e )
						{
							return null;
						}
						inputs.add (in);
					}
					List<TxOut> outs = new ArrayList<TxOut> ();
					tx.setOutputs (outs);
					TxOut out = new TxOut ();
					try
					{
						out.setScript (Script.getPayToAddressScript (AddressConverter.fromSatoshiStyle (receivingAddressString, network.getChain ())));
					}
					catch ( ValidationException e )
					{
						log.trace ("Rejected send coins to invalid address " + receivingAddressString + " for " + sessionID);
						return null;
					}
					out.setValue (amount);
					outs.add (out);
					if ( balance > (amount + fee) )
					{
						out = new TxOut ();
						out.setScript (Script.getPayToAddressScript (Hash.keyHash (client.getKey (0))));
						out.setValue (balance - amount - fee);
						outs.add (out);
					}
					log.trace ("Prepared send " + amount + " (fee " + fee + ") to " + receivingAddressString + " for " + sessionID);
					return new SendCoinForm (tx, useKey, use);
				}
			});

		}
		return null;
	}

	@Override
	public void submitTransaction (String sessionID, Tx tx) throws IOException, APIException
	{
		final Client client = clients.get (sessionID);
		if ( client != null )
		{
			try
			{
				network.getStore ().validateTransaction (tx);
				for ( BitcoinPeer peer : network.getConnectPeers () )
				{
					TxMessage txm = (TxMessage) peer.createMessage ("tx");
					txm.setTx (tx);
					peer.send (txm);
				}
				log.trace ("Sent transaction for " + sessionID + " " + tx.toWireDump ());
			}
			catch ( ValidationException e )
			{
				log.trace ("Rejected invalid transaction for " + sessionID);
			}
		}
	}
}
