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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("jpastore")
class JpaChainStore implements ChainStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaChainStore.class);

	private static final long MAX_BLOCK_SIGOPS = 20000;

	@PersistenceContext
	private EntityManager entityManager;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock ();
	private CachedHead currentHead = null;
	private final Map<String, CachedHead> heads = new HashMap<String, CachedHead> ();
	private final Map<String, Member> members = new HashMap<String, Member> ();
	private final Map<BitcoinPeer, TreeSet<KnownMember>> knownByPeer = new HashMap<BitcoinPeer, TreeSet<KnownMember>> ();
	private final Map<BitcoinPeer, HashSet<String>> requestsByPeer = new HashMap<BitcoinPeer, HashSet<String>> ();
	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());
	private final Map<String, ArrayList<Blk>> pending = new HashMap<String, ArrayList<Blk>> ();

	private final ExecutorService inputProcessor = Executors.newCachedThreadPool ();

	private final Comparator<KnownMember> incomingOrder = new Comparator<KnownMember> ()
	{
		@Override
		public int compare (KnownMember arg0, KnownMember arg1)
		{
			int diff = arg0.nr - arg1.nr;
			if ( diff != 0 )
			{
				return diff;
			}
			else
			{
				return arg0.equals (arg1) ? 0 : arg0.hashCode () - arg1.hashCode ();
			}
		}
	};

	@Autowired
	PlatformTransactionManager transactionManager;

	public class CachedHead
	{
		private StoredMember last;
		private double chainWork;
		private long height;

		public StoredMember getLast ()
		{
			return last;
		}

		public double getChainWork ()
		{
			return chainWork;
		}

		public long getHeight ()
		{
			return height;
		}

		public void setLast (StoredMember last)
		{
			this.last = last;
		}

		public void setChainWork (double chainWork)
		{
			this.chainWork = chainWork;
		}

		public void setHeight (long height)
		{
			this.height = height;
		}

	}

	public class Member
	{
		protected String hash;

		public Member (String hash)
		{
			super ();
			this.hash = hash;
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

	public class StoredMember extends Member
	{
		public StoredMember (String hash, Long id, StoredMember previous, long time)
		{
			super (hash);
			this.id = id;
			this.previous = previous;
			this.time = time;
		}

		protected Long id;
		protected StoredMember previous;
		protected long time;

		public Long getId ()
		{
			return id;
		}

		public StoredMember getPrevious ()
		{
			return previous;
		}

		public long getTime ()
		{
			return time;
		}

	}

	public class KnownMember extends Member
	{
		protected Set<BitcoinPeer> knownBy;
		protected int nr;

		public KnownMember (String hash, int nr, Set<BitcoinPeer> knownBy)
		{
			super (hash);
			this.knownBy = knownBy;
			this.nr = nr;
		}

		public Set<BitcoinPeer> getKnownBy ()
		{
			return knownBy;
		}

		public int getNr ()
		{
			return nr;
		}

	}

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public void cache ()
	{
		log.trace ("filling chain cache with stored blocks");
		QBlk block = QBlk.blk;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Blk b : q.from (block).list (block) )
		{
			if ( b.getPrevious () != null )
			{
				members.put (b.getHash (),
						new StoredMember (b.getHash (), b.getId (), (StoredMember) members.get (b.getPrevious ().getHash ()), b.getCreateTime ()));
			}
			else
			{
				members.put (b.getHash (), new StoredMember (b.getHash (), b.getId (), null, b.getCreateTime ()));
			}
		}

		log.trace ("filling chain cache with heads");
		QHead head = QHead.head;
		q = new JPAQuery (entityManager);
		for ( Head h : q.from (head).list (head) )
		{
			CachedHead sh = new CachedHead ();
			sh.setChainWork (h.getChainWork ());
			sh.setHeight (h.getHeight ());
			sh.setLast ((StoredMember) members.get (h.getLeaf ()));
			heads.put (h.getLeaf (), sh);
			if ( currentHead == null || currentHead.getChainWork () < sh.getChainWork () )
			{
				currentHead = sh;
			}
		}
	}

	@Override
	public void addInventory (List<String> hashes, BitcoinPeer peer)
	{
		try
		{
			lock.writeLock ().lock ();

			for ( String hash : hashes )
			{
				Member cached = members.get (hash);
				if ( cached == null )
				{
					HashSet<BitcoinPeer> peers = new HashSet<BitcoinPeer> ();
					members.put (hash, cached = new KnownMember (hash, members.size (), peers));
				}
				else if ( !(cached instanceof KnownMember) )
				{
					continue;
				}
				((KnownMember) cached).getKnownBy ().add (peer);
				TreeSet<KnownMember> membersOfPeer = knownByPeer.get (peer);
				if ( membersOfPeer == null )
				{
					membersOfPeer = new TreeSet<KnownMember> (incomingOrder);
					knownByPeer.put (peer, membersOfPeer);
				}
				membersOfPeer.add ((KnownMember) cached);
			}
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	@Override
	public List<String> getRequests (BitcoinPeer peer)
	{
		try
		{
			lock.writeLock ().lock ();

			HashSet<String> requests = requestsByPeer.get (peer);
			if ( requests == null )
			{
				requests = new HashSet<String> ();
			}
			TreeSet<KnownMember> knownbyThisPeer = knownByPeer.get (peer);
			ArrayList<String> result = new ArrayList<String> ();
			if ( knownbyThisPeer != null )
			{
				for ( KnownMember kn : knownbyThisPeer )
				{
					requests.add (kn.getHash ());
					result.add (kn.getHash ());
				}
				requestsByPeer.put (peer, requests);
				knownbyThisPeer.clear ();
			}

			return result;
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	@Override
	public void removePeer (BitcoinPeer peer)
	{
		try
		{
			lock.writeLock ().lock ();

			requestsByPeer.remove (peer);
			TreeSet<KnownMember> ms = knownByPeer.get (peer);
			if ( ms != null )
			{
				for ( KnownMember m : ms )
				{
					m.getKnownBy ().remove (peer);
				}
			}
			knownByPeer.remove (peer);
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	@Override
	public List<String> getLocator ()
	{
		try
		{
			lock.readLock ().lock ();

			List<String> locator = new ArrayList<String> ();
			StoredMember curr = currentHead.getLast ();
			locator.add (curr.getHash ());
			StoredMember prev = curr.getPrevious ();
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

	private static class TransactionContext
	{
		String blockHash;
		int blockHeight;
		BigInteger blkSumInput = BigInteger.ZERO;
		BigInteger blkSumOutput = BigInteger.ZERO;
		int nsigs = 0;
		boolean coinbase = true;
		Map<String, Tx> blockTransactions = new HashMap<String, Tx> ();
	}

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public void storeBlock (Blk b) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			Member cached = members.get (b.getHash ());
			if ( cached instanceof StoredMember )
			{
				return;
			}
		}
		finally
		{
			lock.readLock ().unlock ();
		}

		try
		{
			lock.writeLock ().lock ();

			lockedStoreBlock (b);
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

	private void lockedStoreBlock (Blk b) throws ValidationException
	{
		boolean stored = false;

		Member cached = members.get (b.getHash ());
		if ( cached instanceof StoredMember )
		{
			return;
		}

		// find previous block
		Member cachedPrevious = members.get (b.getPreviousHash ());
		if ( cachedPrevious == null || !(cachedPrevious instanceof StoredMember) )
		{
			ArrayList<Blk> pl = pending.get (b.getPreviousHash ());
			if ( pl == null )
			{
				pl = new ArrayList<Blk> ();
				pending.put (b.getPreviousHash (), pl);
			}
			pl.add (b);
		}
		else
		{
			Blk prev = null;
			if ( cachedPrevious instanceof StoredMember )
			{
				prev = entityManager.find (Blk.class, ((StoredMember) cachedPrevious).getId ());
			}
			b.setPrevious (prev);

			if ( b.getCreateTime () > (System.currentTimeMillis () / 1000) * 2 * 60 * 60 )
			{
				throw new ValidationException ("Future generation attempt " + b.getHash ());
			}

			boolean branching = false;
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
				branching = true;
				head = new Head ();
				head.setTrunk (prev.getHash ());
				head.setHeight (prev.getHeight ());
				head.setChainWork (prev.getChainWork ());
				head.setPrevious (prev.getHead ());

				head.setLeaf (b.getHash ());
				head.setHeight (head.getHeight () + 1);
				head.setChainWork (prev.getChainWork () + Difficulty.getDifficulty (b.getDifficultyTarget ()));
				entityManager.persist (head);
			}
			b.setHead (head);
			b.setHeight (head.getHeight ());
			b.setChainWork (head.getChainWork ());

			if ( b.getHeight () >= 2016 && b.getHeight () % 2016 == 0 )
			{
				StoredMember c = null;
				StoredMember p = (StoredMember) cachedPrevious;
				for ( int i = 0; i < 2015; ++i )
				{
					c = p;
					p = c.getPrevious ();
				}

				long next = Difficulty.getNextTarget (prev.getCreateTime () - p.getTime (), prev.getDifficultyTarget ());
				if ( next != b.getDifficultyTarget () )
				{
					throw new ValidationException ("Difficulty does not match expectation " + b.getHash ());
				}
			}
			else
			{
				if ( b.getDifficultyTarget () != prev.getDifficultyTarget () )
				{
					throw new ValidationException ("Illegal attempt to change difficulty " + b.getHash ());
				}
			}
			if ( new Hash (b.getHash ()).toBigInteger ().compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) > 0 )
			{
				throw new ValidationException ("Insufficuent proof of work for current difficulty " + b.getHash ());
			}

			// do we have a new main chain ?
			if ( head.getChainWork () > currentHead.getChainWork () && currentHead.getLast () != cachedPrevious )
			{
				// find where we left off the previous main and blank out sinks thereafter
				StoredMember newMain = (StoredMember) cachedPrevious;
				StoredMember oldMain = currentHead.getLast ();
				while ( newMain != null )
				{
					while ( oldMain != null && oldMain != newMain )
					{
						oldMain = oldMain.getPrevious ();
					}
					if ( oldMain != null )
					{
						break;
					}
					newMain = newMain.getPrevious ();
					oldMain = (StoredMember) cachedPrevious;
				}
				if ( oldMain == null )
				{
					throw new ValidationException ("chain corruption " + newMain.getHash () + "is an orphan branch");
				}
				StoredMember top = (StoredMember) cachedPrevious;
				while ( top != oldMain )
				{
					Blk nb = entityManager.find (Blk.class, top.getId ());
					for ( Tx tx : nb.getTransactions () )
					{
						for ( TxOut o : tx.getOutputs () )
						{
							o.setSink (null);
							entityManager.merge (o);
						}
					}
					top = top.getPrevious ();
				}
			}

			b.parseTransactions ();

			if ( b.getTransactions ().isEmpty () )
			{
				throw new ValidationException ("Block must have transactions " + b.getHash ());
			}

			b.checkMerkleRoot ();

			TransactionContext tcontext = new TransactionContext ();
			tcontext.blockHash = b.getHash ();
			tcontext.blockHeight = b.getHeight ();

			for ( Tx t : b.getTransactions () )
			{
				tcontext.blockTransactions.put (t.getHash (), t);
				validateTransaction (tcontext, t);
			}

			// block reward could actually be less... as in 0000000000004c78956f8643262f3622acf22486b120421f893c0553702ba7b5
			if ( tcontext.blkSumOutput.subtract (tcontext.blkSumInput).longValue () > ((50L * Tx.COIN) >> (b.getHeight () / 210000L)) )
			{
				throw new ValidationException ("Invalid block reward " + b.getHash ());
			}

			// now check if transactions were already known
			boolean seen = false;
			List<Tx> tl = b.getTransactions ();
			for ( int i = 0; i < tl.size (); ++i )
			{
				Tx t = tl.get (i);

				QTx tx = QTx.tx;
				JPAQuery query = new JPAQuery (entityManager);
				Tx st = query.from (tx).where (tx.hash.eq (t.getHash ())).uniqueResult (tx);
				if ( st != null )
				{
					tl.set (i, st);
					seen = true;
				}
			}

			if ( seen )
			{
				b = entityManager.merge (b);
			}
			else
			{
				entityManager.persist (b);
			}
			stored = true;

			for ( Tx t : b.getTransactions () )
			{
				for ( TxIn i : t.getInputs () )
				{
					if ( i.getSource () != null )
					{
						i.getSource ().setSink (i);
						entityManager.merge (i.getSource ());
					}
				}
			}

			StoredMember m = new StoredMember (b.getHash (), b.getId (), (StoredMember) members.get (b.getPrevious ().getHash ()), b.getCreateTime ());
			members.put (b.getHash (), m);

			CachedHead usingHead = currentHead;
			if ( branching )
			{
				heads.put (b.getHash (), usingHead = new CachedHead ());
			}
			if ( head.getChainWork () > currentHead.getChainWork () )
			{
				currentHead = usingHead;
			}

			usingHead.setLast (m);
			usingHead.setChainWork (head.getChainWork ());
			usingHead.setHeight (head.getHeight ());

			log.trace ("stored block " + b.getHash ());

			for ( Tx t : b.getTransactions () )
			{
				unconfirmed.remove (t.getHash ());
			}
		}
		for ( TreeSet<KnownMember> k : knownByPeer.values () )
		{
			k.remove (cached);
		}

		List<BitcoinPeer> finishedPeer = new ArrayList<BitcoinPeer> ();
		for ( Map.Entry<BitcoinPeer, HashSet<String>> e : requestsByPeer.entrySet () )
		{
			e.getValue ().remove (b.getHash ());
			if ( e.getValue ().size () == 0 )
			{
				finishedPeer.add (e.getKey ());
			}
		}
		for ( BitcoinPeer p : finishedPeer )
		{
			requestsByPeer.remove (p);
		}

		if ( stored )
		{
			List<Blk> pendingOnThis = pending.get (b.getHash ());
			if ( pendingOnThis != null )
			{
				for ( Blk o : pendingOnThis )
				{
					try
					{
						lockedStoreBlock (o);
					}
					catch ( ValidationException e )
					{
						log.error ("Dropping invalid pending block ", e);
					}
				}
				pending.remove (b.getHash ());
			}
		}
	}

	private void validateTransaction (final TransactionContext tcontext, final Tx t) throws ValidationException
	{
		if ( tcontext.coinbase )
		{
			if ( t.getInputs ().size () != 1 || !t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ())
					|| t.getInputs ().get (0).getSequence () != 0xFFFFFFFFL )
			{
				throw new ValidationException ("first transaction must be coinbase " + tcontext.blockHash);
			}
			if ( t.getInputs ().get (0).getScript ().length > 100 || t.getInputs ().get (0).getScript ().length < 2 )
			{
				throw new ValidationException ("coinbase scriptsig must be in 2-100 " + tcontext.blockHash);
			}
			tcontext.coinbase = false;
			for ( TxOut o : t.getOutputs () )
			{
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
				tcontext.nsigs += Script.sigOpCount (o.getScript ());
			}
			if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
			{
				throw new ValidationException ("too many signatures in this block " + tcontext.blockHash);
			}
		}
		else
		{
			if ( t.getInputs ().size () == 1 && t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ()) )
			{
				throw new ValidationException ("only the first transaction can be coinbase");
			}
			if ( t.getOutputs ().isEmpty () )
			{
				throw new ValidationException ("Transaction must have outputs " + t.toJSON ());
			}
			if ( t.getInputs ().isEmpty () )
			{
				throw new ValidationException ("Transaction must have inputs " + t.toJSON ());
			}

			long sumOut = 0;
			for ( TxOut o : t.getOutputs () )
			{
				if ( o.getScript ().length > 520 )
				{
					if ( tcontext.blockHeight < 80000 )
					{
						log.trace ("Old DoD at [" + tcontext.blockHeight + "]" + tcontext.blockHash);
					}
					else
					{
						throw new ValidationException ("script too long " + t.toJSON ());
					}
				}
				tcontext.nsigs += Script.sigOpCount (o.getScript ());
				if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
				{
					throw new ValidationException ("too many signatures in this block " + tcontext.blockHash);
				}
				if ( o.getValue () < 0 || o.getValue () > Tx.MAX_MONEY )
				{
					throw new ValidationException ("Transaction output not in money range " + t.toJSON ());
				}
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
				sumOut += o.getValue ();
				if ( sumOut < 0 || sumOut > Tx.MAX_MONEY )
				{
					throw new ValidationException ("Transaction output not in money range " + t.toJSON ());
				}
			}

			long sumIn = 0;
			int inNumber = 0;
			List<Callable<ValidationException>> callables = new ArrayList<Callable<ValidationException>> ();
			for ( final TxIn i : t.getInputs () )
			{
				if ( i.getScript ().length > 520 )
				{
					if ( tcontext.blockHeight < 80000 )
					{
						log.trace ("Old DoD at [" + tcontext.blockHeight + "]" + tcontext.blockHash);
					}
					else
					{
						throw new ValidationException ("script too long " + t.toJSON ());
					}
				}
				if ( tcontext.blockHeight > 140000 && !Script.isPushOnly (i.getScript ()) )
				{
					throw new ValidationException ("input script should be push only " + t.toJSON ());
				}

				if ( i.getSource () == null )
				{
					Tx sourceTransaction;
					TxOut transactionOutput = null;
					if ( (sourceTransaction = tcontext.blockTransactions.get (i.getSourceHash ())) != null )
					{
						if ( i.getIx () < sourceTransaction.getOutputs ().size () )
						{
							transactionOutput = sourceTransaction.getOutputs ().get ((int) i.getIx ());
						}
						else
						{
							throw new ValidationException ("Transaction refers to output number not available " + t.toJSON ());
						}
					}
					else
					{
						QTx tx = QTx.tx;
						QTxOut txout = QTxOut.txOut;
						JPAQuery query = new JPAQuery (entityManager);

						transactionOutput =
								query.from (txout).join (txout.transaction, tx).where (tx.hash.eq (i.getSourceHash ()).and (txout.ix.eq (i.getIx ())))
										.orderBy (tx.id.desc ()).limit (1).uniqueResult (txout);
					}
					if ( transactionOutput == null )
					{
						throw new ValidationException ("Transaction input refers to unknown output " + t.toJSON ());
					}
					if ( transactionOutput.getSink () != null )
					{
						throw new ValidationException ("Double spending attempt " + t.toJSON ());
					}
					if ( transactionOutput.getTransaction ().getInputs ().get (0).getSource () == null )
					{
						QBlk blk = QBlk.blk;
						QTx tx = QTx.tx;

						JPAQuery query = new JPAQuery (entityManager);
						Blk origin =
								query.from (blk).join (blk.transactions, tx).where (tx.hash.eq (transactionOutput.getTransaction ().getHash ()))
										.orderBy (blk.createTime.desc ()).limit (1).uniqueResult (blk);
						if ( origin.getHeight () > (tcontext.blockHeight - 100) )
						{
							throw new ValidationException ("coinbase spent too early " + t.toJSON ());
						}
					}

					i.setSource (transactionOutput);
				}
				sumIn += i.getSource ().getValue ();

				final int nr = inNumber;
				callables.add (new Callable<ValidationException> ()
				{
					@Override
					public ValidationException call () throws Exception
					{
						try
						{
							if ( !new Script (t, nr).evaluate () )
							{
								throw new ValidationException ("The transaction script does not evaluate to true in input: " + nr + " " + t.toJSON ()
										+ " source transaction: " + i.getSource ().getTransaction ().toJSON ());
							}

							synchronized ( tcontext )
							{
								tcontext.blkSumInput = tcontext.blkSumInput.add (BigInteger.valueOf (i.getSource ().getValue ()));
							}
						}
						catch ( ValidationException e )
						{
							return e;
						}
						catch ( Exception e )
						{
							return new ValidationException (e);
						}
						return null;
					}
				});
				++inNumber;
			}
			List<Future<ValidationException>> results;
			try
			{
				results = inputProcessor.invokeAll (callables);
			}
			catch ( InterruptedException e1 )
			{
				throw new ValidationException ("interrupted", e1);
			}
			for ( Future<ValidationException> r : results )
			{
				try
				{
					if ( r.get () != null )
					{
						throw r.get ();
					}
				}
				catch ( InterruptedException e )
				{
					throw new ValidationException ("interrupted", e);
				}
				catch ( ExecutionException e )
				{
					throw new ValidationException ("The executor is corrupt", e);
				}
				catch ( Exception e )
				{
					throw new ValidationException (e);
				}
			}
			if ( sumOut > sumIn )
			{
				throw new ValidationException ("Transaction value out more than in " + t.toJSON ());
			}
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

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public void resetStore (Chain chain)
	{
		Blk genesis = chain.getGenesis ();
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

		Member cached = null;
		try
		{
			lock.readLock ().lock ();
			cached = members.get (hash);
			if ( cached == null || !(cached instanceof StoredMember) )
			{
				return null;
			}
		}
		finally
		{
			lock.readLock ().unlock ();
		}
		return entityManager.find (Blk.class, ((StoredMember) cached).getId ());
	}

	@Override
	public long getChainHeight ()
	{
		try
		{
			lock.readLock ().lock ();
			CachedHead longest = null;
			for ( CachedHead h : heads.values () )
			{
				if ( longest == null || longest.getChainWork () < h.getChainWork () )
				{
					longest = h;
				}
			}
			return longest.getHeight ();
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	public int getNumberOfRequests (BitcoinPeer peer)
	{
		try
		{
			lock.readLock ().lock ();
			HashSet<String> s = requestsByPeer.get (peer);
			if ( s == null )
			{
				return 0;
			}
			return s.size ();
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Override
	@Transactional (propagation = Propagation.MANDATORY)
	public Tx getTransaction (String hash)
	{
		try
		{
			lock.readLock ().lock ();
			Tx t = unconfirmed.get (hash);
			if ( t != null )
			{
				return t;
			}
			QTx tx = QTx.tx;
			JPAQuery query = new JPAQuery (entityManager);
			return query.from (tx).where (tx.hash.eq (hash)).orderBy (tx.id.desc ()).limit (1).uniqueResult (tx);
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public boolean storeTransaction (Tx t, int aboutHeight) throws ValidationException
	{
		try
		{
			lock.writeLock ().lock ();

			if ( unconfirmed.containsKey (t.getHash ()) )
			{
				return true;
			}

			QTx tx = QTx.tx;
			JPAQuery query = new JPAQuery (entityManager);
			if ( query.from (tx).where (tx.hash.eq (t.getHash ())).orderBy (tx.id.desc ()).limit (1).uniqueResult (tx) != null )
			{
				return true;
			}

			TransactionContext tcontext = new TransactionContext ();
			tcontext.blockHash = "unconfirmed";
			tcontext.blockHeight = aboutHeight;
			tcontext.blockTransactions = unconfirmed;
			tcontext.coinbase = false;
			tcontext.nsigs = 0;
			validateTransaction (tcontext, t);
			unconfirmed.put (t.getHash (), t);
			return true;
		}
		catch ( Exception e )
		{
			return false;
		}
		finally
		{
			lock.writeLock ().unlock ();
		}
	}

}
