package hu.blummers.bitcoin.jpa;

import java.util.ArrayList;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
			JpaChainHead h = q1.from(head).orderBy(head.chainWork.desc()).limit(1).list(head).get(0);
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
			JpaBlock genesis = chain.getGenesis();	
			JpaChainHead h = new JpaChainHead();
			h.setHash(chain.getGenesis().getHash());			
			h.setHeight (1);
			h.setChainWork(genesis.getDifficulty());
			genesis.setHead(h);
			h.setBlocks(new ArrayList<JpaBlock> ());
			h.getBlocks().add(genesis);
			entityManager.persist(chain.getGenesis());
		}
		catch ( Exception e )
		{
			throw new ChainStoreException (e);
		}
	}

	@Override
	public void store(JpaBlock newBlock) throws ChainStoreException {
		try {
			// find previous block
			QJpaBlock block = QJpaBlock.jpaBlock;
			JPAQuery query = new JPAQuery(entityManager);

			JpaBlock prev = query.from(block).where(block.hash.eq(newBlock.getPreviousHash())).uniqueResult(block);
			if ( prev == null )
				throw new ChainStoreException ("Block not connected");
			
			newBlock.setPrevious(prev);
			JpaChainHead head;
			if ( prev.getHead().getHash().equals(prev.getHash()) )
			{
				// continuing
				head = prev.getHead();
			}
			else
			{
				// branching
				head = new JpaChainHead ();
				head.setBlocks(new ArrayList<JpaBlock> ());
				head.setJoinHeight(prev.getHeight());
			}
			head.getBlocks().add(newBlock);
			newBlock.setHead(head);
			newBlock.setHeight(head.getHeight()+1);
			newBlock.setChainWork(head.getChainWork()+newBlock.getDifficulty ());
			head.setHeight(newBlock.getHeight());
			head.setChainWork(newBlock.getChainWork());
				
			
			entityManager.persist(newBlock);
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
