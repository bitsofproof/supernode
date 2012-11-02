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
import java.util.HashMap;
import java.util.HashSet;
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

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.Script.Opcode;
import com.bitsofproof.supernode.core.ValidationException;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("jpaBlockStore")
class JpaBlockStore implements BlockStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaBlockStore.class);

	private static final long MAX_BLOCK_SIGOPS = 20000;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	PlatformTransactionManager transactionManager;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock ();

	private CachedHead currentHead = null;
	private final Map<String, CachedHead> heads = new HashMap<String, CachedHead> ();
	private final Map<String, Member> members = new HashMap<String, Member> ();

	private final ExecutorService inputProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());
	private final ExecutorService transactionsProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());

	@Override
	public PlatformTransactionManager getTransactionManager ()
	{
		return transactionManager;
	}

	@Override
	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

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
	public boolean isStoredBlock (String hash)
	{
		try
		{
			lock.readLock ().lock ();

			Member cached = members.get (hash);
			return cached != null && cached instanceof StoredMember;
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
		Blk block;
		BigInteger blkSumInput = BigInteger.ZERO;
		BigInteger blkSumOutput = BigInteger.ZERO;
		int nsigs = 0;
		boolean coinbase = true;
		Map<String, ArrayList<TxOut>> transactionsOutputCache = new HashMap<String, ArrayList<TxOut>> ();
		Map<String, HashMap<Integer, TxOut>> resolvedInputs = new HashMap<String, HashMap<Integer, TxOut>> ();
	}

	@Transactional (propagation = Propagation.MANDATORY)
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
		Member cached = members.get (b.getHash ());
		if ( cached instanceof StoredMember )
		{
			return;
		}

		// find previous block
		Member cachedPrevious = members.get (b.getPreviousHash ());
		if ( cachedPrevious != null && cachedPrevious instanceof StoredMember )
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
					throw new ValidationException ("Difficulty does not match expectation " + b.getHash () + " " + b.toWireDump ());
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

			boolean skip = true;
			for ( Tx t : b.getTransactions () )
			{
				ArrayList<TxOut> outs = new ArrayList<TxOut> ();
				for ( int i = 0; i < t.getOutputs ().size (); ++i )
				{
					outs.add (t.getOutputs ().get (i));
				}
				tcontext.transactionsOutputCache.put (t.getHash (), outs);
				if ( skip ) // skip coinbase
				{
					skip = false;
				}
				else
				{
					resolveInputs (tcontext, t);
				}
			}

			List<Callable<ValidationException>> callables = new ArrayList<Callable<ValidationException>> ();
			for ( final Tx t : b.getTransactions () )
			{
				if ( tcontext.coinbase )
				{
					validateTransaction (tcontext, t);
				}
				else
				{
					callables.add (new Callable<ValidationException> ()
					{
						@Override
						public ValidationException call ()
						{
							try
							{
								validateTransaction (tcontext, t);
							}
							catch ( ValidationException e )
							{
								return e;
							}
							catch ( Exception e )
							{
								return new ValidationException ("Transaction validation faled " + t.toWireDump (), e);
							}
							return null;
						}
					});
				}
			}
			try
			{
				for ( Future<ValidationException> e : transactionsProcessor.invokeAll (callables) )
				{
					try
					{
						if ( e.get () != null )
						{
							throw e.get ();
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

			entityManager.persist (b);

			// modify transient caches only after persistent changes
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
		}
	}

	private void resolveInputs (TransactionContext tcontext, Tx t) throws ValidationException
	{
		HashMap<Integer, TxOut> resolved;
		if ( (resolved = tcontext.resolvedInputs.get (t.getHash ())) == null )
		{
			resolved = new HashMap<Integer, TxOut> ();
			tcontext.resolvedInputs.put (t.getHash (), resolved);
		}

		Set<String> inputtx = new HashSet<String> ();
		for ( final TxIn i : t.getInputs () )
		{
			ArrayList<TxOut> outs;
			if ( (outs = tcontext.transactionsOutputCache.get (i.getSourceHash ())) != null )
			{
				if ( i.getIx () >= outs.size () )
				{
					throw new ValidationException ("Transaction refers to output number not available " + t.toWireDump ());
				}
			}
			else
			{
				inputtx.add (i.getSourceHash ());
			}
		}

		if ( !inputtx.isEmpty () )
		{
			QTx tx = QTx.tx;
			QTxOut txout = QTxOut.txOut;
			JPAQuery query = new JPAQuery (entityManager);

			// find input transactions. Since tx hash is not unique ordering gets rid of older
			for ( TxOut out : query.from (tx).join (tx.outputs, txout).where (tx.hash.in (inputtx)).orderBy (tx.id.asc (), txout.ix.asc ()).list (txout) )
			{
				ArrayList<TxOut> cached = tcontext.transactionsOutputCache.get (out.getTransaction ().getHash ());
				if ( cached == null || cached.size () > out.getIx () ) // replace if more than one tx
				{
					cached = new ArrayList<TxOut> ();
					tcontext.transactionsOutputCache.put (out.getTransaction ().getHash (), cached);
				}
				cached.add (out);
			}
		}
		// check for double spending
		Set<TxOut> outs = new HashSet<TxOut> ();
		int nr = 0;
		for ( final TxIn i : t.getInputs () )
		{
			ArrayList<TxOut> cached;
			TxOut transactionOutput = null;
			if ( (cached = tcontext.transactionsOutputCache.get (i.getSourceHash ())) == null )
			{
				throw new ValidationException ("Transaction refers to unknown source " + i.getSourceHash () + " " + t.toWireDump ());
			}
			try
			{
				transactionOutput = cached.get ((int) i.getIx ());
			}
			catch ( Exception e )
			{
				throw new ValidationException ("Transaction refers to unknown input " + i.getSourceHash () + " [" + i.getIx () + "] " + t.toWireDump ());
			}
			if ( transactionOutput.getId () != null )
			{
				// double spend within same block will be caught by the sum in/out
				outs.add (transactionOutput);
			}
			resolved.put (nr++, transactionOutput);
		}

		// find previously spending blocks
		List<String> dsblocks = new ArrayList<String> ();
		if ( !outs.isEmpty () )
		{
			QTx tx = QTx.tx;
			QBlk blk = QBlk.blk;
			QTxIn txin = QTxIn.txIn;
			JPAQuery query = new JPAQuery (entityManager);
			dsblocks = query.from (blk).join (blk.transactions, tx).join (tx.inputs, txin).where (txin.source.in (outs)).list (blk.hash);
		}

		if ( tcontext.block == null )
		{
			if ( !dsblocks.isEmpty () )
			{
				throw new ValidationException ("Double spend attempt " + t.toWireDump ());
			}
		}
		else
		{
			if ( !dsblocks.isEmpty () )
			{
				// check if a block with double spend is reachable from this block
				StoredMember prev = (StoredMember) members.get (tcontext.block.getPrevious ().getHash ());
				while ( prev != null )
				{
					if ( dsblocks.contains (prev.getHash ()) )
					{
						throw new ValidationException ("Double spend attempt " + t.toWireDump ());
					}
					prev = prev.getPrevious ();
				}
			}

			// check coinbase spending
			for ( int j = 0; j < nr; ++j )
			{
				TxOut transactionOutput = resolved.get (j);
				if ( transactionOutput.getTransaction ().getInputs ().get (0).getSource () == null )
				{
					if ( tcontext.resolvedInputs.get (transactionOutput.getTransaction ().getHash ()) != null )
					{
						if ( tcontext.resolvedInputs.get (transactionOutput.getTransaction ().getHash ()).get (0) == null )
						{
							throw new ValidationException ("coinbase spent in same block " + t.toWireDump ());
						}
					}
					else
					{
						QBlk blk = QBlk.blk;
						QTx tx = QTx.tx;
						JPAQuery query = new JPAQuery (entityManager);
						Blk origin =
								query.from (blk).join (blk.transactions, tx).where (tx.hash.eq (transactionOutput.getTransaction ().getHash ()))
										.orderBy (blk.createTime.desc ()).limit (1).uniqueResult (blk);
						if ( origin.getHeight () > (tcontext.block.getHeight () - 100) )
						{
							throw new ValidationException ("coinbase spent too early " + t.toWireDump ());
						}
					}
				}
			}
		}
	}

	private void validateTransaction (final TransactionContext tcontext, final Tx t) throws ValidationException
	{
		if ( tcontext.block != null )
		{
			for ( TxOut out : t.getOutputs () )
			{
				addOwners (out);
			}
		}
		if ( tcontext.block != null && tcontext.coinbase )
		{
			if ( t.getInputs ().size () != 1 || !t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ())
					|| t.getInputs ().get (0).getSequence () != 0xFFFFFFFFL )
			{
				throw new ValidationException ("first transaction must be coinbase " + tcontext.block.getHash ());
			}
			if ( t.getInputs ().get (0).getScript ().length > 100 || t.getInputs ().get (0).getScript ().length < 2 )
			{
				throw new ValidationException ("coinbase scriptsig must be in 2-100 " + tcontext.block.getHash ());
			}
			tcontext.coinbase = false;
			for ( TxOut o : t.getOutputs () )
			{
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
				tcontext.nsigs += Script.sigOpCount (o.getScript ());
			}
			if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
			{
				throw new ValidationException ("too many signatures in this block " + tcontext.block.getHash ());
			}
		}
		else
		{
			if ( t.getInputs ().size () == 1 && t.getInputs ().get (0).getSourceHash ().equals (Hash.ZERO_HASH.toString ()) )
			{
				throw new ValidationException ("coinbase only first in a block");
			}
			if ( t.getOutputs ().isEmpty () )
			{
				throw new ValidationException ("Transaction must have outputs " + t.toWireDump ());
			}
			if ( t.getInputs ().isEmpty () )
			{
				throw new ValidationException ("Transaction must have inputs " + t.toWireDump ());
			}

			long sumOut = 0;
			for ( TxOut o : t.getOutputs () )
			{
				if ( o.getScript ().length > 520 )
				{
					if ( tcontext.block != null && tcontext.block.getHeight () < 80000 )
					{
						log.trace ("Old DoD at [" + tcontext.block.getHeight () + "]" + tcontext.block.getHash ());
					}
					else
					{
						throw new ValidationException ("script too long " + t.toWireDump ());
					}
				}
				if ( tcontext.block != null )
				{
					tcontext.nsigs += Script.sigOpCount (o.getScript ());
					if ( tcontext.nsigs > MAX_BLOCK_SIGOPS )
					{
						throw new ValidationException ("too many signatures in this block " + tcontext.block.getHash ());
					}
				}
				if ( o.getValue () < 0 || o.getValue () > Tx.MAX_MONEY )
				{
					throw new ValidationException ("Transaction output not in money range " + t.toWireDump ());
				}
				tcontext.blkSumOutput = tcontext.blkSumOutput.add (BigInteger.valueOf (o.getValue ()));
				sumOut += o.getValue ();
				if ( sumOut < 0 || sumOut > Tx.MAX_MONEY )
				{
					throw new ValidationException ("Transaction output not in money range " + t.toWireDump ());
				}
			}

			long sumIn = 0;
			int inNumber = 0;
			List<Callable<ValidationException>> callables = new ArrayList<Callable<ValidationException>> ();
			HashMap<Integer, TxOut> resolved = tcontext.resolvedInputs.get (t.getHash ());
			final Set<String> signatureCache = new HashSet<String> ();
			for ( final TxIn i : t.getInputs () )
			{
				if ( i.getScript ().length > 520 )
				{
					if ( tcontext.block != null && tcontext.block.getHeight () < 80000 )
					{
						log.trace ("Old DoD at [" + tcontext.block.getHeight () + "]" + tcontext.block.getHash ());
					}
					else
					{
						throw new ValidationException ("script too long " + t.toWireDump ());
					}
				}

				i.setSource (resolved.get (inNumber));
				sumIn += i.getSource ().getValue ();

				final int nr = inNumber;
				callables.add (new Callable<ValidationException> ()
				{
					@Override
					public ValidationException call () throws Exception
					{
						try
						{
							if ( !new Script (t, nr, signatureCache).evaluate () )
							{
								throw new ValidationException ("The transaction script does not evaluate to true in input: " + nr + "-"
										+ i.getSource ().getIx () + " " + t.toWireDump () + " source transaction: "
										+ i.getSource ().getTransaction ().toWireDump ());
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
				throw new ValidationException ("Transaction value out more than in " + t.toWireDump ());
			}
		}
	}

	private void addOwners (TxOut out) throws ValidationException
	{
		List<Owner> owners = new ArrayList<Owner> ();
		parseOwners (out.getScript (), out, owners);
		out.setOwners (owners);
	}

	private void parseOwners (byte[] script, TxOut out, List<Owner> owners) throws ValidationException
	{
		List<Script.Token> parsed = Script.parse (out.getScript ());
		if ( parsed.size () == 3 && parsed.get (0).data != null && parsed.get (1).data != null && parsed.get (2).op == Opcode.OP_CHECKSIG )
		{
			// pay to key
			Owner o = new Owner ();
			o.setHash (ByteUtils.toHex (Hash.keyHash (parsed.get (1).data)));
			o.setOutpoint (out);
			owners.add (o);
			out.setVotes (1L);
		}
		if ( parsed.size () == 5 && parsed.get (0).op == Opcode.OP_DUP && parsed.get (1).op == Opcode.OP_HASH160 && parsed.get (2).data != null
				&& parsed.get (3).op == Opcode.OP_EQUALVERIFY && parsed.get (4).op == Opcode.OP_CHECKSIG )
		{
			// pay to key
			Owner o = new Owner ();
			o.setHash (ByteUtils.toHex (parsed.get (2).data));
			o.setOutpoint (out);
			owners.add (o);
			out.setVotes (1L);
		}
		if ( parsed.size () == 3 && parsed.get (0).op == Opcode.OP_HASH160 && parsed.get (1).data != null && parsed.get (1).data.length == 20
				&& parsed.get (2).op == Opcode.OP_EQUAL )
		{
			// pay to script
			parseOwners (parsed.get (1).data, out, owners);
		}
		for ( int i = 0; i < parsed.size (); ++i )
		{
			if ( parsed.get (i).op == Opcode.OP_CHECKMULTISIG || parsed.get (i).op == Opcode.OP_CHECKMULTISIGVERIFY )
			{
				int nkeys = Script.toNumber (parsed.get (i - 1).data).intValue ();
				for ( int j = 0; j < nkeys; ++j )
				{
					Owner o = new Owner ();
					o.setHash (ByteUtils.toHex (Hash.keyHash (parsed.get (i - j - 2).data)));
					o.setOutpoint (out);
					owners.add (o);
				}
				out.setVotes (Script.toNumber (parsed.get (i - nkeys - 2).data).longValue ());
				return;
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

	@Transactional (propagation = Propagation.MANDATORY)
	@Override
	public boolean validateTransaction (Tx t) throws ValidationException
	{
		try
		{
			lock.readLock ().lock ();

			TransactionContext tcontext = new TransactionContext ();
			tcontext.block = null;
			tcontext.transactionsOutputCache = new HashMap<String, ArrayList<TxOut>> ();
			tcontext.coinbase = false;
			tcontext.nsigs = 0;
			resolveInputs (tcontext, t);
			validateTransaction (tcontext, t);
			return true;
		}
		finally
		{
			lock.readLock ().unlock ();
		}
	}

}
