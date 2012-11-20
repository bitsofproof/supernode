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
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.Chain;
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
		gnuOptions.addOption ("f", "full", false, "Full validation");

		try
		{
			parser.parse (gnuOptions, args);
		}
		catch ( ParseException e1 )
		{
			log.error ("Invalid options ", e1);
			return;
		}
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				try
				{
					audit ();
				}
				catch ( Exception e )
				{
					log.error ("Exception in audit", e);
				}
			}
		});
	}

	public void audit () throws Exception
	{
		long ms = System.currentTimeMillis ();
		log.info ("Check 1. All heads lead to genesis");
		validateHeads ();
		log.info ("Check 1. Done in " + (System.currentTimeMillis () - ms) / 1000.0 + " seconds.");
		ms = System.currentTimeMillis ();
	}

	public void validateHeads ()
	{
		QHead head = QHead.head;
		JPAQuery q = new JPAQuery (entityManager);
		Set<String> checked = new HashSet<String> ();
		for ( Head h : q.from (head).list (head) )
		{
			QBlk blk = QBlk.blk;
			q = new JPAQuery (entityManager);
			Blk b = q.from (blk).where (blk.hash.eq (h.getLeaf ())).uniqueResult (blk);
			while ( b.getPrevious () != null && !checked.contains (b.getHash ()) )
			{
				b = b.getPrevious ();
				checked.add (b.getHash ());
			}
		}
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
