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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.bitsofproof.supernode.core.Hash;
import com.mysema.query.jpa.impl.JPADeleteClause;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("jpaBlockStore")
public class JpaBlockStore extends CachedBlockStore
{
	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	protected void cacheUTXO ()
	{
		QUTxOut utxo = QUTxOut.uTxOut;
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Object[] o : q.from (utxo).join (utxo.txOut, txout).list (utxo, txout) )
		{
			UTxOut u = (UTxOut) o[0];
			TxOut out = (TxOut) o[1];
			u.setTxOut (out);
			HashMap<Long, UTxOut> outs = cachedUTXO.get (u.getHash ());
			if ( outs == null )
			{
				outs = new HashMap<Long, UTxOut> ();
				cachedUTXO.put (u.getHash (), outs);
			}
			outs.put (u.getIx (), u);
			entityManager.detach (u);
			entityManager.detach (out);
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
			if ( b.getPrevious () != null )
			{
				cb = new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPrevious ().getHash ()), b.getCreateTime ());
			}
			else
			{
				cb = new CachedBlock (b.getHash (), b.getId (), null, b.getCreateTime ());
			}
			cachedBlocks.put (b.getHash (), cb);
			CachedHead h = cachedHeads.get (b.getHead ().getId ());
			h.getBlocks ().add (cb);
			h.setLast (cb);
		}
	}

	@Override
	protected void cacheHeads ()
	{
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Head h : q.from (head).list (head) )
		{
			CachedHead sh = new CachedHead ();
			sh.setId (h.getId ());
			sh.setChainWork (h.getChainWork ());
			sh.setHeight (h.getHeight ());
			if ( h.getPrevious () != null )
			{
				sh.setPrevious (cachedHeads.get (h.getId ()));
			}
			cachedHeads.put (h.getId (), sh);
			if ( currentHead == null || currentHead.getChainWork () < sh.getChainWork () )
			{
				currentHead = sh;
			}
		}
	}

	@Override
	protected void backwardCache (Blk b)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				removeUTXO (t.getHash (), out.getIx ());
			}
			QUTxOut utxo = QUTxOut.uTxOut;
			JPADeleteClause q = new JPADeleteClause (entityManager, utxo);
			q.where (utxo.hash.eq (t.getHash ())).execute ();

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					UTxOut u = new UTxOut ();
					u.setHash (t.getHash ());
					u.setIx (in.getIx ());
					u.setTxOut (in.getSource ().flatCopy (null));
					u.setHeight (b.getHeight ());
					entityManager.persist (u);

					addUTXO (in.getSourceHash (), u);
				}
			}
		}
	}

	@Override
	protected void forwardCache (Blk b)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				UTxOut utxo = new UTxOut ();
				utxo.setHash (t.getHash ());
				utxo.setIx (out.getIx ());
				utxo.setTxOut (out.flatCopy (null));
				utxo.setHeight (b.getHeight ());
				entityManager.persist (utxo);

				addUTXO (t.getHash (), utxo);
			}
			Map<Long, ArrayList<String>> tuples = new HashMap<Long, ArrayList<String>> ();
			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					ArrayList<String> txs = tuples.get (in.getIx ());
					if ( txs == null )
					{
						txs = new ArrayList<String> ();
						tuples.put (in.getIx (), txs);
					}
					txs.add (in.getSourceHash ());

					removeUTXO (in.getSourceHash (), in.getIx ());
				}
			}
			for ( Long ix : tuples.keySet () )
			{
				QUTxOut utxo = QUTxOut.uTxOut;
				JPADeleteClause q = new JPADeleteClause (entityManager, utxo);
				q.where (utxo.hash.in (tuples.get (ix)).and (utxo.ix.eq (ix))).execute ();
			}
		}
	}

	@Override
	protected List<Object[]> getSpendList (List<String> addresses)
	{
		QTxOut txout = QTxOut.txOut;
		QTxIn txin = QTxIn.txIn;
		QTx tx = QTx.tx;
		QBlk blk = QBlk.blk;
		JPAQuery q = new JPAQuery (entityManager);
		List<Object[]> rows =
				q.from (txin).join (txin.source, txout).join (txin.transaction, tx).join (tx.block, blk)
						.where (txout.owner1.in (addresses).or (txout.owner2.in (addresses)).or (txout.owner3.in (addresses))).list (blk.hash, txin);
		return rows;
	}

	@Override
	protected List<Object[]> getReceivedList (List<String> addresses)
	{
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		QTx tx = QTx.tx;
		QBlk blk = QBlk.blk;
		List<Object[]> rows =
				q.from (txout).join (txout.transaction, tx).join (tx.block, blk)
						.where (txout.owner1.in (addresses).or (txout.owner2.in (addresses)).or (txout.owner3.in (addresses))).list (blk.hash, txout);
		return rows;
	}

	@Override
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		QTxOut txout = QTxOut.txOut;
		QUTxOut utxo = QUTxOut.uTxOut;
		JPAQuery q = new JPAQuery (entityManager);
		return q.from (utxo).join (utxo.txOut, txout).where (txout.owner1.in (addresses).or (txout.owner2.in (addresses)).or (txout.owner3.in (addresses)))
				.list (txout);
	}

	@Override
	protected Blk retrieveBlock (CachedBlock cached)
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
}
