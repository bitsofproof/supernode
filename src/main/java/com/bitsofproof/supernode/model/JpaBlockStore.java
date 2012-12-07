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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import com.bitsofproof.supernode.core.CachedBlockStore;
import com.bitsofproof.supernode.core.Hash;
import com.mysema.query.jpa.impl.JPAQuery;
import com.mysema.query.types.expr.BooleanExpression;

public class JpaBlockStore extends CachedBlockStore
{
	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	protected void cacheUTXO ()
	{
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		for ( TxOut o : q.from (txout).where (txout.available.eq (true)).list (txout) )
		{
			HashMap<Long, TxOut> outs = cachedUTXO.get (o.getTxHash ());
			if ( outs == null )
			{
				outs = new HashMap<Long, TxOut> ();
				cachedUTXO.put (o.getTxHash (), outs);
			}
			outs.put (o.getIx (), o.flatCopy (null));
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
				cb = new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime ());
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
				out.setAvailable (false);
				removeUTXO (t.getHash (), out.getIx ());
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					TxOut source = in.getSource ();
					source.setAvailable (true);
					addUTXO (in.getSourceHash (), source.flatCopy (null));
				}
			}
		}
		entityManager.merge (b);
	}

	@Override
	protected void forwardCache (Blk b)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				out.setAvailable (true);
				addUTXO (t.getHash (), out.flatCopy (null));
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					in.getSource ().setAvailable (false);
					removeUTXO (in.getSourceHash (), in.getIx ());
				}
			}
		}
		entityManager.merge (b);
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
	protected List<TxOut> findTxOuts (Map<Long, HashSet<String>> need)
	{
		QTxOut txout = QTxOut.txOut;
		JPAQuery q = new JPAQuery (entityManager);
		BooleanExpression exp = null;
		for ( Long ix : need.keySet () )
		{
			if ( exp == null )
			{
				exp = txout.ix.eq (ix).and (txout.txHash.in (need.get (ix)));
			}
			else
			{
				exp = BooleanExpression.anyOf (exp, BooleanExpression.allOf (txout.ix.eq (ix)).and (txout.txHash.in (need.get (ix))));
			}
		}

		return q.from (txout).where (BooleanExpression.allOf (exp, txout.available.eq (true))).list (txout);
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
		JPAQuery q = new JPAQuery (entityManager);
		return q.from (txout)
				.where (txout.available.eq (true).and (txout.owner1.in (addresses).or (txout.owner2.in (addresses)).or (txout.owner3.in (addresses))))
				.list (txout);
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
