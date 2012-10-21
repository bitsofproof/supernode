package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.bouncycastle.util.encoders.Hex;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.main.Setup;
import com.bitsofproof.supernode.model.ChainStore;
import com.bitsofproof.supernode.model.Tx;

public class ScriptTest
{
	private static Chain chain;
	private static ChainStore store;

	private static PlatformTransactionManager transactionManager;
	private static ApplicationContext context;
	private static EntityManagerFactory emf;

	@BeforeClass
	public static void setup ()
	{
		try
		{
			Setup.setup ();
			context = new ClassPathXmlApplicationContext ("app-context.xml");
			chain = context.getBean (Chain.class);
			store = context.getBean (ChainStore.class);
			transactionManager = context.getBean (PlatformTransactionManager.class);
			emf = context.getBean (EntityManagerFactory.class);
		}
		catch ( IOException e )
		{
			e.printStackTrace ();
		}
	}

	@Test
	public void stringTest ()
	{
		Script s = new Script ("OP_PUSH2 abcd");
		assertTrue (s.toString ().equals ("OP_PUSH2 abcd"));
	}

	@Test
	public void dataPushTest ()
	{
		Script s = new Script ("OP_PUSH3 0a0b0c OP_PUSHDATA1 03 0a0b0c OP_EQUALVERIFY");
		assertTrue (s.evaluate ());
	}

	@Test
	public void ifTest ()
	{
		assertTrue (new Script ("OP_1 OP_1 OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_IF OP_1 OP_ELSE OP_1 OP_ENDIF").evaluate ());
		assertFalse (new Script ("OP_1 OP_IF OP_FALSE OP_ELSE OP_1 OP_ENDIF OP_EQUALVERIFY").evaluate ());
		assertFalse (new Script ("OP_1 OP_IF OP_1 OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_NOTIF OP_FALSE OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_1 OP_EQUALVERIFY").evaluate ());
	}

	@Test
	public void stackTest ()
	{
		assertTrue (new Script ("OP_1 OP_TOALTSTACK OP_FALSE OP_FROMALTSTACK OP_1 OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_2 OP_SWAP OP_1 OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_2 OP_3 OP_1 OP_PICK OP_2 OP_EQUALVERIFY").evaluate ());
	}

	@Test
	public void mathTest ()
	{
		assertTrue (new Script ("OP_1 OP_2 OP_ADD OP_3 OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_3 OP_DUP OP_SUB OP_FALSE OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_5 OP_SUB OP_ABS OP_4 OP_EQUALVERIFY").evaluate ());
		assertTrue (new Script ("OP_1 OP_5 OP_MAX OP_2 OP_MIN OP_2 OP_EQUALVERIFY").evaluate ());
	}

	private static String toHex (byte[] b)
	{
		try
		{
			return new String (Hex.encode (b), "US-ASCII");
		}
		catch ( UnsupportedEncodingException e )
		{
			return null;
		}
	}

	@Test
	public void digestTest ()
	{
		byte[] b = { 'H', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd', '!' };
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			byte[] h = a.digest (b);

			assertTrue (new Script ("OP_PUSHDATA1 0" + Integer.toString (b.length, 16) + " " + toHex (b) + " OP_SHA256 OP_PUSHDATA1 20 " + toHex (h)
					+ " OP_EQUALVERIFY").evaluate ());
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
	}

	@Test
	public void transactionTest ()
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{

			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager (emf);
				Tx tx = entityManager.find (Tx.class, new Long (184));
				new Script (tx, 0).evaluate ();
			}
		});
	}
}
