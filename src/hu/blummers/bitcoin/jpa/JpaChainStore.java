package hu.blummers.bitcoin.jpa;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.query.jpa.impl.JPADeleteClause;
import com.mysema.query.jpa.impl.JPAQuery;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.ChainStoreException;

@Transactional
@Component
public class JpaChainStore implements ChainStore {
	@PersistenceContext
	EntityManager entityManager;
	
	@Override
	public String getHeadHash() throws ChainStoreException {
		try
		{
			QJpaChainHead head = QJpaChainHead.jpaChainHead;
	
			JPAQuery q1 = new JPAQuery(entityManager);
			JpaChainHead h = q1.from(head).uniqueResult(head);
			return h.getHash();
		}
		catch ( Exception e )
		{
			throw new ChainStoreException (e);
		}
	}

	@Override
	public void resetStore(Chain chain) throws ChainStoreException {
		try {
			JPADeleteClause delete = new JPADeleteClause(entityManager, QJpaChainHead.jpaChainHead);
			delete.execute();

			JpaChainHead h = new JpaChainHead();
			h.setHash(chain.getGenesis().getHash());			
			
			entityManager.persist(h);
			entityManager.persist(chain.getGenesis());
		}
		catch ( Exception e )
		{
			throw new ChainStoreException (e);
		}
	}

	@Override
	public void store(JpaBlock block) throws ChainStoreException {
		try {
			entityManager.persist(block);
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
	}

	@Override
	public JpaBlock get(String hash) throws ChainStoreException {
		try {
			QJpaBlock block = QJpaBlock.jpaBlock;

			JPAQuery query = new JPAQuery(entityManager);

			return query.from(block).where(block.hash.eq(hash)).uniqueResult(block);
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
	}

	@Override
	public JpaBlock getPrevious(JpaBlock block) throws ChainStoreException {
		try {
			return block.getPrevious();
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
	}
}
