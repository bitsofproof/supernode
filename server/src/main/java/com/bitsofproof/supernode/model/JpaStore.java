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
package com.bitsofproof.supernode.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.CachedBlockStore;
import com.bitsofproof.supernode.core.Discovery;
import com.bitsofproof.supernode.core.PeerStore;
import com.bitsofproof.supernode.core.TxOutCache;
import com.mysema.query.jpa.impl.JPAQuery;

public class JpaStore extends CachedBlockStore implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaStore.class);

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	protected void cacheUTXO (int lookback, TxOutCache cache)
	{
		long after = Math.max (currentHead.getHeight () - lookback, 0L);
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.available.eq (true).and (txout.height.gt (after))).list (txout) )
		{
			cache.add (o.flatCopy (null));
			entityManager.detach (o);
		}
	}

	@Override
	protected void cacheChain ()
	{
		JPAQuery q;
		QBlk block = QBlk.blk;
		q = new JPAQuery (entityManager);
		for ( Blk b : q.from (block).orderBy (block.id.asc ()).list (block) )
		{
			CachedBlock cb = null;
			if ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				cb =
						new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime (), b.getHeight (),
								(int) b.getVersion ());
			}
			else
			{
				cb = new CachedBlock (b.getHash (), b.getId (), null, b.getCreateTime (), b.getHeight (), (int) b.getVersion ());
			}
			cachedBlocks.put (b.getHash (), cb);
			CachedHead h = cachedHeads.get (b.getHeadId ());
			h.getBlocks ().add (cb);
			h.setLast (cb);
		}
	}

	@Override
	protected void cacheHeads ()
	{
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Head h : q.from (head).orderBy (head.id.asc ()).list (head) )
		{
			CachedHead sh = new CachedHead ();
			sh.setId (h.getId ());
			sh.setChainWork (h.getChainWork ());
			sh.setHeight (h.getHeight ());
			if ( h.getPreviousId () != null )
			{
				sh.setPrevious (cachedHeads.get (h.getPreviousId ()));
				sh.setPreviousHeight (h.getPreviousHeight ());
			}
			cachedHeads.put (h.getId (), sh);
			if ( currentHead == null || currentHead.getChainWork () < sh.getChainWork () )
			{
				currentHead = sh;
			}
		}
	}

	@Override
	protected void backwardCache (Blk b, TxOutCache cache, boolean modify)
	{
		List<Tx> txs = new ArrayList<Tx> ();
		txs.addAll (b.getTransactions ());
		Collections.reverse (txs);
		for ( Tx t : txs )
		{
			for ( TxOut out : t.getOutputs () )
			{
				if ( modify )
				{
					out.setAvailable (false);
				}
				cache.remove (t.getHash (), out.getIx ());
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					TxOut source = in.getSource ();
					if ( modify )
					{
						source.setAvailable (true);
					}
					cache.add (source.flatCopy (null));
				}
			}
		}
	}

	@Override
	protected void forwardCache (Blk b, TxOutCache cache, boolean modify)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				if ( modify )
				{
					out.setAvailable (true);
				}
				cache.add (out.flatCopy (null));
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					if ( modify )
					{
						in.getSource ().setAvailable (false);
					}
					cache.remove (in.getSourceHash (), in.getIx ());
				}
			}
		}
	}

	@Override
	protected List<TxOut> findTxOuts (Map<String, HashSet<Long>> need)
	{
		List<TxOut> fromDB = new ArrayList<TxOut> ();
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.txHash.in (need.keySet ())).list (txout) )
		{
			if ( o.isAvailable () && need.get (o.getTxHash ()).contains (o.getIx ()) )
			{
				fromDB.add (o);
			}
		}
		return fromDB;
	}

	@Override
	protected void checkBIP30Compliance (Set<String> txs, int untilHeight) throws ValidationException
	{
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.txHash.in (txs)).list (txout) )
		{
			if ( o.isAvailable () && o.getTransaction ().getBlock ().getHeight () <= untilHeight )
			{
				throw new ValidationException ("BIP30 violation block contains unspent tx " + o.getTxHash ());
			}
		}
	}

	@Override
	protected List<TxIn> getSpendList (List<String> addresses, long from)
	{
		List<TxIn> spent = new ArrayList<TxIn> ();

		QTxOut txout = QTxOut.txOut;
		QTxIn txin = QTxIn.txIn;

		JPAQuery q = new JPAQuery (entityManager);

		spent.addAll (q.from (txin).join (txin.source, txout).where (txout.owner1.in (addresses).and (txin.blockTime.goe (from))).list (txin));

		q = new JPAQuery (entityManager);
		spent.addAll (q.from (txin).join (txin.source, txout).where (txout.owner2.in (addresses).and (txin.blockTime.goe (from))).list (txin));

		q = new JPAQuery (entityManager);
		spent.addAll (q.from (txin).join (txin.source, txout).where (txout.owner3.in (addresses).and (txin.blockTime.goe (from))).list (txin));

		return spent;
	}

	@Override
	protected List<TxOut> getReceivedList (List<String> addresses, long from)
	{
		List<TxOut> received = new ArrayList<TxOut> ();
		QTxOut txout = QTxOut.txOut;

		JPAQuery q = new JPAQuery (entityManager);

		received.addAll (q.from (txout).where (txout.owner1.in (addresses).and (txout.blockTime.goe (from))).list (txout));

		q = new JPAQuery (entityManager);
		received.addAll (q.from (txout).where (txout.owner2.in (addresses).and (txout.blockTime.goe (from))).list (txout));

		q = new JPAQuery (entityManager);
		received.addAll (q.from (txout).where (txout.owner3.in (addresses).and (txout.blockTime.goe (from))).list (txout));

		return received;
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		List<TxOut> utxo = new ArrayList<TxOut> ();
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.owner1.in (addresses).and (txout.available.eq (true))).list (txout) )
		{
			utxo.add (o);
		}
		q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.owner2.in (addresses).and (txout.available.eq (true))).list (txout) )
		{
			utxo.add (o);
		}
		q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.owner3.in (addresses).and (txout.available.eq (true))).list (txout) )
		{
			utxo.add (o);
		}
		return utxo;
	}

	@Override
	protected Blk retrieveBlock (CachedBlock cached)
	{
		return entityManager.find (Blk.class, cached.getId ());
	}

	@Override
	protected Blk retrieveBlockHeader (CachedBlock cached)
	{
		return entityManager.find (Blk.class, cached.getId ());
	}

	@Override
	protected void insertHead (Head head)
	{
		entityManager.persist (head);
	}

	@Override
	protected Head updateHead (Head head)
	{
		return entityManager.merge (head);
	}

	@Override
	protected Head retrieveHead (CachedHead cached)
	{
		return entityManager.find (Head.class, cached.getId ());
	}

	@Override
	protected void insertBlock (Blk b)
	{
		entityManager.persist (b);
	}

	@Override
	public boolean isEmpty ()
	{
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		return q.from (head).list (head).isEmpty ();
	}

	@Override
	protected TxOut getSourceReference (TxOut source)
	{
		return entityManager.getReference (TxOut.class, source.getId ());
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	public Tx getTransaction (String hash)
	{
		JPAQuery q = new JPAQuery (entityManager);
		QTx tx = QTx.tx;

		Tx t = q.from (tx).where (tx.hash.eq (hash)).uniqueResult (tx);
		if ( t != null )
		{
			// trigger lazy loading
			t.getInputs ().size ();
			t.getOutputs ().size ();
			entityManager.detach (t);
			return t;
		}
		return null;
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public List<KnownPeer> getConnectablePeers ()
	{
		QKnownPeer kp = QKnownPeer.knownPeer;
		JPAQuery q = new JPAQuery (entityManager);
		List<KnownPeer> pl =
				q.from (kp).where (kp.banned.lt (System.currentTimeMillis () / 1000)).orderBy (kp.preference.desc ()).orderBy (kp.height.desc ())
						.orderBy (kp.responseTime.desc ()).orderBy (kp.connected.desc ()).list (kp);
		log.trace ("Retrieved " + pl.size () + " peers from store");
		return pl;
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public void store (KnownPeer peer)
	{
		try
		{
			KnownPeer stored;
			if ( (stored = findPeer (InetAddress.getByName (peer.getAddress ()))) == null )
			{
				entityManager.persist (peer);
			}
			else
			{
				stored.setAgent (peer.getAgent ());
				stored.setBanned (peer.getBanned ());
				stored.setBanReason (peer.getBanReason ());
				stored.setConnected (peer.getConnected ());
				stored.setDisconnected (peer.getDisconnected ());
				stored.setHeight (peer.getHeight ());
				stored.setName (peer.getName ());
				stored.setPreference (peer.getPreference ());
				stored.setResponseTime (peer.getResponseTime ());
				stored.setServices (peer.getServices ());
				stored.setTrafficIn (peer.getTrafficIn ());
				stored.setTrafficOut (peer.getTrafficOut ());
				stored.setVersion (peer.getVersion ());
				entityManager.merge (stored);
			}
		}
		catch ( ConstraintViolationException e )
		{
		}
		catch ( UnknownHostException e )
		{
		}
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public KnownPeer findPeer (InetAddress address)
	{
		QKnownPeer kp = QKnownPeer.knownPeer;
		JPAQuery q = new JPAQuery (entityManager);
		return q.from (kp).where (kp.address.eq (address.getHostAddress ())).uniqueResult (kp);
	}

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering stored peers");
		List<InetAddress> peers = new ArrayList<InetAddress> ();
		for ( KnownPeer kp : getConnectablePeers () )
		{
			try
			{
				peers.add (InetAddress.getByName (kp.getAddress ()));
			}
			catch ( UnknownHostException e )
			{
			}
		}
		return peers;
	}
}
