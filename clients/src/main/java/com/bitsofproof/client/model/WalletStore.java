package com.bitsofproof.client.model;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.api.KeyFormatter;
import com.bitsofproof.supernode.api.Wallet;
import com.mysema.query.jpa.impl.JPAQuery;

public class WalletStore
{
	@PersistenceContext
	private EntityManager entityManager;

	@Transactional (propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void store (String name, Wallet wallet, String passphrase, int addressFlag)
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
