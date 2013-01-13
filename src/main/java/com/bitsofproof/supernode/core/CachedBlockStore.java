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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.Difficulty;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
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
	private static final long MIN_RELAY_TX_FEE = 10000;
	private static final long KB_RELAY_TX_FEE = 50000;
	private static final int COINBASE_MATURITY = 100;

	private static final Map<Integer, String> checkPoints = new HashMap<Integer, String> ();
	private static final int lastCheckPoint;

	static
	{
		checkPoints.put (11111, "0000000069e244f73d78e8fd29ba2fd2ed618bd6fa2ee92559f542fdb26e7c1d");
		checkPoints.put (33333, "000000002dd5588a74784eaa7ab0507a18ad16a236e7b1ce69f00d7ddfb5d0a6");
		checkPoints.put (74000, "0000000000573993a3c9e41ce34471c079dcf5f52a0e824a81e7f953b8661a20");
		checkPoints.put (105000, "00000000000291ce28027faea320c8d2b054b2e0fe44a773f3eefb151d6bdc97");
		checkPoints.put (134444, "00000000000005b12ffd4cd315cd34ffd4a594f430ac814c91184a0d42d2b0fe");
		checkPoints.put (168000, "000000000000099e61ea72015e79632f216fe6cb33d7899acb35b75c8303b763");
		checkPoints.put (193000, "000000000000059f452a5f7340de6682a977387c17010ff6e6c3bd83ca8b1317");
		checkPoints.put (210000, "000000000000048b95347e83192f69cf0366076336c639f9b7228e9ba171342e");
		checkPoints.put (215000, "000000000000032f64cb76edfb654062e7b94b152bb6458a59b979fabf99012b");

		lastCheckPoint = 215000;
	}

	private Chain chain;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock ();

	private boolean enforceV2Block = false;
	private boolean checkV2BlockCoinBase = false;

	protected CachedHead currentHead = null;
	protected final Map<String, CachedBlock> cachedBlocks = new HashMap<String, CachedBlock> ();
	protected final Map<Long, CachedHead> cachedHeads = new HashMap<Long, CachedHead> ();

	private final ImplementTxOutCache cachedUTXO = new ImplementTxOutCache ();
	private final List<TrunkListener> trunkListener = new ArrayList<TrunkListener> ();

	private final ExecutorService inputProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);
	private final ExecutorService transactionsProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors () * 2);

	@Override
	public void runInCacheContext (CacheContextRunnable runnable)
	{
		try
		{
			lock.readLock ().lock ();

			runnable.run (cachedUTXO);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

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

	protected abstract void cacheUTXO (int lookback, TxOutCache cache);

	protected abstract List<TxOut> findTxOuts (Map<String, HashSet<Long>> need);

	protected abstract void backwardCache (Blk b, TxOutCache cache, boolean modify);

	protected abstract void forwardCache (Blk b, TxOutCache cache, boolean modify);

	protected abstract List<TxOut> getReceivedList (List<String> addresses, long from);

	protected abstract TxOut getSourceReference (TxOut source);

	protected abstract void insertBlock (Blk b);

	protected abstract void insertHead (Head head);

	protected abstract Head updateHead (Head head);

	protected abstract Head retrieveHead (CachedHead cached);

	protected abstract Blk retrieveBlock (CachedBlock cached);

	protected abstract Blk retrieveBlockHeader (CachedBlock cached);

	protected abstract List<TxIn> getSpendList (List<String> addresses, long from);

	@Override
	public void addTrunkListener (TrunkListener listener)
	{
		trunkListener.add (listener);
	}

	protected static class CachedHead
	{
		private Long id;
		private CachedBlock last;
		private double chainWork;
		private int height;
		private CachedHead previous;
		private int previousHeight;
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

		public void setHeight (int height)
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

		public int getPreviousHeight ()
		{
			return previousHeight;
		}

		public void setPreviousHeight (int previousHeight)
		{
			this.previousHeight = previousHeight;
		}

	}

	protected static class CachedBlock
	{
		public CachedBlock (String hash, Long id, CachedBlock previous, long time, int height, int version)
		{
			this.hash = hash;
			this.id = id;
			this.previous = previous;
			this.time = time;
			this.height = height;
			this.version = version;
		}

		private final String hash;
		private final Long id;
		private final CachedBlock previous;
		private final long time;
		private final int height;
		private final int version;

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

		public int getHeight ()
		{
			return height;
		}

		public int getVersion ()
		{
			return version;
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
			if ( o instanceof CachedBlock )
			{
				return hash.equals (((CachedBlock) o).hash);
			}
			return false;
		}
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED, readOnly = true)
	public void cache (Chain chain, int size) throws ValidationException
	{
		this.chain = chain;

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
				cacheUTXO (size, cachedUTXO);
			}

			log.trace ("Cache filled.");
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	private boolean isBlockOnBranch (CachedBlock block, CachedHead branch, int untilHeight)
	{
		if ( branch.getBlocks ().contains (block) )
		{
			return block.getHeight () <= untilHeight;
		}
		if ( branch.getPrevious () == null )
		{
			return false;
		}
		return isBlockOnBranch (block, branch.getPrevious (), branch.getPreviousHeight ());
	}

	private boolean isOnTrunk (String block)
	{
		CachedBlock b = cachedBlocks.get (block);
		return isBlockOnBranch (b, currentHead, (int) currentHead.getHeight ());
	}

	private boolean isSuperMajority (int minVersion, CachedBlock from, int nRequired, int nToCheck)
	{
		int nFound = 0;
		for ( int i = 0; i < nToCheck && nFound < nRequired && from != null; i++ )
		{
			if ( from.version >= minVersion )
			{
				++nFound;
			}
			from = from.previous;
		}
		return (nFound >= nRequired);
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
				if ( locator.contains (curr.getHash ()) || curr.getHeight () < 1 )
				{
					break;
				}
				inventory.add (0, curr.getHash ());
				if ( inventory.size () > limit )
				{
					inventory.remove (limit);
				}
				curr = prev;
				prev = curr.getPrevious ();
			} while ( prev != null );
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

	@Override
	public long getPeriodLength (String previousHash, int reviewPeriod)
	{
		try
		{
			lock.readLock ().lock ();

			CachedBlock cachedPrevious = cachedBlocks.get (previousHash);

			return computePeriodLength (cachedPrevious, cachedPrevious.getTime (), reviewPeriod);
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
		TxOutCache resolvedInputs = new ImplementTxOutCache ();
	}

	@Override
	public void storeBlock (final Blk b) throws ValidationException
	{
		try
		{
			// have to lock before transaction starts and unlock after finished.
			// otherwise updates to transient vs. persistent structures are out of sync for a
			// concurrent tx. This does not apply to methods using MANDATORY transaction annotation
			// context since they must have been invoked within a transaction.
			lock.writeLock ().lock ();

			ValidationException e = new TransactionTemplate (transactionManager).execute (new TransactionCallback<ValidationException> ()
			{
				@Override
				public ValidationException doInTransaction (TransactionStatus status)
				{
					try
					{
						startBatch ();
						lockedStoreBlock (b);
						endBatch ();
					}
					catch ( ValidationException e )
					{
						cancelBatch ();
						status.setRollbackOnly ();
						return e;
					}
					catch ( Exception e )
					{
						cancelBatch ();
						return new ValidationException ("OTHER exception " + b.toWireDump (), e);
					}
					return null;
				}
			});
			if ( e != null )
			{
				throw e;
			}
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
		if ( cachedPrevious == null )
		{
			throw new ValidationException ("Does not connect to a known block " + b.getHash ());
		}
		Blk prev = null;
		prev = retrieveBlockHeader (cachedPrevious);

		if ( b.getCreateTime () > (System.currentTimeMillis () / 1000) * 2 * 60 * 60 )
		{
			throw new ValidationException ("Future generation attempt " + b.getHash ());
		}

		if ( chain.isProduction () )
		{
			if ( enforceV2Block || isSuperMajority (2, cachedPrevious, 950, 1000) )
			{
				if ( !enforceV2Block )
				{
					log.trace ("Majority for V2 blocks reached, enforcing");
				}
				enforceV2Block = true;
				checkV2BlockCoinBase = true;
				if ( b.getVersion () < 2 )
				{
					throw new ValidationException ("Rejecting version 1 block " + b.getHash ());
				}
			}
			if ( checkV2BlockCoinBase || isSuperMajority (2, cachedPrevious, 750, 1000) )
			{
				checkV2BlockCoinBase = true;
			}
		}

		Head head;

		CachedHead cachedPreviousHead = cachedHeads.get (prev.getHeadId ());
		Head previousHead = retrieveHead (cachedPreviousHead);

		if ( previousHead.getLeaf ().equals (prev.getHash ()) )
		{
			// continuing
			head = previousHead;

			head.setLeaf (b.getHash ());
			head.setHeight (head.getHeight () + 1);
			head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget (), chain));
			head = updateHead (head);
		}
		else
		{
			// branching
			head = new Head ();

			head.setPreviousId (prev.getHeadId ());
			head.setPreviousHeight (prev.getHeight ());
			head.setLeaf (b.getHash ());
			head.setHeight (prev.getHeight () + 1);
			head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget (), chain));
			insertHead (head);
		}
		b.setHeadId (head.getId ());
		b.setHeight (head.getHeight ());
		b.setChainWork (head.getChainWork ());

		if ( b.getHeight () >= chain.getDifficultyReviewBlocks () && b.getHeight () % chain.getDifficultyReviewBlocks () == 0 )
		{
			long periodLength = computePeriodLength (cachedPrevious, prev.getCreateTime (), chain.getDifficultyReviewBlocks ());

			long next = Difficulty.getNextTarget (periodLength, prev.getDifficultyTarget (), chain);
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

		if ( chain.isProduction () && checkPoints.containsKey (b.getHeight ()) )
		{
			if ( !checkPoints.get (b.getHeight ()).equals (b.getHash ()) )
			{
				throw new ValidationException ("Checkpoint missed");
			}
		}

		BigInteger hashAsInteger = new Hash (b.getHash ()).toBigInteger ();
		if ( chain.isProduction () && hashAsInteger.compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) > 0 )
		{
			throw new ValidationException ("Insufficuent proof of work for current difficulty " + b.getHash () + " " + b.toWireDump ());
		}

		b.parseTransactions ();

		if ( b.getTransactions ().isEmpty () )
		{
			throw new ValidationException ("Block must have transactions " + b.getHash () + " " + b.toWireDump ());
		}

		b.checkMerkleRoot ();

		ImplementTxOutCacheDelta deltaUTXO = new ImplementTxOutCacheDelta (cachedUTXO);

		CachedBlock trunkBlock = cachedPrevious;
		List<CachedBlock> pathFromTrunkToPrev = new ArrayList<CachedBlock> ();
		while ( !isOnTrunk (trunkBlock.getHash ()) )
		{
			pathFromTrunkToPrev.add (trunkBlock);
			trunkBlock = trunkBlock.getPrevious ();
		}
		Collections.reverse (pathFromTrunkToPrev);

		if ( trunkBlock.getHeight () < (currentHead.getLast ().getHeight () - FORCE_TRUNK) )
		{
			throw new ValidationException ("Attempt to build on or create a branch too far back in history");
		}

		if ( currentHead.getLast () != cachedPrevious )
		{
			CachedBlock q = currentHead.getLast ();
			CachedBlock p = q.previous;
			while ( !q.getHash ().equals (trunkBlock.getHash ()) )
			{
				Blk block = retrieveBlock (q);
				backwardCache (block, deltaUTXO, false);

				q = p;
				p = q.previous;
			}

			for ( CachedBlock block : pathFromTrunkToPrev )
			{
				forwardCache (retrieveBlock (block), deltaUTXO, false);
			}
		}

		final TransactionContext tcontext = new TransactionContext ();
		tcontext.block = b;
		tcontext.resolvedInputs = deltaUTXO;

		log.trace ("resolving inputs for block " + b.getHash ());
		for ( Tx t : b.getTransactions () )
		{
			resolveInputs (tcontext.resolvedInputs, b.getHeight (), t);
			for ( TxOut o : t.getOutputs () )
			{
				tcontext.resolvedInputs.add (o);
			}
		}
		if ( !chain.isProduction () || b.getHeight () > lastCheckPoint || chain.isUnitTest () )
		{
			log.trace ("validating block " + b.getHash ());
			List<Callable<TransactionValidationException>> callables = new ArrayList<Callable<TransactionValidationException>> ();
			for ( final Tx t : b.getTransactions () )
			{
				if ( tcontext.coinbase )
				{
					if ( !isCoinBase (t) )
					{
						throw new ValidationException ("The first transaction of a block must be coinbase");
					}
					try
					{
						if ( checkV2BlockCoinBase
								&& ScriptFormat.intValue (ScriptFormat.parse (t.getInputs ().get (0).getScript ()).get (0).data) != b.getHeight () )
						{
							throw new ValidationException ("Block height mismatch in coinbase");
						}
						validateTransaction (tcontext, t);
					}
					catch ( TransactionValidationException e )
					{
						throw new ValidationException (e.getMessage () + " " + t.toWireDump (), e);
					}
					tcontext.coinbase = false;
				}
				else
				{
					if ( isCoinBase (t) )
					{
						throw new ValidationException ("Only the first transaction of a block can be coinbase");
					}
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
							throw new ValidationException (e.get ().getMessage () + " " + e.get ().getIn () + " " + e.get ().getTx ().toWireDump (), e.get ());
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
		if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
		{
			throw new ValidationException ("too many signatures in this block ");
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
		CachedBlock m =
				new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime (), b.getHeight (), (int) b.getVersion ());
		cachedBlocks.put (b.getHash (), m);

		CachedHead usingHead = cachedHeads.get (head.getId ());
		if ( usingHead == null )
		{
			cachedHeads.put (head.getId (), usingHead = new CachedHead ());
			usingHead.id = head.getId ();
			usingHead.previous = cachedHeads.get (head.getPreviousId ());
			usingHead.previousHeight = b.getHeight () - 1;
		}
		usingHead.setChainWork (b.getChainWork ());
		usingHead.setHeight (b.getHeight ());
		usingHead.getBlocks ().add (m);

		final List<Blk> removedBlocks = new ArrayList<Blk> ();
		final List<Blk> addedBlocks = new ArrayList<Blk> ();

		if ( usingHead.getChainWork () > currentHead.getChainWork () )
		{
			// we have a new trunk
			CachedBlock p = currentHead.getLast ();
			CachedBlock q = p.previous;
			while ( !p.equals (trunkBlock) )
			{
				Blk block = retrieveBlock (p);
				backwardCache (block, cachedUTXO, true);
				removedBlocks.add (block);
				p = q;
				q = p.previous;
			}
			List<CachedBlock> pathToNewHead = new ArrayList<CachedBlock> ();
			p = m;
			q = p.previous;
			while ( !q.equals (trunkBlock) )
			{
				pathToNewHead.add (q);
				p = q;
				q = p.previous;
			}
			Collections.reverse (pathToNewHead);

			for ( CachedBlock cb : pathToNewHead )
			{
				Blk block = retrieveBlock (cb);
				forwardCache (block, cachedUTXO, true);
				addedBlocks.add (block);
			}

			forwardCache (b, cachedUTXO, true);
			addedBlocks.add (b);
			currentHead = usingHead;
		}
		else if ( currentHead.getLast () == cachedPrevious )
		{
			forwardCache (b, cachedUTXO, true);
			addedBlocks.add (b);
			currentHead = usingHead;
		}

		usingHead.setLast (m);

		log.info ("stored block " + b.getHeight () + " " + b.getHash ());
		if ( !removedBlocks.isEmpty () || !addedBlocks.isEmpty () )
		{
			for ( TrunkListener l : trunkListener )
			{
				l.trunkUpdate (removedBlocks, addedBlocks);
			}
		}
	}

	private long computePeriodLength (CachedBlock cachedPrevious, long previousTime, int reviewPeriod)
	{
		long periodLength = previousTime;

		CachedBlock c = null;
		CachedBlock p = cachedPrevious;
		for ( int i = 0; i < reviewPeriod - 1; ++i )
		{
			c = p;
			p = c.getPrevious ();
		}
		periodLength -= p.getTime ();

		return periodLength;
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
				resolvedInputs.add (o);
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
				if ( height != 0 && out.isCoinbase () && !chain.isUnitTest () )
				{
					if ( out.getHeight () > height - COINBASE_MATURITY )
					{
						throw new ValidationException ("coinbase spent too early " + t.toWireDump ());
					}
				}
			}
		}
	}

	private boolean checkForRelay (Tx t, TxOutCache resolvedInputs) throws ValidationException
	{
		if ( t.getVersion () != 1 )
		{
			throw new ValidationException ("Transaction version must be 1");
		}
		if ( t.getInputs () == null || t.getInputs ().isEmpty () )
		{
			throw new TransactionValidationException ("a transaction must have inputs", t);
		}
		if ( t.getOutputs () == null || t.getOutputs ().isEmpty () )
		{
			throw new TransactionValidationException ("a transaction must have outputs", t);
		}

		long fee = 0;
		for ( TxIn in : t.getInputs () )
		{
			if ( in.getScript ().length > 500 )
			{
				throw new ValidationException ("script length limit exceeded");
			}
			if ( !ScriptFormat.isPushOnly (in.getScript ()) )
			{
				throw new ValidationException ("input script should be push only");
			}
			fee += resolvedInputs.get (in.getSourceHash (), in.getIx ()).getValue ();
		}
		for ( TxOut out : t.getOutputs () )
		{
			if ( !ScriptFormat.isStandard (out.getScript ()) )
			{
				throw new ValidationException ("not a standard output script");
			}
			if ( out.getValue () == 0 )
			{
				throw new ValidationException ("zero output");
			}
			fee -= out.getValue ();
		}

		// This node will not relay transactions not paying a minimal fee.
		WireFormat.Writer writer = new WireFormat.Writer ();
		t.toWire (writer);
		return fee >= Math.max (MIN_RELAY_TX_FEE, writer.toByteArray ().length / 1024 * KB_RELAY_TX_FEE);
	}

	private boolean isCoinBase (Tx t)
	{
		return t.getInputs ().size () == 1 && t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH_STRING);
	}

	private void validateTransaction (final TransactionContext tcontext, final Tx t) throws TransactionValidationException
	{
		if ( t.getInputs () == null || t.getInputs ().isEmpty () )
		{
			throw new TransactionValidationException ("a transaction must have inputs", t);
		}
		if ( t.getOutputs () == null || t.getOutputs ().isEmpty () )
		{
			throw new TransactionValidationException ("a transaction must have outputs", t);
		}

		if ( isCoinBase (t) )
		{
			if ( tcontext.block == null )
			{
				throw new TransactionValidationException ("coinbase only allowed in a block", t);
			}
			if ( t.getInputs ().get (0).getScript ().length < 2 || t.getInputs ().get (0).getScript ().length > 100 )
			{
				throw new TransactionValidationException ("coinbase script length out of bounds", t);
			}
		}

		long sumOut = 0;
		for ( TxOut o : t.getOutputs () )
		{
			if ( o.getValue () < 0 )
			{
				throw new TransactionValidationException ("negative output is not allowed", t);
			}
			if ( o.getValue () > Tx.MAX_MONEY )
			{
				throw new TransactionValidationException ("output too high", t);
			}
			synchronized ( tcontext )
			{
				tcontext.nsigs += ScriptFormat.sigOpCount (o.getScript (), false);
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
			}
			sumOut += o.getValue ();
		}

		if ( isCoinBase (t) == false )
		{
			long sumIn = 0;
			int inNumber = 0;
			List<Callable<TransactionValidationException>> callables = new ArrayList<Callable<TransactionValidationException>> ();
			Map<String, HashSet<Long>> inputUse = new HashMap<String, HashSet<Long>> ();
			for ( TxIn i : t.getInputs () )
			{
				HashSet<Long> seen = inputUse.get (i.getSourceHash ());
				if ( seen == null )
				{
					inputUse.put (i.getSourceHash (), seen = new HashSet<Long> ());
				}
				if ( seen.contains (i.getIx ()) )
				{
					throw new TransactionValidationException ("duplicate input", t);
				}
				seen.add (i.getIx ());

				TxOut source = tcontext.resolvedInputs.get (i.getSourceHash (), i.getIx ());
				sumIn += source.getValue ();
				try
				{
					synchronized ( tcontext )
					{
						if ( ScriptFormat.isPayToScriptHash (source.getScript ()) )
						{
							ScriptFormat.Tokenizer tokenizer = new ScriptFormat.Tokenizer (i.getScript ());
							byte[] last = null;
							while ( tokenizer.hashMoreElements () )
							{
								last = tokenizer.nextToken ().data;
							}
							tcontext.nsigs += ScriptFormat.sigOpCount (last, true);
						}
						else
						{
							tcontext.nsigs += ScriptFormat.sigOpCount (i.getScript (), false);
						}
						tcontext.blkSumInput = tcontext.blkSumInput.add (BigInteger.valueOf (source.getValue ()));
					}
				}
				catch ( ValidationException e )
				{
					throw new TransactionValidationException (e, t);
				}

				final ScriptEvaluation evaluation = new ScriptEvaluation (t, inNumber++, source);
				callables.add (new Callable<TransactionValidationException> ()
				{
					@Override
					public TransactionValidationException call () throws Exception
					{
						try
						{
							if ( !evaluation.evaluate (chain.isProduction ()) )
							{
								return new TransactionValidationException ("The transaction script does not evaluate to true in input", t, evaluation.getInr ());
							}
						}
						catch ( Exception e )
						{
							return new TransactionValidationException (e, t, evaluation.getInr ());
						}
						return null;
					}
				});
			}
			if ( sumOut > sumIn )
			{
				throw new TransactionValidationException ("Transaction value out more than in", t);
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

	private void parseOwners (TxOut out)
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
		catch ( Exception e )
		{
			// parsing owner is a nice to have
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
		this.chain = chain;

		Blk genesis = chain.getGenesis ();
		TxOut out = genesis.getTransactions ().get (0).getOutputs ().get (0);
		parseOwners (out);
		Head h = new Head ();
		h.setLeaf (genesis.getHash ());
		h.setHeight (0);
		h.setChainWork (Difficulty.getDifficulty (genesis.getDifficultyTarget (), chain));
		insertHead (h);
		genesis.setHeadId (h.getId ());
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

	@Transactional (propagation = Propagation.MANDATORY, readOnly = true)
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

	@Transactional (propagation = Propagation.MANDATORY, readOnly = true)
	@Override
	public boolean validateTransaction (Tx t, TxOutCache resolvedInputs) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			if ( t.getInputs () == null )
			{
				throw new ValidationException ("a transaction must have inputs");
			}

			resolveInputs (resolvedInputs, 0, t);

			TransactionContext tcontext = new TransactionContext ();
			tcontext.block = null;
			tcontext.coinbase = false;
			tcontext.nsigs = 0;
			tcontext.resolvedInputs = resolvedInputs;

			boolean relay = !chain.isProduction () || checkForRelay (t, resolvedInputs);

			validateTransaction (tcontext, t);

			return relay;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}
}
