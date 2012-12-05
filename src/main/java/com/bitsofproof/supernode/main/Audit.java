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
package com.bitsofproof.supernode.main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.TransactionValidationException;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Head;
import com.bitsofproof.supernode.model.QBlk;
import com.bitsofproof.supernode.model.QHead;
import com.mysema.query.jpa.impl.JPAQuery;

public class Audit extends Main implements Main.App
{
	private static final Logger log = LoggerFactory.getLogger (Audit.class);

	@Autowired
	private Chain chain;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	static class Settings
	{
		@Option (name = "-h", aliases = "--help", usage = "I can't help you yet")
		boolean help;
		@Option (name = "-u", aliases = "--upto", usage = "Run checks upto n")
		Integer upto;
		@Option (name = "-f", aliases = "--from", usage = "Run from check n")
		Integer from;
		@Option (name = "-c", aliases = "--check", usage = "Run check n")
		Integer check;

		public int getStart ()
		{
			if ( check != null )
			{
				return check;
			}

			return from == null ? 1 : from;
		}

		public int getEnd ()
		{
			if ( check != null )
			{
				return check;
			}

			return upto == null ? Integer.MAX_VALUE : upto;
		}

		static Settings parse (String[] args) throws CmdLineException
		{
			Settings s = new Settings ();
			CmdLineParser parser = new CmdLineParser (s);
			parser.parseArgument (args);

			if ( s.help )
			{
				throw new CmdLineException (parser, "Help");
			}

			return s;
		}
	}

	public static void main (String[] args) throws Exception
	{
		try
		{
			loadContext (Profile.AUDIT).getBean (App.class).start (args);
		}
		catch ( CmdLineException cle )
		{
			cle.getParser ().printUsage (System.err);
		}
	}

	@Override
	public void start (String[] args) throws IOException, TransactionValidationException, NumberFormatException, CmdLineException
	{
		Settings settings = Settings.parse (args);
		audit (settings.getStart (), settings.getEnd ());
	}

