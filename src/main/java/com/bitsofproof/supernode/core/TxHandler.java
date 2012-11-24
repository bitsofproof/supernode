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
package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.BlockStore;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class TxHandler implements ChainListener
{
	private static final Logger log = LoggerFactory.getLogger (TxHandler.class);

	private PlatformTransactionManager transactionManager;

	private final Set<String> heard = Collections.synchronizedSet (new HashSet<String> ());
	private final Map<String, Tx> unconfirmed = new HashMap<String, Tx> ();
	private final Map<String, ArrayList<TxIn>> spentByAddress = new HashMap<String, ArrayList<TxIn>> ();
	private final Map<String, ArrayList<TxOut>> receivedByAddress = new HashMap<String, ArrayList<TxOut>> ();

	public TxHandler (final BitcoinNetwork network, final ChainLoader loader)
	{
		final BlockStore store = network.getStore ();
		loader.addChainListener (this);

		network.addListener ("inv", new BitcoinMessageListener<InvMessage> ()
		{
			@Override
			public void process (InvMessage im, BitcoinPeer peer)
			{
				GetDataMessage get = (GetDataMessage) peer.createMessage ("getdata");
				for ( byte[] h : im.getTransactionHashes () )
				{
					String hash = new Hash (h).toString ();
					if ( !heard.contains (hash) )
					{
						heard.add (hash);
						log.trace ("heard about new transaction " + hash + " from " + peer.getAddress ());
						get.getTransactions ().add (h);
					}
				}
				if ( !loader.isBehind () && get.getTransactions ().size () > 0 )
				{
					log.trace ("asking for transaction details from " + peer.getAddress ());
					peer.send (get);
				}
			}
		});
		network.addListener ("tx", new BitcoinMessageListener<TxMessage> ()
		{

			@Override
			public void process (final TxMessage txm, final BitcoinPeer peer)
			{
				log.trace ("received transaction details for " + txm.getTx ().getHash () + " from " + peer.getAddress ());
				heard.remove (txm.getTx ().getHash ());
				if ( !unconfirmed.containsKey (txm.getTx ().getHash ()) && !loader.isBehind () )
				{
					if ( new TransactionTemplate (transactionManager).execute (new TransactionCallback<Boolean> ()
					{
						@Override
						public Boolean doInTransaction (TransactionStatus status)
						{
							status.setRollbackOnly ();
							try
							{
								store.validateTransaction (txm.getTx ());
								cacheTransaction (txm.getTx ());
								return true;
							}
							catch ( ValidationException e )
							{
								peer.error ("Received invalid transaction", 50);
								log.trace ("Rejeting transaction " + txm.getTx ().getHash () + " from " + peer.getAddress (), e);
							}
							return false;
						}
					}).booleanValue () )
					{
						for ( BitcoinPeer p : network.getConnectPeers () )
						{
							if ( p != peer )
							{
								TxMessage tm = (TxMessage) p.createMessage ("tx");
								tm.setTx (txm.getTx ());
								p.send (tm);
							}
						}
					}
				}
			}
		});
	}

	public List<TxIn> getSpentByAddress (List<String> addresses)
	{
		List<TxIn> spent = new ArrayList<TxIn> ();
		for ( String a : addresses )
		{
			List<TxIn> s = spentByAddress.get (a);
			if ( s != null )
			{
				spent.addAll (s);
			}
		}
		return spent;
	}

	public List<TxOut> getReceivedByAddress (List<String> addresses)
	{
		List<TxOut> received = new ArrayList<TxOut> ();
		for ( String a : addresses )
		{
			List<TxOut> s = receivedByAddress.get (a);
			if ( s != null )
			{
				received.addAll (s);
			}
		}
		return received;
	}

	public Tx getTransaction (String hash)
	{
		return unconfirmed.get (hash);
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void cacheTransaction (Tx tx)
	{
		log.trace ("Caching unconfirmed transaction " + tx.getHash ());
		synchronized ( unconfirmed )
		{
			unconfirmed.put (tx.getHash (), tx);
			for ( TxIn in : tx.getInputs () )
			{
				addSpent (in, in.getSource ().getOwner1 ());
				addSpent (in, in.getSource ().getOwner2 ());
				addSpent (in, in.getSource ().getOwner3 ());
			}
			for ( TxOut out : tx.getOutputs () )
			{
				addReceived (out, out.getOwner1 ());
				addReceived (out, out.getOwner2 ());
				addReceived (out, out.getOwner3 ());
			}
		}
	}

	private void addReceived (TxOut out, String address)
	{
		if ( address != null )
		{
			ArrayList<TxOut> recd = receivedByAddress.get (address);
			if ( recd == null )
			{
				recd = new ArrayList<TxOut> ();
				receivedByAddress.put (address, recd);
			}
			recd.add (out);
		}
	}

	private void removeReceived (TxOut out, String address)
	{
		if ( address != null )
		{
			ArrayList<TxOut> recd = receivedByAddress.get (address);
			if ( recd != null )
			{
				recd.remove (out);
			}
		}
	}

	private void addSpent (TxIn in, String address)
	{
		if ( address != null )
		{
			ArrayList<TxIn> spent = spentByAddress.get (address);
			if ( spent == null )
			{
				spent = new ArrayList<TxIn> ();
				spentByAddress.put (address, spent);
			}
			spent.add (in);
		}
	}

	private void removeSpent (TxIn in, String address)
	{
		if ( address != null )
		{
			ArrayList<TxIn> spent = spentByAddress.get (address);
			if ( spent != null )
			{
				spentByAddress.remove (in);
			}
		}
	}

	@Override
	public void blockAdded (final Blk blk)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{

			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				synchronized ( unconfirmed )
				{
					if ( unconfirmed.isEmpty () )
					{
						return;
					}
					for ( Tx tx : blk.getTransactions () )
					{
						heard.remove (tx.getHash ());
						Tx cached = unconfirmed.get (tx.getHash ());
						unconfirmed.remove (tx.getHash ());
						for ( TxIn in : cached.getInputs () )
						{
							removeSpent (in, in.getSource ().getOwner1 ());
							removeSpent (in, in.getSource ().getOwner2 ());
							removeSpent (in, in.getSource ().getOwner3 ());
						}
						for ( TxOut out : cached.getOutputs () )
						{
							removeReceived (out, out.getOwner1 ());
							removeReceived (out, out.getOwner2 ());
							removeReceived (out, out.getOwner3 ());
						}
					}
				}
			}
		});
	}
}
