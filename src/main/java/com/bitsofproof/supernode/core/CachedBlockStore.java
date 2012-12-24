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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.Difficulty;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Head;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public abstract class CachedBlockStore implements BlockStore
{
	private static final Logger log = LoggerFactory.getLogger (CachedBlockStore.class);

	private static final long MAX_BLOCK_SIGOPS = 20000;

	// not allowed to branch further back on trunk
	private static final int FORCE_TRUNK = 100;

	@Autowired
	private Chain chain;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock ();

	protected CachedHead currentHead = null;
	protected final Map<String, CachedBlock> cachedBlocks = new HashMap<String, CachedBlock> ();
	protected final Map<Long, CachedHead> cachedHeads = new HashMap<Long, CachedHead> ();

	private final TxOutCache cachedUTXO = new TxOutCache ();
	private final List<TrunkListener> trunkListener = new ArrayList<TrunkListener> ();

	private final ExecutorService inputProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);
	private final ExecutorService transactionsProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);

	protected void startBatch ()
	{
	};

	protected void endBatch ()
	{
	};

	protected void cancelBatch ()
	{
	};

	protected abstract void cacheChain ();

	protected abstract void cacheHeads ();

	protected abstract void cacheUTXO (int lookback);

	protected abstract List<TxOut> findTxOuts (Map<String, HashSet<Long>> need);

	protected abstract void backwardCache (Blk b);

	protected abstract void forwardCache (Blk b);

	protected abstract List<TxOut> getReceivedList (List<String> addresses, long from);

	protected abstract TxOut getSourceReference (TxOut source);

	protected abstract void insertBlock (Blk b);

	protected abstract void insertHead (Head head);

	protected abstract Head updateHead (Head head);

	protected abstract Blk retrieveBlock (CachedBlock cached);

	protected abstract Blk retrieveBlockHeader (CachedBlock cached);

	protected abstract List<TxIn> getSpendList (List<String> addresses, long from);

	@Override
	public void addTrunkListener (TrunkListener listener)
	{
		trunkListener.add (listener);
	}

	private void extendTrunk (Blk b)
	{
		forwardCache (b);
		for ( TrunkListener l : trunkListener )
		{
			l.trunkExtended (b);
		}
	}

	private void shortenTrunk (Blk b)
	{
		backwardCache (b);
		for ( TrunkListener l : trunkListener )
		{
			l.trunkShortened (b);
		}
	}

	protected void removeUTXO (String txhash, long ix)
	{
		cachedUTXO.remove (txhash, ix);
	}

	protected void addUTXO (String txhash, TxOut out)
	{
		cachedUTXO.put (txhash, out);
	}

	protected static class CachedHead
	{
		private Long id;
		private CachedBlock last;
		private double chainWork;
		private long height;
		private CachedHead previous;
		private final Set<CachedBlock> blocks = new HashSet<CachedBlock> ();

		public CachedHead ()
		{
		}

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

	protected static class CachedBlock
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

		@Override
		public boolean equals (Object o)
		{
			if ( o == null )
			{
				return false;
			}
			if ( o == this )
			{
				return true;
			}
			return hash.equals (((CachedBlock) o).hash);
		}
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	public void cache (int size) throws ValidationException
	{
		try
		{
			lock.writeLock ().lock ();

			log.trace ("Cache heads...");
			cacheHeads ();

			log.trace ("Cache chain...");
			cacheChain ();

			if ( size > 0 )
			{
				log.trace ("Cache UTXO set ...");
				cacheUTXO (size);
			}

			log.trace ("Cache filled.");
		}
		finally
		{
			lock.writeLock ().unlock ();
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

	private boolean isOnTrunk (String block)
	{
		CachedBlock b = cachedBlocks.get (block);
		return isBlockOnBranch (b, currentHead);
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
	public String getPreviousBlockHash (String hash)
	{
		try
		{
			lock.readLock ().lock ();

			CachedBlock current = cachedBlocks.get (hash);
			if ( current != null )
			{
				CachedBlock previous = current.previous;
				if ( previous == null )
				{
					return Hash.ZERO_HASH_STRING;
				}
				return previous.hash;
			}
			return null;
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
				while ( prev != null && !curr.getHash ().equals (last) )
				{
					curr = prev;
					prev = curr.getPrevious ();
				}
			}
			do
			{
				if ( locator.contains (curr.getHash ()) )
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
	@Transactional (propagation = Propagation.MANDATORY, readOnly = true)
	public List<TxIn> getSpent (List<String> addresses, long after)
	{
		List<TxIn> rows = getSpendList (addresses, after);
		try
		{
			lock.readLock ().lock ();

			Iterator<TxIn> i = rows.iterator ();
			while ( i.hasNext () )
			{
				TxIn in = i.next ();
				String block = in.getTransaction ().getBlock ().getHash ();
				if ( !isOnTrunk (block) )
				{
					i.remove ();
				}
			}
		}
		finally
		{
			lock.readLock ().unlock ();
		}
		return rows;
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY, readOnly = true)
	public List<TxOut> getReceived (List<String> addresses, long after)
	{
		List<TxOut> rows = getReceivedList (addresses, after);
		try
		{
			lock.readLock ().lock ();

			Iterator<TxOut> i = rows.iterator ();
			while ( i.hasNext () )
			{
				TxOut cols = i.next ();
				String block = cols.getTransaction ().getBlock ().getHash ();
				if ( !isOnTrunk (block) )
				{
					i.remove ();
				}
			}
		}
		finally
		{
			lock.readLock ().unlock ();
		}
		return rows;
	}

	private static class TransactionContext
	{
		Blk block;
		BigInteger blkSumInput = BigInteger.ZERO;
		BigInteger blkSumOutput = BigInteger.ZERO;
		int nsigs = 0;
		boolean coinbase = true;
		TxOutCache resolvedInputs = new TxOutCache ();
	}

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = { Exception.class })
	@Override
	public void storeBlock (Blk b) throws ValidationException
	{
		try
		{
			lock.writeLock ().lock ();

			startBatch ();
			lockedStoreBlock (b);
			endBatch ();
		}
		catch ( ValidationException e )
		{
			cancelBatch ();
			throw e;
		}
		catch ( Exception e )
		{
			cancelBatch ();
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
		log.trace ("Start storing block " + b.getHash ());
		// find previous block
		CachedBlock cachedPrevious = cachedBlocks.get (b.getPreviousHash ());
		if ( cachedPrevious != null )
		{
			Blk prev = null;
			prev = retrieveBlockHeader (cachedPrevious);

			if ( b.getCreateTime () > (System.currentTimeMillis () / 1000) * 2 * 60 * 60 )
			{
				throw new ValidationException ("Future generation attempt " + b.getHash ());
			}

			CachedBlock trunkBlock = cachedPrevious;

			Head head;

			Map<String, HashMap<Long, TxOut>> notInBranch = new HashMap<String, HashMap<Long, TxOut>> ();
			Map<String, HashMap<Long, TxOut>> newBranch = new HashMap<String, HashMap<Long, TxOut>> ();

			if ( prev.getHead ().getLeaf ().equals (prev.getHash ()) )
			{
				log.trace ("continuing trunk");
				// continuing
				head = prev.getHead ();

				head.setLeaf (b.getHash ());
				head.setHeight (head.getHeight () + 1);
				head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget ()));
				head = updateHead (head);
			}
			else
			{
				// branching
				head = new Head ();
				int n = 0;
				while ( !isBlockOnBranch (trunkBlock, currentHead) )
				{
					Blk c = retrieveBlock (trunkBlock);
					List<Tx> txs = new ArrayList<Tx> ();
					txs.addAll (c.getTransactions ());
					Collections.reverse (txs);
					for ( Tx t : txs )
					{
					}
					if ( ++n > FORCE_TRUNK )
					{
						throw new ValidationException ("Attempt to branch too far back in history " + b.getHash ());
					}
					trunkBlock = trunkBlock.getPrevious ();
				}
				log.trace ("branching trunk at " + trunkBlock);
				head.setPrevious (prev.getHead ());

				head.setLeaf (b.getHash ());
				head.setHeight (prev.getHeight () + 1);
				head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget ()));
				insertHead (head);
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
			for ( Tx t : b.getTransactions () )
			{
				resolveInputs (tcontext.resolvedInputs, b.getHeight (), t);
				for ( TxOut o : t.getOutputs () )
				{
					tcontext.resolvedInputs.put (t.getHash (), o);
				}
			}
			if ( b.getHeight () > chain.getValidateFrom () )
			{
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
								throw new ValidationException (e.get ().getMessage () + " " + e.get ().getIn () + " " + e.get ().getTx ().toWireDump (),
										e.get ());
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
			}
			// block reward could actually be less... as in 0000000000004c78956f8643262f3622acf22486b120421f893c0553702ba7b5
			if ( tcontext.blkSumOutput.subtract (tcontext.blkSumInput).longValue () > chain.getRewardForHeight (b.getHeight ()) )
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
						TxOut source = tcontext.resolvedInputs.get (i.getSourceHash (), i.getIx ());
						if ( source.getId () == null )
						{
							i.setSource (source);
						}
						else
						{
							i.setSource (getSourceReference (source));
						}
					}
					i.setBlockTime (b.getCreateTime ());
				}
				for ( TxOut o : t.getOutputs () )
				{
					parseOwners (o);
					o.setTxHash (t.getHash ());
					o.setHeight (b.getHeight ());
					o.setBlockTime (b.getCreateTime ());
				}
			}

			log.trace ("storing block " + b.getHash ());
			insertBlock (b);

			// modify transient caches only after persistent changes
			CachedBlock m = new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime ());
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
				log.trace ("NEW trunk ");
				// we have a new trunk
				// if branching from main we have to revert, then forward unspent cache
				CachedBlock p = currentHead.getLast ();
				CachedBlock q = p.previous;
				while ( !q.equals (trunkBlock) )
				{
					log.trace ("BACKWARD cache " + p.hash);
					Blk block = retrieveBlock (p);
					shortenTrunk (block);
					p = q;
					q = p.previous;
				}
				List<CachedBlock> pathToNewHead = new ArrayList<CachedBlock> ();
				p = cachedBlocks.get (usingHead.getLast ().getHash ());
				q = p.previous;
				while ( !q.equals (trunkBlock) )
				{
					pathToNewHead.add (p);
				}
				Collections.reverse (pathToNewHead);
				// spend what now came to trunk
				for ( CachedBlock cb : pathToNewHead )
				{
					log.trace ("FORWARD cache " + cb.hash);
					Blk block = retrieveBlock (cb);
					extendTrunk (block);
				}
			}
			else if ( b.getHead ().getId ().longValue () == currentHead.getId ().longValue () )
			{
				// spend if on the trunk
				extendTrunk (b);
			}
			log.info ("stored block " + b.getHeight () + " " + b.getHash ());

			// now this is the new trunk
			currentHead = usingHead;
		}
	}

	private void resolveInputs (TxOutCache resolvedInputs, int blockHeight, Tx t) throws ValidationException
	{
		resolveInputsUsingUTXOCache (resolvedInputs, t);
		resolveInputsUsingDB (resolvedInputs, t);
		checkInputResolution (resolvedInputs, blockHeight, t);
	}

	private void resolveInputsUsingUTXOCache (TxOutCache resolvedInputs, Tx t) throws ValidationException
	{
		for ( final TxIn i : t.getInputs () )
		{
			if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				resolvedInputs.copy (cachedUTXO, i.getSourceHash ());
			}
		}
	}

	private void resolveInputsUsingDB (TxOutCache resolvedInputs, Tx t) throws ValidationException
	{
		Map<String, HashSet<Long>> need = new HashMap<String, HashSet<Long>> ();
		for ( final TxIn i : t.getInputs () )
		{
			if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				if ( resolvedInputs.get (i.getSourceHash (), i.getIx ()) == null )
				{
					HashSet<Long> ixs = need.get (i.getSourceHash ());
					if ( ixs == null )
					{
						ixs = new HashSet<Long> ();
						need.put (i.getSourceHash (), ixs);
					}
					ixs.add (i.getIx ());
				}
			}
		}
		if ( !need.isEmpty () )
		{
			List<TxOut> all = findTxOuts (need);

			// filter for duplicate transaction hashes.
			// such transaction is only available if none of its siblings is spent
			List<TxOut> fromDB = new ArrayList<TxOut> ();
			Map<String, HashMap<Long, ArrayList<TxOut>>> found = new HashMap<String, HashMap<Long, ArrayList<TxOut>>> ();
			for ( TxOut o : all )
			{
				HashMap<Long, ArrayList<TxOut>> outsByIx = found.get (o.getTxHash ());
				if ( outsByIx == null )
				{
					outsByIx = new HashMap<Long, ArrayList<TxOut>> ();
					found.put (o.getTxHash (), outsByIx);
				}
				ArrayList<TxOut> outs = outsByIx.get (o.getIx ());
				if ( outs == null )
				{
					outs = new ArrayList<TxOut> ();
					outsByIx.put (o.getIx (), outs);
				}
				outs.add (o);
			}
			for ( Map.Entry<String, HashMap<Long, ArrayList<TxOut>>> e : found.entrySet () )
			{
				for ( Map.Entry<Long, ArrayList<TxOut>> e2 : e.getValue ().entrySet () )
				{
					if ( e2.getValue ().size () == 1 )
					{
						fromDB.add (e2.getValue ().get (0));
					}
					else
					{
						boolean allAvailable = true;
						for ( TxOut o : e2.getValue () )
						{
							if ( !o.isAvailable () )
							{
								allAvailable = false;
							}
						}
						if ( allAvailable )
						{
							fromDB.add (e2.getValue ().get (0));
						}
					}
				}
			}

			for ( TxOut o : fromDB )
			{
				resolvedInputs.put (o.getTxHash (), o);
			}
		}
	}

	private void checkInputResolution (TxOutCache resolvedInputs, int height, Tx t) throws ValidationException
	{
		for ( final TxIn i : t.getInputs () )
		{
			if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				TxOut out = resolvedInputs.get (i.getSourceHash (), i.getIx ());
				if ( out == null )
				{
					throw new ValidationException ("Transaction refers to unknown or spent output " + i.getSourceHash () + " [" + i.getIx () + "] "
							+ t.toWireDump ());
				}
				if ( height != 0 && out.isCoinbase () )
				{
					if ( out.getHeight () > height - 100 )
					{
						throw new ValidationException ("coinbase spent too early " + t.toWireDump ());
					}
				}
			}
		}
	}

	private void validateTransaction (final TransactionContext tcontext, final Tx t) throws TransactionValidationException
	{
		if ( tcontext.block == null || tcontext.block != null && tcontext.block.getHeight () > 200000 )
		{
			// BIP 0034
			if ( t.getVersion () != 1 )
			{
				throw new TransactionValidationException ("Transaction version must be 1", t);
			}
		}
		if ( t.getLockTime () != 0 )
		{
			throw new TransactionValidationException ("Transaction must be locked", t);
		}
		if ( tcontext.block != null && tcontext.coinbase )
		{
			if ( t.getInputs ().size () != 1 || !t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ()) )
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
					tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
					tcontext.nsigs += ScriptFormat.sigOpCount (o.getScript ());
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
				if ( tcontext.block.getVersion () == 2 && tcontext.coinbase )
				{
					try
					{
						if ( ScriptFormat.intValue (ScriptFormat.parse (t.getInputs ().get (0).getScript ()).get (0).data) != tcontext.block.getHeight () )
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
				try
				{
					if ( tcontext.block == null && chain.isProduction () && !ScriptFormat.isStandard (o.getScript ()) )
					{
						throw new TransactionValidationException ("Nonstandard script rejected", t);
					}
				}
				catch ( ValidationException e )
				{
					throw new TransactionValidationException (e, t);
				}
				if ( tcontext.block != null )
				{
					try
					{
						tcontext.nsigs += ScriptFormat.sigOpCount (o.getScript ());
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
				if ( i.getSequence () != 0xFFFFFFFFL )
				{
					throw new TransactionValidationException ("Input sequencing is disabled ", t);
				}
				if ( i.getScript ().length > 520 )
				{
					if ( tcontext.block == null || tcontext.block.getHeight () > 200000 )
					{
						throw new TransactionValidationException ("script too long ", t);
					}
				}

				final TxOut source = tcontext.resolvedInputs.get (i.getSourceHash (), i.getIx ());
				sumIn += source.getValue ();

				final int nr = inNumber;
				callables.add (new Callable<TransactionValidationException> ()
				{
					@Override
					public TransactionValidationException call () throws Exception
					{
						try
						{
							if ( !new ScriptEvaluation (t, nr, source).evaluate (chain.isProduction ()) )
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

	private void parseOwners (TxOut out) throws TransactionValidationException
	{
		List<ScriptFormat.Token> parsed;
		try
		{
			parsed = ScriptFormat.parse (out.getScript ());
			if ( parsed.size () == 2 && parsed.get (0).data != null && parsed.get (1).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to key
				out.setOwner1 (AddressConverter.toSatoshiStyle (Hash.keyHash (parsed.get (0).data), false, chain));
				out.setVotes (1L);
			}
			else if ( parsed.size () == 5 && parsed.get (0).op == ScriptFormat.Opcode.OP_DUP && parsed.get (1).op == ScriptFormat.Opcode.OP_HASH160
					&& parsed.get (2).data != null && parsed.get (3).op == ScriptFormat.Opcode.OP_EQUALVERIFY
					&& parsed.get (4).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				// pay to address
				out.setOwner1 (AddressConverter.toSatoshiStyle (parsed.get (2).data, false, chain));
				out.setVotes (1L);
			}
			else if ( parsed.size () == 3 && parsed.get (0).op == ScriptFormat.Opcode.OP_HASH160 && parsed.get (1).data != null
					&& parsed.get (1).data.length == 20 && parsed.get (2).op == ScriptFormat.Opcode.OP_EQUAL )
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
					if ( parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIG || parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIGVERIFY )
					{
						if ( chain.isProduction () )
						{
							int nkeys = parsed.get (i - 1).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1;
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
							out.setVotes ((long) parsed.get (i - nkeys - 2).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1);
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

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = { Exception.class })
	@Override
	public void resetStore (Chain chain) throws TransactionValidationException
	{
		Blk genesis = chain.getGenesis ();
		TxOut out = genesis.getTransactions ().get (0).getOutputs ().get (0);
		parseOwners (out);
		Head h = new Head ();
		h.setLeaf (genesis.getHash ());
		h.setHeight (0);
		h.setChainWork (Difficulty.getDifficulty (genesis.getDifficultyTarget ()));
		insertHead (h);
		genesis.setHead (h);
		insertBlock (genesis);
	}

	@Transactional (propagation = Propagation.MANDATORY, readOnly = true)
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
		return retrieveBlock (cached);
	}

	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	@Override
	public void resolveTransactionInputs (Tx t, TxOutCache resolvedInputs) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			resolveInputs (resolvedInputs, 0, t);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	@Override
	public void validateTransaction (Tx t, TxOutCache resolvedInputs) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			resolveInputs (resolvedInputs, 0, t);

			TransactionContext tcontext = new TransactionContext ();
			tcontext.block = null;
			tcontext.coinbase = false;
			tcontext.nsigs = 0;
			tcontext.resolvedInputs = resolvedInputs;

			validateTransaction (tcontext, t);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}
}
