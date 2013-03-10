package com.bitsofproof.client.test;

import java.security.SecureRandom;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.client.model.KeyEntity;
import com.bitsofproof.client.model.WalletEntity;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/memdb.xml" })
public class WalletStoreTest
{
	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void testKeyStore ()
	{
		KeyEntity key = new KeyEntity ();
		key.setPriv ("6PfQu77ygVyJLZjfvMLyhLMQbYnu5uguoJJ4kMCLqWwPEdfpwANVS76gTX");
		entityManager.persist (key);
	}

	@Test
	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void testWalletStore ()
	{
		KeyEntity key = new KeyEntity ();
		key.setPriv ("6PfQu77ygVyJLZjfvMLyhLMQbYnu5uguoJJ4kMCLqWwPEdfpwANVS76gTX");
		WalletEntity wallet = new WalletEntity ();
		wallet.setName ("testwallet");
		wallet.setMaster (key);
		wallet.setChainCode (new byte[32]);
		new SecureRandom ().nextBytes (wallet.getChainCode ());
		entityManager.persist (wallet);
	}
}
