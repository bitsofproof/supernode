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
package com.bitsofproof.supernode.test;

import java.io.IOException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.main.Setup;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.QBlk;
import com.mysema.query.jpa.impl.JPAQuery;

public class BlockTest
{
	private static PlatformTransactionManager transactionManager;
	private static ApplicationContext context;
	private static EntityManagerFactory emf;

	private static final boolean usedb = true;

	@BeforeClass
	public static void setup ()
	{
		try
		{
			if ( usedb )
			{
				Setup.setup ();
				context = new ClassPathXmlApplicationContext ("app-context.xml");
				transactionManager = context.getBean (PlatformTransactionManager.class);
				emf = context.getBean (EntityManagerFactory.class);
			}
		}
		catch ( IOException e )
		{
			e.printStackTrace ();
		}
	}

	@Test
	public void rewardTest ()
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{

			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager (emf);
				QBlk blk = QBlk.blk;

				JPAQuery query = new JPAQuery (entityManager);

				Blk b = query.from (blk).where (blk.hash.eq ("0000000000004c78956f8643262f3622acf22486b120421f893c0553702ba7b5")).uniqueResult (blk);
				System.out.println (b.toJSON ());
			}
		});
	}
}
