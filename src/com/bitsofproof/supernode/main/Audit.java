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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Head;
import com.bitsofproof.supernode.model.QBlk;
import com.bitsofproof.supernode.model.QHead;
import com.bitsofproof.supernode.model.TransactionValidationException;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("audit")
public class Audit
{
	private static final Logger log = LoggerFactory.getLogger (Audit.class);

	@Autowired
	private Chain chain;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	public void start (String[] args) throws IOException, TransactionValidationException
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you yet");
		gnuOptions.addOption ("u", "upto", true, "Checks upto n");

		CommandLine cl = null;
		int upto = Integer.MAX_VALUE;
		try
		{
			cl = parser.parse (gnuOptions, args);
			if ( cl.hasOption ('u') )
			{
				upto = Integer.valueOf (cl.getOptionValue ('c'));
			}
		}
		catch ( ParseException e1 )
		{
			log.error ("Invalid options ", e1);
			return;
		}
		audit (upto);
	}

	public void audit (int upto)
	{
		Set<String> reachableBlocks;
		if ( upto > 0 )
		{
			long ms = System.currentTimeMillis ();
			log.info ("Check 1. All paths lead to genesis");
			reachableBlocks = validateHeads ();
			log.info ("Check 1. Done in " + (System.currentTimeMillis () - ms) / 1000.0 + " seconds.");
			if ( upto > 1 )
			{
				ms = System.currentTimeMillis ();
				log.info ("Check 2. There are no orphan blocks");
				orphanChecks (reachableBlocks);
				log.info ("Check 2. Done in " + (System.currentTimeMillis () - ms) / 1000.0 + " seconds.");
				if ( upto > 2 )
				{
					ms = System.currentTimeMillis ();
					log.info ("Check 3. Sufficient proof of work on all blocks and correct cumulative work on all paths");
					powCheck ();
					log.info ("Check 3. Done in " + (System.currentTimeMillis () - ms) / 1000.0 + " seconds.");
					if ( upto > 3 )
					{
						ms = System.currentTimeMillis ();
						log.info ("Check 4. Check merkle root on all blocks");
						merkleRootCheck ();
						log.info ("Check 4. Done in " + (System.currentTimeMillis () - ms) / 1000.0 + " seconds.");
					}
				}
			}
		}
	}

	public Set<String> validateHeads ()
	{
		return new TransactionTemplate (transactionManager).execute (new TransactionCallback<Set<String>> ()
		{
			@Override
			public Set<String> doInTransaction (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
					QHead head = QHead.head;
					JPAQuery q = new JPAQuery (entityManager);
					Set<String> checked = new HashSet<String> ();
					final String genesisHash = chain.getGenesis ().getHash ();
					for ( Head h : q.from (head).list (head) )
					{
						QBlk blk = QBlk.blk;
						q = new JPAQuery (entityManager);
						Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
						while ( b.getPrevious () != null && !checked.contains (b.getHash ()) )
						{
							checked.add (b.getHash ());
							b = b.getPrevious ();
						}
						if ( !b.getHash ().equals (genesisHash) && !checked.contains (b.getHash ()) )
						{
							log.error ("Failed. " + h.getLeaf () + " is not connected to genesis");
						}
					}
					checked.add (genesisHash);
					return checked;
				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
				return null;
			}
		});
	}

	public void orphanChecks (final Set<String> reachable)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
					QBlk blk = QBlk.blk;
					JPAQuery q = new JPAQuery (entityManager);
					for ( Blk b : q.from (blk).list (blk) )
					{
						if ( !reachable.contains (b.getHash ()) )
						{
							log.error ("Failed. Block " + b.getHash () + " is orphan.");
						}
					}
				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
			}
		});
	}

	public void powCheck ()
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
					Set<Long> checked = new HashSet<Long> ();
					QHead head = QHead.head;
					JPAQuery q = new JPAQuery (entityManager);
					for ( Head h : q.from (head).list (head) )
					{
						List<Long> path = new ArrayList<Long> ();
						QBlk blk = QBlk.blk;
						q = new JPAQuery (entityManager);
						Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
						while ( b.getPrevious () != null )
						{
							path.add (b.getId ());
							b = b.getPrevious ();
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
									Blk p = b.getPrevious ();
									for ( int i = 0; i < chain.getDifficultyReviewBlocks () - 1; ++i )
									{
										c = p;
										p = c.getPrevious ();
									}
									difficultyTarget =
											Difficulty.getNextTarget (prev.getCreateTime () - p.getCreateTime (), prev.getDifficultyTarget (),
													chain.getTargetBlockTime ());
								}
								if ( difficultyTarget != b.getDifficultyTarget () )
								{
									log.error ("Wrong difficulty for block " + b.getHash () + ", should be " + difficultyTarget);
								}
							}
							cwork += Difficulty.getDifficulty (difficultyTarget);
							if ( b.getChainWork () != cwork )
							{
								log.error ("Wrong cummulative work for block " + b.getHash () + " should be " + cwork);
							}
							try
							{
								b.checkHash ();
							}
							catch ( ValidationException e )
							{
								log.error ("Block hash incorrect hash " + b.getHash ());
							}
							if ( new Hash (b.getHash ()).toBigInteger ().compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) > 0 )
							{
								throw new ValidationException ("Insufficuent proof of work for the difficulty in block " + b.getHash ());
							}

							++height;
							checked.add (b.getId ());
						}
						if ( cwork != h.getChainWork () )
						{
							log.error ("Cummulative work mismatch for head " + b.getHash () + " should be " + cwork);
						}
					}
				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
			}
		});
	}

	public void merkleRootCheck ()
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
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
						}
					}
				}
				catch ( Exception e )
				{
					log.error ("Exception", e);
				}
			}
		});
	}

	private static ApplicationContext context;

	public static ApplicationContext getApplicationContext ()
	{
		return context;
	}

	public static void main (String[] args)
	{
		try
		{
			log.info ("bitsofproof audit (c) 2012 Tamas Blummer tamas@bitsofproof.com");
			log.trace ("Spring context setup");
			context = new ClassPathXmlApplicationContext ("audit.xml");
			Audit application = context.getBean (Audit.class);
			application.start (args);
		}
		catch ( Exception e )
		{
			log.error ("Application", e);
		}
	}

}
