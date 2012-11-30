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

import java.math.BigInteger;
import java.security.acl.Owner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.Script.Opcode;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("jpaBlockStore")
class JpaBlockStore implements BlockStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaBlockStore.class);

	private static final long MAX_BLOCK_SIGOPS = 20000;

	@Autowired
	private Chain chain;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// note: the lock protects the caches (currentHead, cachedBlocks, cachedHeads, utxoCache) not the database
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock ();

	private CachedHead currentHead = null;
	private final Map<String, CachedBlock> cachedBlocks = new HashMap<String, CachedBlock> ();
	private final Map<Long, CachedHead> cachedHeads = new HashMap<Long, CachedHead> ();

	private final Cache utxoCache = new Cache ();
	private final ExecutorService inputProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);
	private final ExecutorService transactionsProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);

	private class Cache
	{
		private final Map<String, Tx> txCache = new HashMap<String, Tx> ();

		private final Map<String, BigInteger> outputs = new HashMap<String, BigInteger> ();

		public void remove (String key, BigInteger c)
		{
			BigInteger mask = outputs.get (key);
			if ( mask != null )
			{
				mask = mask.xor (c);
				outputs.put (key, mask);

				if ( mask.equals (BigInteger.ZERO) )
				{
					txCache.remove (key);
				}
			}
		}

		public void add (String key, BigInteger c)
		{
			BigInteger mask = outputs.get (key);
			if ( mask == null )
			{
				mask = BigInteger.ZERO;
			}
			outputs.put (key, mask.or (c));
		}

		public List<TxOut> get (String txhash)
		{
			List<TxOut> outs = new ArrayList<TxOut> ();
			Tx cached = txCache.get (txhash);
			if ( cached != null )
			{
				for ( TxOut out : cached.getOutputs () )
				{
					if ( contains (txhash, out.getIx ()) )
					{
						outs.add (out);
					}
				}
			}
			return outs;
		}

		public boolean contains (String key, long ix)
		{
			BigInteger mask = outputs.get (key);
			if ( mask == null )
			{
				return false;
			}
			return mask.testBit ((int) ix);
		}

		public void add (Blk b)
		{
			for ( Tx t : b.getTransactions () )
			{
				if ( outputs.containsKey (t.getHash ()) )
				{
					txCache.put (t.getHash (), t.flatCopy ());
				}
				for ( TxIn in : t.getInputs () )
				{
					if ( !outputs.containsKey (in.getSourceHash ()) )
					{
						txCache.remove (in.getSourceHash ());
					}
				}
			}
		}

		public void remove (Blk b)
		{
			for ( Tx t : b.getTransactions () )
			{
				if ( !outputs.containsKey (t.getHash ()) )
				{
					txCache.remove (t.getHash ());
				}
				for ( TxIn in : t.getInputs () )
				{
					if ( outputs.containsKey (in.getSourceHash ()) )
					{
						txCache.put (in.getSourceHash (), in.getSource ().getTransaction ().flatCopy ());
					}
				}
			}
		}

		public void cacheUTXO ()
		{
			if ( !outputs.keySet ().isEmpty () )
			{
				QTx tx = QTx.tx;
				List<String> txs = new ArrayList<String> ();
				txs.addAll (outputs.keySet ());
				for ( int i = 0; i < txs.size (); i += 1000 )
				{
					int end = Math.min (i + 1000, txs.size ());
					JPAQuery q = new JPAQuery (entityManager);
					for ( Tx t : q.from (tx).where (tx.hash.in (txs.subList (i, end))).list (tx) )
					{
						txCache.put (t.getHash (), t.flatCopy ());
					}
					log.trace ("... read " + end);
				}
			}
		}
	}

	private static class CachedHead
	{
		private Long id;
		private CachedBlock last;
		private double chainWork;
		private long height;
		private CachedHead previous;
		private final Set<CachedBlock> blocks = new HashSet<CachedBlock> ();

		public Long getId ()
		{
			return id;
		}

		public void setId (Long id)
		{
			this.id = id;
		}

		public double getChainWork ()
		{
			return chainWork;
		}

		public long getHeight ()
		{
			return height;
		}

		public void setChainWork (double chainWork)
		{
			this.chainWork = chainWork;
		}

		public void setHeight (long height)
		{
			this.height = height;
		}

		public Set<CachedBlock> getBlocks ()
		{
			return blocks;
		}

		public CachedHead getPrevious ()
		{
			return previous;
		}

		public void setPrevious (CachedHead previous)
		{
			this.previous = previous;
		}

		public CachedBlock getLast ()
		{
			return last;
		}

		public void setLast (CachedBlock last)
		{
			this.last = last;
		}

	}

	private static class CachedBlock
	{
		public CachedBlock (String hash, Long id, CachedBlock previous, long time)
		{
			this.hash = hash;
			this.id = id;
			this.previous = previous;
			this.time = time;
		}

		private final String hash;
		private final Long id;
		private final CachedBlock previous;
		private final long time;

		public Long getId ()
		{
			return id;
		}

		public CachedBlock getPrevious ()
		{
			return previous;
		}

		public long getTime ()
		{
			return time;
		}

		public String getHash ()
		{
			return hash;
		}

		@Override
		public int hashCode ()
		{
			return hash.hashCode ();
		}
	}

	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	@Override
	public void cache () throws ValidationException
	{
		try
		{
			lock.writeLock ().lock ();

			log.trace ("Cache heads...");
			cacheHeads ();

			log.trace ("Cache chain...");
			cacheChain ();

			log.trace ("Compute UTXO...");
			List<Long> trunkPath = new ArrayList<Long> ();
			CachedBlock b = currentHead.last;
			CachedBlock p = b.previous;
			while ( p != null )
			{
				trunkPath.add (b.getId ());
				b = p;
				p = b.previous;
			}
			Collections.reverse (trunkPath);
			for ( Long id : trunkPath )
			{
				Blk blk = entityManager.find (Blk.class, id);
				if ( (blk.getHeight () % 10000) == 0 )
				{
					log.trace ("... at block " + blk.getHeight ());
				}
				forwardCache (blk);
			}
			log.trace ("Fetching UTXO set...");
			utxoCache.cacheUTXO ();
			log.trace ("Cache filled.");
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	private void cacheChain ()
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

	private void cacheHeads ()
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

	private void backwardCache (Blk b)
	{
		if ( b.getUtxoDelta () != null )
		{
			WireFormat.Reader reader = new WireFormat.Reader (b.getUtxoDelta ());
			long nout = reader.readVarInt ();
			for ( long i = 0; i < nout; ++i )
			{
				String hash = reader.readHash ().toString ();
				BigInteger mask = new BigInteger (reader.readVarBytes ());
				utxoCache.remove (hash, mask);
			}

			long nin = reader.readVarInt ();
			for ( long i = 0; i < nin; ++i )
			{
				String hash = reader.readHash ().toString ();
				BigInteger mask = new BigInteger (reader.readVarBytes ());
				utxoCache.add (hash, mask);
			}
		}
	}

	private void forwardCache (Blk b)
	{
		if ( b.getUtxoDelta () != null )
		{
			WireFormat.Reader reader = new WireFormat.Reader (b.getUtxoDelta ());
			long nout = reader.readVarInt ();
			for ( long i = 0; i < nout; ++i )
			{
				String hash = reader.readHash ().toString ();
				BigInteger mask = new BigInteger (reader.readVarBytes ());
				utxoCache.add (hash, mask);
			}
			long nin = reader.readVarInt ();
			for ( long i = 0; i < nin; ++i )
			{
				String hash = reader.readHash ().toString ();
				BigInteger mask = new BigInteger (reader.readVarBytes ());
				utxoCache.remove (hash, mask);
			}
		}
	}

	private void resolveWithUTXO (TransactionContext tcontext, Set<String> needTx)
	{
		for ( String txhash : needTx )
		{
			List<TxOut> outs = utxoCache.get (txhash);
			if ( !outs.isEmpty () )
			{
				HashMap<Long, TxOut> resolved = tcontext.resolvedInputs.get (txhash);
				if ( resolved == null )
				{
					resolved = new HashMap<Long, TxOut> ();
					tcontext.resolvedInputs.put (txhash, resolved);
				}

				for ( TxOut o : outs )
				{
					resolved.put (o.getIx (), o);
				}
			}
		}
	}

	private boolean isBlockOnBranch (CachedBlock block, CachedHead branch)
	{
		if ( branch.getBlocks ().contains (block) )
		{
			return true;
		}
		if ( branch.getPrevious () == null )
		{
			return false;
		}
		return isBlockOnBranch (block, branch.getPrevious ());
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY)
	public Blk getGenesisBlock ()
	{
		try
		{
			lock.readLock ().lock ();

			QBlk block = QBlk.blk;
			JPAQuery q = new JPAQuery (entityManager);
			return q.from (block).orderBy (block.id.asc ()).limit (1).uniqueResult (block);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public boolean isStoredBlock (String hash)
	{
		try
		{
			lock.readLock ().lock ();

			return cachedBlocks.get (hash) != null;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public long getChainHeight ()
	{
		try
		{
			lock.readLock ().lock ();

			return currentHead.getHeight ();
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public List<String> getInventory (List<String> locator, String last, int limit)
	{
		try
		{
			lock.readLock ().lock ();

			List<String> inventory = new LinkedList<String> ();
			CachedBlock curr = currentHead.getLast ();
			CachedBlock prev = curr.getPrevious ();
			if ( !last.equals (Hash.ZERO_HASH.toString ()) )
			{
				while ( prev != null && !curr.equals (last) )
				{
					curr = prev;
					prev = curr.getPrevious ();
				}
			}
			do
			{
				if ( locator.contains (curr) )
				{
					break;
				}
				inventory.add (0, curr.getHash ());
				if ( inventory.size () > limit )
				{
					inventory.remove (limit);
				}
				curr = prev;
				if ( prev != null )
				{
					prev = curr.getPrevious ();
				}
			} while ( curr != null );
			return inventory;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public List<String> getLocator ()
	{
		try
		{
			lock.readLock ().lock ();

			List<String> locator = new ArrayList<String> ();
			CachedBlock curr = currentHead.getLast ();
			locator.add (curr.getHash ());
			CachedBlock prev = curr.getPrevious ();
			for ( int i = 0, step = 1; prev != null; ++i )
			{
				for ( int j = 0; prev != null && j < step; ++j )
				{
					curr = prev;
					prev = curr.getPrevious ();
				}
				locator.add (curr.getHash ());
				if ( i >= 10 )
				{
					step *= 2;
				}
			}
			return locator;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY)
	public List<TxIn> getSpent (List<String> addresses)
	{
		try
		{
			return null;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY)
	public List<TxOut> getReceived (List<String> addresses)
	{
		try
		{
			lock.readLock ().lock ();

			return null;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY)
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		try
		{
			lock.readLock ().lock ();

			return null;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	private static class TransactionContext
	{
		Blk block;
		BigInteger blkSumInput = BigInteger.ZERO;
		BigInteger blkSumOutput = BigInteger.ZERO;
		int nsigs = 0;
		boolean coinbase = true;
		Map<String, HashMap<Long, TxOut>> resolvedInputs = new HashMap<String, HashMap<Long, TxOut>> ();
	}

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = { Exception.class })
	@Override
	public void storeBlock (Blk b) throws ValidationException
	{
		try
		{
			lock.writeLock ().lock ();

			lockedStoreBlock (b);
		}
		catch ( ValidationException e )
		{
			throw e;
		}
		catch ( Exception e )
		{
			throw new ValidationException ("OTHER exception " + b.toWireDump (), e);
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	private void lockedStoreBlock (Blk b) throws ValidationException
	{
		CachedBlock cached = cachedBlocks.get (b.getHash ());
		if ( cached != null )
		{
			return;
		}

		// find previous block
		CachedBlock cachedPrevious = cachedBlocks.get (b.getPreviousHash ());
		if ( cachedPrevious != null )
		{
			Blk prev = null;
			prev = entityManager.find (Blk.class, (cachedPrevious).getId ());
			b.setPrevious (prev);

			if ( b.getCreateTime () > (System.currentTimeMillis () / 1000) * 2 * 60 * 60 )
			{
				throw new ValidationException ("Future generation attempt " + b.getHash ());
			}

			CachedBlock trunkBlock = cachedPrevious;

			Head head;
			if ( prev.getHead ().getLeaf ().equals (prev.getHash ()) )
			{
				// continuing
				head = prev.getHead ();

				head.setLeaf (b.getHash ());
				head.setHeight (head.getHeight () + 1);
				head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget ()));
				head = entityManager.merge (head);
			}
			else
			{
				// branching
				head = new Head ();
				while ( !isBlockOnBranch (trunkBlock, currentHead) )
				{
					trunkBlock = trunkBlock.getPrevious ();
				}
				head.setPrevious (prev.getHead ());

				head.setLeaf (b.getHash ());
				head.setHeight (head.getHeight () + 1);
				head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget ()));
				entityManager.persist (head);
			}
			b.setHead (head);
			b.setHeight (head.getHeight ());
			b.setChainWork (head.getChainWork ());

			if ( b.getHeight () >= chain.getDifficultyReviewBlocks () && b.getHeight () % chain.getDifficultyReviewBlocks () == 0 )
			{
				CachedBlock c = null;
				CachedBlock p = cachedPrevious;
				for ( int i = 0; i < chain.getDifficultyReviewBlocks () - 1; ++i )
				{
					c = p;
					p = c.getPrevious ();
				}

				long next = Difficulty.getNextTarget (prev.getCreateTime () - p.getTime (), prev.getDifficultyTarget (), chain.getTargetBlockTime ());
				if ( chain.isProduction () && next != b.getDifficultyTarget () )
				{
					throw new ValidationException ("Difficulty does not match expectation " + b.getHash () + " " + b.toWireDump ());
				}
			}
			else
			{
				if ( chain.isProduction () && b.getDifficultyTarget () != prev.getDifficultyTarget () )
				{
					throw new ValidationException ("Illegal attempt to change difficulty " + b.getHash ());
				}
			}

			b.checkHash ();

			if ( chain.isProduction () && new Hash (b.getHash ()).toBigInteger ().compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) > 0 )
			{
				throw new ValidationException ("Insufficuent proof of work for current difficulty " + b.getHash () + " " + b.toWireDump ());
			}

			b.parseTransactions ();

			if ( b.getTransactions ().isEmpty () )
			{
				throw new ValidationException ("Block must have transactions " + b.getHash () + " " + b.toWireDump ());
			}

			b.checkMerkleRoot ();

			final TransactionContext tcontext = new TransactionContext ();
			tcontext.block = b;

			log.trace ("resolving inputs for block " + b.getHash ());
			Set<String> needTx = new HashSet<String> ();
			for ( Tx t : b.getTransactions () )
			{
				HashMap<Long, TxOut> outs = tcontext.resolvedInputs.get (t.getHash ());
				if ( outs == null )
				{
					outs = new HashMap<Long, TxOut> ();
					tcontext.resolvedInputs.put (t.getHash (), outs);
				}
				for ( TxOut o : t.getOutputs () )
				{
					outs.put (o.getIx (), o);
				}
				for ( TxIn i : t.getInputs () )
				{
					if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
					{
						needTx.add (i.getSourceHash ());
					}
				}
			}
			if ( !needTx.isEmpty () )
			{
				resolveWithUTXO (tcontext, needTx);
			}
			boolean skip = true;
			for ( Tx t : b.getTransactions () )
			{
				if ( skip ) // skip coinbase
				{
					skip = false;
				}
				else
				{
					checkTxInputsExist (tcontext, t);
				}
			}
			log.trace ("validating block " + b.getHash ());
			List<Callable<TransactionValidationException>> callables = new ArrayList<Callable<TransactionValidationException>> ();
			for ( final Tx t : b.getTransactions () )
			{
				if ( tcontext.coinbase )
				{
					try
					{
						validateTransaction (tcontext, t);
					}
					catch ( TransactionValidationException e )
					{
						throw new ValidationException (e.getMessage () + " " + t.toWireDump (), e);
					}
				}
				else
				{
					callables.add (new Callable<TransactionValidationException> ()
					{
						@Override
						public TransactionValidationException call ()
						{
							try
							{
								validateTransaction (tcontext, t);
							}
							catch ( TransactionValidationException e )
							{
								return e;
							}
							catch ( Exception e )
							{
								return new TransactionValidationException (e, t);
							}
							return null;
						}
					});
				}
			}
			try
			{
				for ( Future<TransactionValidationException> e : transactionsProcessor.invokeAll (callables) )
				{
					try
					{
						if ( e.get () != null )
						{
							throw new ValidationException (e.get ().getMessage () + " " + e.get ().getTx ().toWireDump (), e.get ());
						}
					}
					catch ( ExecutionException e1 )
					{
						throw new ValidationException ("corrupted transaction processor", e1);
					}
				}
			}
			catch ( InterruptedException e1 )
			{
				throw new ValidationException ("interrupted", e1);
			}

			// block reward could actually be less... as in 0000000000004c78956f8643262f3622acf22486b120421f893c0553702ba7b5
			if ( tcontext.blkSumOutput.subtract (tcontext.blkSumInput).longValue () > ((50L * Tx.COIN) >> (b.getHeight () / 210000L)) )
			{
				throw new ValidationException ("Invalid block reward " + b.getHash () + " " + b.toWireDump ());
			}

			// this is last loop before persist since modifying the entities.
			for ( Tx t : b.getTransactions () )
			{
				t.setBlock (b);
				for ( TxIn i : t.getInputs () )
				{
					if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
					{
						TxOut source = tcontext.resolvedInputs.get (i.getSourceHash ()).get (i.getIx ());
						if ( source.getId () == null )
						{
							i.setSource (source);
						}
						else
						{
							i.setSource (entityManager.getReference (TxOut.class, source.getId ()));
						}
					}
				}
				for ( TxOut o : t.getOutputs () )
				{
					addOwners (o);
				}
			}

			b.computeUTXODelta ();

			log.trace ("storing block " + b.getHash ());
			entityManager.persist (b);

			// modify transient caches only after persistent changes
			CachedBlock m = new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPrevious ().getHash ()), b.getCreateTime ());
			cachedBlocks.put (b.getHash (), m);

			CachedHead usingHead = cachedHeads.get (head.getId ());
			if ( usingHead == null )
			{
				cachedHeads.put (head.getId (), usingHead = new CachedHead ());
			}
			usingHead.setLast (m);
			usingHead.setChainWork (b.getChainWork ());
			usingHead.setHeight (b.getHeight ());
			usingHead.getBlocks ().add (m);

			if ( usingHead.getChainWork () > currentHead.getChainWork () )
			{
				// we have a new trunk
				// if branching from main we have to revert, then forward unspent cache
				CachedBlock p = currentHead.getLast ();
				CachedBlock q = p.previous;
				while ( !q.hash.equals (trunkBlock) )
				{
					Blk block = entityManager.find (Blk.class, p.id);
					backwardCache (block);
					utxoCache.remove (block);
					p = q;
					q = p.previous;
				}
				List<Long> pathToNewHead = new ArrayList<Long> ();
				p = cachedBlocks.get (usingHead.getLast ());
				q = p.previous;
				while ( !q.hash.equals (trunkBlock) )
				{
					pathToNewHead.add (p.getId ());
				}
				Collections.reverse (pathToNewHead);
				// spend what now came to trunk
				for ( Long id : pathToNewHead )
				{
					Blk block = entityManager.find (Blk.class, id);
					forwardCache (block);
					utxoCache.add (block);
				}
			}
			else if ( b.getHead ().getId () == currentHead.getId () )
			{
				// spend if on the trunk
				forwardCache (b);
				utxoCache.add (b);

			}
			log.trace ("stored block " + b.getHeight () + " " + b.getHash ());

			// now this is the new trunk
			currentHead = usingHead;
		}
	}

	private void resolveInputs (TransactionContext tcontext, Tx t) throws ValidationException
	{
		Set<String> needTx = new HashSet<String> ();
		for ( final TxIn i : t.getInputs () )
		{
			HashMap<Long, TxOut> resolved;
			if ( (resolved = tcontext.resolvedInputs.get (i.getSourceHash ())) == null )
			{
				resolved = new HashMap<Long, TxOut> ();
				tcontext.resolvedInputs.put (i.getSourceHash (), resolved);

				needTx.add (i.getSourceHash ());
			}
		}

		if ( !needTx.isEmpty () )
		{
			resolveWithUTXO (tcontext, needTx);
			checkTxInputsExist (tcontext, t);
		}
	}

	private void checkTxInputsExist (TransactionContext tcontext, Tx t) throws ValidationException
	{
		for ( final TxIn i : t.getInputs () )
		{
			HashMap<Long, TxOut> resolved = tcontext.resolvedInputs.get (i.getSourceHash ());
			if ( resolved == null )
			{
				throw new ValidationException ("Transaction refers to unknown or spent transaction " + i.getSourceHash () + " " + t.toWireDump ());
			}
			TxOut out = resolved.get (i.getIx ());
			if ( out == null )
			{
				throw new ValidationException ("Transaction refers to unknown or spent output " + i.getSourceHash () + " [" + i.getIx () + "] "
						+ t.toWireDump ());
			}
			if ( tcontext.block != null && out.getTransaction ().getHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				if ( out.getTransaction ().getBlock ().getHeight () > tcontext.block.getHeight () - 100 )
				{
					throw new ValidationException ("coinbase spent too early " + t.toWireDump ());
				}
			}
		}
	}

	private void validateTransaction (final TransactionContext tcontext, final Tx t) throws TransactionValidationException
	{
		if ( tcontext.block != null && tcontext.coinbase )
		{
			if ( t.getInputs ().size () != 1
					|| !t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ())
					|| (chain.isProduction () && tcontext.block.getHeight () > 209378 && (t.getInputs ().get (0).getIx () != 0 || t.getInputs ().get (0)
							.getSequence () != 0xFFFFFFFFL)) )
			{
				throw new TransactionValidationException ("first transaction must be coinbase ", t);
			}
			if ( t.getInputs ().get (0).getScript ().length > 100 || t.getInputs ().get (0).getScript ().length < 2 )
			{
				throw new TransactionValidationException ("coinbase scriptsig must be in 2-100 ", t);
			}
			tcontext.coinbase = false;
			for ( TxOut o : t.getOutputs () )
			{
				try
				{
					// some miner add 0 with garbage...
					if ( chain.isProduction () && o.getValue () != 0 && tcontext.block.getHeight () > 180000 && !Script.isStandard (o.getScript ()) )
					{
						throw new TransactionValidationException ("Nonstandard script rejected", t);
					}
					tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
					tcontext.nsigs += Script.sigOpCount (o.getScript ());
				}
				catch ( ValidationException e )
				{
					throw new TransactionValidationException (e, t);
				}
			}
			if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
			{
				throw new TransactionValidationException ("too many signatures in this block ", t);
			}
		}
		else
		{
			if ( t.getInputs ().size () == 1 && t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ()) )
			{
				throw new TransactionValidationException ("coinbase only first in a block", t);
			}
			if ( t.getOutputs ().isEmpty () )
			{
				throw new TransactionValidationException ("Transaction must have outputs ", t);
			}
			if ( t.getInputs ().isEmpty () )
			{
				throw new TransactionValidationException ("Transaction must have inputs ", t);
			}
			if ( tcontext.block != null && tcontext.block.getHeight () > 200000 )
			{
				// BIP 0034
				if ( t.getVersion () != 1 )
				{
					throw new TransactionValidationException ("Transaction version must be 1", t);
				}
				if ( tcontext.block.getVersion () == 2 && tcontext.coinbase )
				{
					try
					{
						if ( Script.intValue (Script.parse (t.getInputs ().get (0).getScript ()).get (0).data) != tcontext.block.getHeight () )
						{
							throw new TransactionValidationException ("Block height mismatch in coinbase", t);
						}
					}
					catch ( ValidationException e )
					{
						throw new TransactionValidationException (e, t);
					}
				}
			}

			long sumOut = 0;
			for ( TxOut o : t.getOutputs () )
			{
				if ( o.getScript ().length > 520 )
				{
					if ( tcontext.block != null && tcontext.block.getHeight () < 200000 )
					{
						log.trace ("Old DoS at [" + tcontext.block.getHeight () + "]" + tcontext.block.getHash ());
					}
					else
					{
						throw new TransactionValidationException ("script too long ", t);
					}
				}
				if ( chain.isProduction () )
				{
					try
					{
						if ( tcontext.block.getHeight () > 180000 && !Script.isStandard (o.getScript ()) )
						{
							throw new TransactionValidationException ("Nonstandard script rejected", t);
						}
					}
					catch ( ValidationException e )
					{
						throw new TransactionValidationException (e, t);
					}
				}
				if ( tcontext.block != null )
				{
					try
					{
						tcontext.nsigs += Script.sigOpCount (o.getScript ());
					}
					catch ( ValidationException e )
					{
						throw new TransactionValidationException (e, t);
					}
					if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
					{
						throw new TransactionValidationException ("too many signatures in this block ", t);
					}
				}
				if ( o.getValue () < 0 || o.getValue () > Tx.MAX_MONEY )
				{
					throw new TransactionValidationException ("Transaction output not in money range ", t);
				}
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
				sumOut += o.getValue ();
				if ( sumOut < 0 || sumOut > Tx.MAX_MONEY )
				{
					throw new TransactionValidationException ("Transaction output not in money range ", t);
				}
			}

			long sumIn = 0;
			int inNumber = 0;
			List<Callable<TransactionValidationException>> callables = new ArrayList<Callable<TransactionValidationException>> ();
			for ( final TxIn i : t.getInputs () )
			{
				if ( i.getScript ().length > 520 )
				{
					if ( tcontext.block == null || tcontext.block.getHeight () > 200000 )
					{
						throw new TransactionValidationException ("script too long ", t);
					}
				}

				final TxOut source = tcontext.resolvedInputs.get (i.getSourceHash ()).get (i.getIx ());
				sumIn += source.getValue ();

				final int nr = inNumber;
				callables.add (new Callable<TransactionValidationException> ()
				{
					@Override
					public TransactionValidationException call () throws Exception
					{
						try
						{
							if ( !new Script (t, nr, source).evaluate (chain.isProduction ()) )
							{
								return new TransactionValidationException ("The transaction script does not evaluate to true in input", t, nr);
							}

							synchronized ( tcontext )
							{
								tcontext.blkSumInput = tcontext.blkSumInput.add (BigInteger.valueOf (source.getValue ()));
							}
						}
						catch ( Exception e )
						{
							return new TransactionValidationException (e, t, nr);
						}
						return null;
					}
				});
				++inNumber;
			}
			if ( sumOut > sumIn )
			{
				throw new TransactionValidationException ("Transaction value out more than in", t);
			}
			if ( tcontext.block == null && (sumIn - sumOut) < Tx.COIN / 10000 )
			{
				throw new TransactionValidationException ("There is no free lunch.", t);
			}
			List<Future<TransactionValidationException>> results;
			try
			{
				results = inputProcessor.invokeAll (callables);
			}
			catch ( InterruptedException e1 )
			{
				throw new TransactionValidationException (e1, t);
			}
			for ( Future<TransactionValidationException> r : results )
			{
				TransactionValidationException ex;
				try
				{
					ex = r.get ();
				}
				catch ( InterruptedException e )
				{
					throw new TransactionValidationException (e, t);
				}
				catch ( ExecutionException e )
				{
					throw new TransactionValidationException (e, t);
				}
				if ( ex != null )
				{
					throw ex;
				}
			}
		}
	}

	private void addOwners (TxOut out) throws TransactionValidationException
	{
		List<Owner> owners = new ArrayList<Owner> ();
		parseOwners (out.getScript (), out, owners);
	}

	private void parseOwners (byte[] script, TxOut out, List<Owner> owners) throws TransactionValidationException
	{
		List<Script.Token> parsed;
		try
		{
			parsed = Script.parse (out.getScript ());
			if ( parsed.size () == 2 && parsed.get (0).data != null && parsed.get (1).op == Opcode.OP_CHECKSIG )
			{
				// pay to key
				out.setOwner1 (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (0).data), false, chain));
				out.setVotes (1L);
			}
			else if ( parsed.size () == 5 && parsed.get (0).op == Opcode.OP_DUP && parsed.get (1).op == Opcode.OP_HASH160 && parsed.get (2).data != null
					&& parsed.get (3).op == Opcode.OP_EQUALVERIFY && parsed.get (4).op == Opcode.OP_CHECKSIG )
			{
				// pay to address
				out.setOwner1 (AddressConverter.toSatoshiStyle (parsed.get (2).data, false, chain));
				out.setVotes (1L);
			}
			else if ( parsed.size () == 3 && parsed.get (0).op == Opcode.OP_HASH160 && parsed.get (1).data != null && parsed.get (1).data.length == 20
					&& parsed.get (2).op == Opcode.OP_EQUAL )
			{
				byte[] hash = parsed.get (1).data;
				if ( hash.length == 20 )
				{
					// BIP 0013
					out.setOwner1 (AddressConverter.toSatoshiStyle (hash, true, chain));
					out.setVotes (1L);
				}
			}
			else
			{
				for ( int i = 0; i < parsed.size (); ++i )
				{
					if ( parsed.get (i).op == Opcode.OP_CHECKMULTISIG || parsed.get (i).op == Opcode.OP_CHECKMULTISIGVERIFY )
					{
						if ( chain.isProduction () )
						{
							int nkeys = parsed.get (i - 1).op.ordinal () - Opcode.OP_1.ordinal () + 1;
							for ( int j = 0; j < nkeys; ++j )
							{
								if ( j == 0 )
								{
									out.setOwner1 (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), true, chain));
								}
								if ( j == 1 )
								{
									out.setOwner2 (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), true, chain));
								}
								if ( j == 2 )
								{
									out.setOwner3 (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (i - j - 2).data), true, chain));
								}
							}
							out.setVotes ((long) parsed.get (i - nkeys - 2).op.ordinal () - Opcode.OP_1.ordinal () + 1);
							return;
						}
					}
				}
			}
		}
		catch ( ValidationException e )
		{
			throw new TransactionValidationException (e, out.getTransaction ());
		}
	}

	@Override
	public String getHeadHash ()
	{
		try
		{
			lock.readLock ().lock ();

			return currentHead.getLast ().getHash ();
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public boolean isEmpty ()
	{
		try
		{
			lock.readLock ().lock ();

			QHead head = QHead.head;
			JPAQuery q = new JPAQuery (entityManager);
			return q.from (head).list (head).isEmpty ();
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = { Exception.class })
	@Override
	public void resetStore (Chain chain) throws TransactionValidationException
	{
		Blk genesis = chain.getGenesis ();
		addOwners (genesis.getTransactions ().get (0).getOutputs ().get (0));
		Head h = new Head ();
		h.setLeaf (genesis.getHash ());
		h.setHeight (0);
		h.setChainWork (Difficulty.getDifficulty (genesis.getDifficultyTarget ()));
		entityManager.persist (h);
		genesis.setHead (h);
		entityManager.persist (genesis);
	}

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public Blk getBlock (String hash)
	{

		CachedBlock cached = null;
		try
		{
			lock.readLock ().lock ();
			cached = cachedBlocks.get (hash);
			if ( cached == null )
			{
				return null;
			}
		}
		finally
		{
			lock.readLock ().unlock ();
		}
		return entityManager.find (Blk.class, cached.getId ());
	}

	@Transactional (propagation = Propagation.REQUIRED)
	@Override
	public void validateTransaction (Tx t) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			TransactionContext tcontext = new TransactionContext ();
			tcontext.block = null;
			tcontext.coinbase = false;
			tcontext.nsigs = 0;
			resolveInputs (tcontext, t);
			validateTransaction (tcontext, t);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}
}
