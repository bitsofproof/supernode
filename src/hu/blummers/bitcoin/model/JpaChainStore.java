package hu.blummers.bitcoin.model;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.query.jpa.impl.JPAQuery;

import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.ChainStoreException;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.model.QJpaBlock;
import hu.blummers.bitcoin.model.QJpaHead;
import hu.blummers.bitcoin.model.QJpaTransaction;

@Component("store")
@Transactional(propagation=Propagation.MANDATORY)
public class JpaChainStore implements ChainStore {
	@PersistenceContext
	EntityManager entityManager;
	
	@Override
	public String getHeadHash() {
		QJpaHead head = QJpaHead.jpaHead;

		JPAQuery q1 = new JPAQuery(entityManager);
		JpaHead h = q1.from(head).orderBy(head.chainWork.desc()).limit(1).list(head).get(0);
		return h.getHash();
	}

	@Override
	public synchronized void resetStore(Chain chain) {
		JpaBlock genesis = chain.getGenesis();	
		JpaHead h = new JpaHead();
		h.setHash(chain.getGenesis().getHash());			
		h.setHeight (1);
		h.setChainWork(genesis.getDifficulty());
		genesis.setHead(h);
		h.setBlocks(new ArrayList<JpaBlock> ());
		h.getBlocks().add(genesis);
		entityManager.persist(chain.getGenesis());
	}

	@Override
	public synchronized void store(JpaBlock newBlock) throws ChainStoreException {
		try {
			// find previous block
			QJpaBlock block = QJpaBlock.jpaBlock;
			JPAQuery query = new JPAQuery(entityManager);

			JpaBlock prev = query.from(block).where(block.hash.eq(newBlock.getPreviousHash())).uniqueResult(block);
			if ( prev == null )
				throw new ChainStoreException ("Block not connected");
			
			newBlock.setPrevious(prev);
			JpaHead head;
			if ( prev.getHead().getHash().equals(prev.getHash()) )
			{
				// continuing
				head = prev.getHead();
			}
			else
			{
				// branching
				head = new JpaHead ();
				head.setBlocks(new ArrayList<JpaBlock> ());
				head.setJoinHeight(prev.getHeight());
				head.setHeight(prev.getHeight());
				head.setChainWork(prev.getChainWork());
				head.setPrevious(prev.getHead());
			}
			head.setHash(newBlock.getHash());
			head.getBlocks().add(newBlock);
			head.setHeight(head.getHeight()+1);
			head.setChainWork(head.getChainWork()+newBlock.getDifficulty ());

			newBlock.setHead(head);
			newBlock.setHeight(head.getHeight());
			newBlock.setChainWork(head.getChainWork());

			boolean coinbase = true;
			for ( JpaTransaction t : newBlock.getTransactions() )
			{
				t.validate(this, coinbase);
				store (t);
				coinbase = false;
			}
			
			entityManager.persist(newBlock);
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
		finally
		{
		}
	}

	@Override
	public JpaBlock get(String hash) {
		QJpaBlock block = QJpaBlock.jpaBlock;

		JPAQuery query = new JPAQuery(entityManager);

		return query.from(block).where(block.hash.eq(hash)).uniqueResult(block);
	}

	@Override
	public List<JpaTransaction> getTransactions(String hash) {
		QJpaTransaction tx = QJpaTransaction.jpaTransaction;
		JPAQuery query = new JPAQuery (entityManager);
		return query.from(tx).where(tx.hash.eq(hash)).list(tx);
	}

	@Override
	public synchronized void store(JpaTransaction transaction) {
		entityManager.persist(transaction);
	}

	@Override
	public int getChainHeight(){
		QJpaHead head = QJpaHead.jpaHead;

		JPAQuery q1 = new JPAQuery(entityManager);
		JpaHead h = q1.from(head).orderBy(head.chainWork.desc()).limit(1).list(head).get(0);
		return h.getHeight();
	}

}
