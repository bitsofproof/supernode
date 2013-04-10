/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.client.model;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.api.KeyFormatter;
import com.bitsofproof.supernode.api.KeyGenerator;
import com.bitsofproof.supernode.api.ValidationException;
import com.mysema.query.jpa.impl.JPAQuery;

public class WalletStore
{
	@PersistenceContext
	private EntityManager entityManager;

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void store (String name, KeyGenerator wallet, String passphrase, int addressFlag) throws ValidationException
	{
		KeyFormatter formatter = new KeyFormatter (passphrase, addressFlag);
		QWalletEntity qwe = QWalletEntity.walletEntity;
		JPAQuery q = new JPAQuery (entityManager);
		WalletEntity we = q.from (qwe).where (qwe.name.eq (name)).singleResult (qwe);
		if ( we != null )
		{
			we.setChainCode (wallet.getMaster ().getChainCode ());
			String key = formatter.serializeKey (wallet.getMaster ().getKey ());
		}
		entityManager.persist (wallet);
	}
}
