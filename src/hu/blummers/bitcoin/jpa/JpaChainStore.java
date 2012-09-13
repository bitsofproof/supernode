package hu.blummers.bitcoin.jpa;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.query.jpa.impl.JPAQuery;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.ChainStoreException;

@Transactional
@Component("JpaChainStore")
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
		finally
		{
		}
	}

	@Override
	public synchronized void resetStore(Chain chain) throws ChainStoreException {
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
		finally
		{
		}
	}

	@Override
	public synchronized boolean store(JpaBlock newBlock) throws ChainStoreException {
		try {
			JpaBlock stored = get (newBlock.getHash());
			if ( stored != null )
				return false;
			
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
			return true;
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
		finally
		{
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
		finally
		{
		}
	}

	@Override
	public List<JpaTransaction> getTransactions(String hash) throws ChainStoreException {
		try {
			QJpaTransaction tx = QJpaTransaction.jpaTransaction;
			JPAQuery query = new JPAQuery (entityManager);
			return query.from(tx).where(tx.hash.eq(hash)).list(tx);
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
		finally
		{
		}
	}

	@Override
	public synchronized void store(JpaTransaction transaction) throws ChainStoreException {
		try {
			entityManager.persist(transaction);
		} catch (Exception e) {
			throw new ChainStoreException (e);
		}
		finally
		{
		}
	}

}
