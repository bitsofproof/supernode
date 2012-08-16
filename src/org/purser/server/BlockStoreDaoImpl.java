package org.purser.server;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.store.BlockStoreException;
import com.mysema.query.jpa.impl.JPADeleteClause;
import com.mysema.query.jpa.impl.JPAQuery;

@Transactional
@Component
public class BlockStoreDaoImpl implements BlockStoreDao {
	private static final Logger log = LoggerFactory
			.getLogger(BlockStoreDaoImpl.class);

	@PersistenceContext
	EntityManager entityManager;

	private NetworkParameters params;

	public void setNetworkParams (NetworkParameters params) {
		this.params = params;
	}

	public StoredBlock resetStore() throws BlockStoreException {
		log.info("reset store");
		try {
			QJpaChainHead head = QJpaChainHead.jpaChainHead;
			JPADeleteClause delete = new JPADeleteClause(entityManager, head);
			delete.execute();

			QJpaStoredBlock block = QJpaStoredBlock.jpaStoredBlock;
			delete = new JPADeleteClause(entityManager, block);
			delete.execute();

			Block genesis = params.genesisBlock.cloneAsHeader();
			StoredBlock storedGenesis;
			storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);
			put(storedGenesis);
			setChainHead(storedGenesis);
			return storedGenesis;
		} catch (VerificationException e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public void put(StoredBlock block) throws BlockStoreException {
		log.info("put");
		JpaStoredBlock b = new JpaStoredBlock();
		b.setHash(block.getHeader().getHashAsString());
		b.setHeader(block.getHeader().unsafeBitcoinSerialize());
		b.setChainWork(block.getChainWork());
		b.setHeight(block.getHeight());
		entityManager.persist(b);
	}

	@Override
	public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
		return get(hash.toString());
	}

	private StoredBlock get(String hash) throws BlockStoreException {
		log.info("get");
		try {
			QJpaStoredBlock block = QJpaStoredBlock.jpaStoredBlock;

			JPAQuery query = new JPAQuery(entityManager);

			JpaStoredBlock b = query.from(block).where(block.hash.eq(hash))
					.uniqueResult(block);
			if ( b == null )
				return null;
			
			return new StoredBlock(new Block(params, b.getHeader()),
					b.getChainWork(), b.getHeight());
		} catch (Exception e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public StoredBlock getChainHead() throws BlockStoreException {
		log.info("getChainHead");
		try {
			QJpaChainHead head = QJpaChainHead.jpaChainHead;

			JPAQuery q1 = new JPAQuery(entityManager);
			JpaChainHead h = q1.from(head).uniqueResult(head);
			if ( h == null )
			{
				return resetStore ();
			}
			return get(h.getHash());
		} catch (Exception e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
		log.info("setChainHead");
		try {
			QJpaChainHead head = QJpaChainHead.jpaChainHead;

			JPADeleteClause delete = new JPADeleteClause(entityManager, head);
			delete.execute();

			JpaChainHead h = new JpaChainHead();
			h.setHash(chainHead.getHeader().getHashAsString());
			entityManager.persist(h);
		} catch (Exception e) {
			throw (new BlockStoreException(e));
		}
	}

	@Override
	public void close() throws BlockStoreException {
	}

}