	public void audit (final int from, final int to)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
					boolean passed = true;
					if ( from <= 1 && to >= 1 )
					{
						log.info ("Check 1. All paths lead to genesis...");
						passed = validateHeads ();
						if ( passed )
						{
							log.info ("Check 1. Passed.");
						}
					}
					if ( passed && from <= 2 && to >= 2 )
					{
						log.info ("Check 2. There are no unconnected blocks...");
						connectionCheck ();
						if ( passed )
						{
							log.info ("Check 2. Passed.");
						}
					}
					if ( passed && from <= 3 && to >= 3 )
					{
						log.info ("Check 3. Sufficient proof of work on all blocks and correct cumulative work on all paths...");
						passed = powCheck ();
						if ( passed )
						{
							log.info ("Check 3. Passed.");
						}
					}
					if ( passed && from <= 4 && to >= 4 )
					{
						log.info ("Check 4. Check transaction hashes and Merkle roots on all blocks...");
						passed = merkleRootCheck ();
						if ( passed )
						{
							log.info ("Check 4. Passed.");
						}
					}
					if ( passed )
					{
						log.info ("All requested checks PASSED.");
					}
					else
					{
						log.error ("A requested check FAILED.");
					}
				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
			}
		});
	}

	public boolean validateHeads ()
	{
		boolean passed = true;
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		Set<String> checked = new HashSet<String> ();
		final String genesisHash = chain.getGenesis ().getHash ();
		for ( Head h : q.from (head).list (head) )
		{
			QBlk blk = QBlk.blk;
			q = new JPAQuery (entityManager);
			Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
			while ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) && !checked.contains (b.getHash ()) )
			{
				checked.add (b.getHash ());
				q = new JPAQuery (entityManager);
				b = q.from (blk).where (blk.hash.eq (b.getPreviousHash ())).uniqueResult (blk);
			}
			if ( !b.getHash ().equals (genesisHash) && !checked.contains (b.getHash ()) )
			{
				log.error ("Failed. " + h.getLeaf () + " is not connected to genesis");
				passed = false;
			}
		}
		checked.add (genesisHash);
		return passed;
	}

	public boolean connectionCheck ()
	{
		boolean passed = true;
		final Set<String> reachable = new HashSet<String> ();
		reachable.add (chain.getGenesis ().getHash ());
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Head h : q.from (head).list (head) )
		{
			QBlk blk = QBlk.blk;
			q = new JPAQuery (entityManager);
			Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
			while ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) && !reachable.contains (b.getHash ()) )
			{
				reachable.add (b.getHash ());
				q = new JPAQuery (entityManager);
				b = q.from (blk).where (blk.hash.eq (b.getPreviousHash ())).uniqueResult (blk);
			}
		}
		QBlk blk = QBlk.blk;
		q = new JPAQuery (entityManager);
		for ( Blk b : q.from (blk).list (blk) )
		{
			if ( !reachable.contains (b.getHash ()) )
			{
				log.error ("Failed. Block " + b.getHash () + " is not connected to genesis.");
				passed = false;
			}
		}
		return passed;
	}

	public boolean powCheck ()
	{
		boolean passed = true;
		Set<Long> checked = new HashSet<Long> ();
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Head h : q.from (head).list (head) )
		{
			List<Long> path = new ArrayList<Long> ();
			QBlk blk = QBlk.blk;
			q = new JPAQuery (entityManager);
			Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
			while ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				path.add (b.getId ());
				q = new JPAQuery (entityManager);
				b = q.from (blk).where (blk.hash.eq (b.getPreviousHash ())).uniqueResult (blk);
			}
			Collections.reverse (path);

			double cwork = 1; // 1 is genesis
			long difficultyTarget = b.getDifficultyTarget ();
			int height = 1;
			b = null;
			for ( Long id : path )
			{
				Blk prev = b;
				if ( !checked.contains (id) )
				{
					b = entityManager.find (Blk.class, id);
					if ( b.getHeight () != height )
					{
						log.error ("Incorrect block height for " + b.getHash () + ", should be " + height);
					}
					if ( b.getHeight () >= chain.getDifficultyReviewBlocks () && b.getHeight () % chain.getDifficultyReviewBlocks () == 0 )
					{
						Blk c = null;
						q = new JPAQuery (entityManager);
						Blk p = q.from (blk).where (blk.hash.eq (b.getPreviousHash ())).uniqueResult (blk);
						for ( int i = 0; i < chain.getDifficultyReviewBlocks () - 1; ++i )
						{
							c = p;
							q = new JPAQuery (entityManager);
							p = q.from (blk).where (blk.hash.eq (c.getPreviousHash ())).uniqueResult (blk);
						}
						difficultyTarget =
								Difficulty.getNextTarget (prev.getCreateTime () - p.getCreateTime (), prev.getDifficultyTarget (), chain.getTargetBlockTime ());
					}
					if ( difficultyTarget != b.getDifficultyTarget () )
					{
						log.error ("Wrong difficulty for block " + b.getHash () + ", should be " + difficultyTarget);
						passed = false;
					}
				}
				cwork += Difficulty.getDifficulty (difficultyTarget);
				if ( b.getChainWork () != cwork )
				{
					log.error ("Wrong cummulative work for block " + b.getHash () + " should be " + cwork);
					passed = false;
				}
				try
				{
					b.checkHash ();
				}
				catch ( ValidationException e )
				{
					log.error ("Block hash incorrect hash " + b.getHash ());
					passed = false;
				}
				if ( new Hash (b.getHash ()).toBigInteger ().compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) > 0 )
				{
					log.error ("Insufficuent proof of work for the difficulty in block " + b.getHash ());
					passed = false;
				}

				++height;
				checked.add (b.getId ());
			}
			if ( cwork != h.getChainWork () )
			{
				log.error ("Cummulative work mismatch for head " + b.getHash () + " should be " + cwork);
				passed = false;
			}
		}
		return passed;
	}

	public boolean merkleRootCheck ()
	{
		boolean passed = true;
		QBlk blk = QBlk.blk;
		JPAQuery q = new JPAQuery (entityManager);
		for ( Blk b : q.from (blk).list (blk) )
		{
			try
			{
				b.checkMerkleRoot ();
				entityManager.detach (b);
			}
			catch ( ValidationException e )
			{
				log.error ("Failed. Block " + b.getHash () + " has incorrect merkle root");
				passed = false;
			}
		}
		return passed;
	}
}
