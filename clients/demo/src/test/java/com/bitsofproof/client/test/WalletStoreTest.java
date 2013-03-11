/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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
