package org.purser.server;

import hu.blummers.bitcoin.core.Chain;

import java.math.BigInteger;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	JpaSerializer serializer;
	
	private NetworkParameters params;
	private Chain chain;

	public void setNetworkParams (NetworkParameters params) {
		this.params = params;
	}
	
	@Override
	public void setChain(Chain chain) {
		this.chain = chain;		
	}

	public void resetStore() throws BlockStoreException {
		log.info("reset store");
		put(chain.getGenesis());
		setChainHead(chain.getGenesis());
	}

	@Override
	public void put(StoredBlock block) throws BlockStoreException {
		QJpaBlock jb = QJpaBlock.jpaBlock;
		JPAQuery q1 = new JPAQuery(entityManager);
		JpaBlock b = q1.from(jb).where(jb.hash.eq(block.getHeader().getHashAsString())).uniqueResult(jb);
		if ( b == null )
		{
			b = serializer.jpaBlockFromWire(block.getHeader().unsafeBitcoinSerialize());
			b.setChainWork(block.getChainWork().toByteArray());
			b.setHeight(block.getHeight());
			try {
				b.validate(entityManager);
				entityManager.persist(b);
			} catch (ValidationException e) {
				log.error("can not store block " + block.toString(), e);
			}
		}
	}

	public void put(JpaBlock block) throws BlockStoreException {
		entityManager.merge(block);
	}

	@Override
	public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
		return get(hash.toString());
	}

	private StoredBlock get(String hash) throws BlockStoreException {
		try {
			QJpaBlock block = QJpaBlock.jpaBlock;

			JPAQuery query = new JPAQuery(entityManager);

			JpaBlock b = query.from(block).where(block.hash.eq(hash))
					.uniqueResult(block);
			if ( b == null )
				return null;
			
			byte [] message = serializer.jpaBlockToWire(b);
			return new StoredBlock(new Block(params, message,false,true,message.length),
					new BigInteger (b.getChainWork()), b.getHeight());
		} catch (Exception e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public StoredBlock getChainHead() throws BlockStoreException {
		try {
			QJpaChainHead head = QJpaChainHead.jpaChainHead;

			JPAQuery q1 = new JPAQuery(entityManager);
			JpaChainHead h = q1.from(head).uniqueResult(head);
			if ( h == null )
			{
				resetStore ();
			}
			return get(h.getHash());
		} catch (Exception e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
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

	public void setChainHead(JpaBlock chainHead) throws BlockStoreException {
		try {
			QJpaChainHead head = QJpaChainHead.jpaChainHead;

			JPADeleteClause delete = new JPADeleteClause(entityManager, head);
			delete.execute();

			JpaChainHead h = new JpaChainHead();
			h.setHash(chainHead.getHash());
			entityManager.persist(h);
		} catch (Exception e) {
			throw (new BlockStoreException(e));
		}
	}

	@Override
	public void close() throws BlockStoreException {
	}
}
