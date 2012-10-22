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

import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.main.Setup;
import com.bitsofproof.supernode.model.QTx;
import com.bitsofproof.supernode.model.Tx;
import com.mysema.query.jpa.impl.JPAQuery;

public class ScriptTest
{
	private static PlatformTransactionManager transactionManager;
	private static ApplicationContext context;
	private static EntityManagerFactory emf;

	@BeforeClass
	public static void setup ()
	{
		/*
		try
		{
			Setup.setup ();
			context = new ClassPathXmlApplicationContext ("app-context.xml");
			transactionManager = context.getBean (PlatformTransactionManager.class);
			emf = context.getBean (EntityManagerFactory.class);
		}
		catch ( IOException e )
		{
			e.printStackTrace ();
		}
		*/
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
		Script s = new Script ("OP_PUSH3 0a0b0c OP_PUSHDATA1 03 0a0b0c OP_EQUAL");
		assertTrue (s.evaluate ());
	}

	@Test
	public void ifTest ()
	{
		assertTrue (new Script ("OP_1 OP_1 OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_IF OP_1 OP_ELSE OP_1 OP_ENDIF").evaluate ());
		assertFalse (new Script ("OP_1 OP_IF OP_FALSE OP_ELSE OP_1 OP_ENDIF OP_EQUAL").evaluate ());
		assertFalse (new Script ("OP_1 OP_IF OP_1 OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_NOTIF OP_FALSE OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_1 OP_EQUAL").evaluate ());
	}

	@Test
	public void stackTest ()
	{
		assertTrue (new Script ("OP_1 OP_TOALTSTACK OP_FALSE OP_FROMALTSTACK OP_1 OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_2 OP_SWAP OP_1 OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_2 OP_3 OP_1 OP_PICK OP_2 OP_EQUAL").evaluate ());
	}

	@Test
	public void mathTest ()
	{
		assertTrue (new Script ("OP_1 OP_2 OP_ADD OP_3 OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_3 OP_DUP OP_SUB OP_FALSE OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_5 OP_SUB OP_ABS OP_4 OP_EQUAL").evaluate ());
		assertTrue (new Script ("OP_1 OP_5 OP_MAX OP_2 OP_MIN OP_2 OP_EQUAL").evaluate ());
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
					+ " OP_EQUAL").evaluate ());
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
	}

	@Test
	public void transactionTest ()
	{
		WireFormat.Reader reader =
				new WireFormat.Reader (
						Hex.decode ("0100000001169e1e83e930853391bc6f35f605c6754cfead57cf8387639d3b4096c54f18f40100000048473044022027542a94d6646c51240f23a76d33088d3dd8815b25e9ea18cac67d1171a3212e02203baf203c6e7b80ebd3e588628466ea28be572fe1aaa3f30947da4763dd3b3d2b01ffffffff0200ca9a3b00000000434104b5abd412d4341b45056d3e376cd446eca43fa871b51961330deebd84423e740daa520690e1d9e074654c59ff87b408db903649623e86f1ca5412786f61ade2bfac005ed0b20000000043410411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3ac00000000"));
		Tx t1 = new Tx ();
		t1.fromWire (reader);
		assertTrue (t1.getHash ().equals ("a16f3ce4dd5deb92d98ef5cf8afeaf0775ebca408f708b2146c4fb42b41e14be"));

		reader =
				new WireFormat.Reader (
						Hex.decode ("0100000001c997a5e56e104102fa209c6a852dd90660a20b2d9c352423edce25857fcd3704000000004847304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d0901ffffffff0200ca9a3b00000000434104ae1a62fe09c5f51b13905f07f06b99a2f7159b2225f374cd378d71302fa28414e7aab37397f554a7df5f142c21c1b7303b8a0626f1baded5c72a704f7e6cd84cac00286bee0000000043410411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3ac00000000"));
		Tx t2 = new Tx ();
		t2.fromWire (reader);
		assertTrue (t2.getHash ().equals ("f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16"));
		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (1));
		assertTrue (new Script (t1, 0).evaluate ());

		reader =
				new WireFormat.Reader (
						Hex.decode ("0100000001944badc33f9a723eb1c85dde24374e6dee9259ef4cfa6a10b2fd05b6e55be400000000008c4930460221009f8aef83489d5c3524b68ddf77e8af8ceb5cba89790d31d2d2db0c80b9cbfd26022100bb2c13e15bb356a4accdd55288e8b2fd39e204a93d849ccf749eaef9d8162787014104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcffffffff0100f2052a010000001976a9146934efcef36903b5b45ebd1e5f862d1b63a99fa588ac00000000"));
		t1 = new Tx ();
		t1.fromWire (reader);
		assertTrue (t1.getHash ().equals ("74c1a6dd6e88f73035143f8fc7420b5c395d28300a70bb35b943f7f2eddc656d"));

		reader =
				new WireFormat.Reader (
						Hex.decode ("01000000016d65dcedf2f743b935bb700a30285d395c0b42c78f3f143530f7886edda6c174000000008c493046022100b687c4436277190953466b3e4406484e89a4a4b9dbefea68cf5979f74a8ef5b1022100d32539ffb88736f3f9445fa6dd484b443ebb31af1471ee65071c7414e3ec007b014104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcffffffff0240420f000000000043410403c344438944b1ec413f7530aaa6130dd13562249d07d53ba96d8ac4f59832d05c837e36efd9533a6adf1920465fed2a4553fb357844f2e41329603c320753f4acc0aff62901000000434104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcac00000000"));
		t2 = new Tx ();
		t2.fromWire (reader);
		assertTrue (t2.getHash ().equals ("131f68261e28a80c3300b048c4c51f3ca4745653ba7ad6b20cc9188322818f25"));

		t2.getInputs ().get (0).setSource (t1.getOutputs ().get (0));
		assertTrue (new Script (t2, 0).evaluate ());

		/*
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{

			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager (emf);

				QTx tx = QTx.tx;

				JPAQuery query = new JPAQuery (entityManager);

				Tx t = query.from (tx).where (tx.hash.eq ("131f68261e28a80c3300b048c4c51f3ca4745653ba7ad6b20cc9188322818f25")).singleResult (tx);
				System.out.println (t.toJSON ());
				WireFormat.Writer writer = new WireFormat.Writer ();
				t.toWire (writer);
				try
				{
					Hex.encode (writer.toByteArray (), System.out);
					System.out.println ("");
				}
				catch ( IOException e )
				{
					e.printStackTrace ();
				}
				System.out.println (t.toJSON ());
				assertTrue (new Script (t, 0).evaluate ());

				query = new JPAQuery (entityManager);
				t = query.from (tx).where (tx.hash.eq ("74c1a6dd6e88f73035143f8fc7420b5c395d28300a70bb35b943f7f2eddc656d")).singleResult (tx);
				System.out.println (t.toJSON ());
				assertTrue (new Script (t, 0).evaluate ());

				query = new JPAQuery (entityManager);
				t = query.from (tx).where (tx.hash.eq ("131f68261e28a80c3300b048c4c51f3ca4745653ba7ad6b20cc9188322818f25")).singleResult (tx);
				System.out.println (t.toJSON ());
				assertTrue (new Script (t, 0).evaluate ());

				query = new JPAQuery (entityManager);
				t = query.from (tx).where (tx.hash.eq ("66ac54c1a3196e839c32c7700fbd91ee37b1ed4a53332e491bf5ddcd3901d0b1")).singleResult (tx);
				System.out.println (t.toJSON ());
				assertTrue (new Script (t, 0).evaluate ());

			}
		});
				*/
	}
}
