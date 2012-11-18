package com.bitsofproof.supernode.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.core.Discovery;
import com.mysema.query.jpa.impl.JPAQuery;

@Component ("jpaPeerStore")
public class JpaPeerStore implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaPeerStore.class);

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	public List<KnownPeer> getConnectablePeers ()
	{
		QKnownPeer kp = QKnownPeer.knownPeer;
		JPAQuery q = new JPAQuery (entityManager);
		List<KnownPeer> pl =
				q.from (kp).where (kp.banned.lt (System.currentTimeMillis () / 1000)).orderBy (kp.preference.desc ()).orderBy (kp.height.desc ())
						.orderBy (kp.responseTime.desc ()).orderBy (kp.connected.desc ()).list (kp);
		log.trace ("Retrieved " + pl.size () + " peers from store");
		return pl;
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public synchronized void store (KnownPeer peer)
	{
		try
		{
			entityManager.merge (peer);
			log.trace ("Stored peer " + peer.getAddress ());
		}
		catch ( ConstraintViolationException e )
		{
		}
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public KnownPeer findPeer (InetAddress address)
	{
		QKnownPeer kp = QKnownPeer.knownPeer;
		JPAQuery q = new JPAQuery (entityManager);
		KnownPeer peer = q.from (kp).where (kp.address.eq (address.getHostAddress ())).uniqueResult (kp);
		if ( peer != null )
		{
			log.trace ("Retrieved peer " + peer.getAddress ());
		}
		return peer;
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering stored peers");
		List<InetAddress> peers = new ArrayList<InetAddress> ();
		for ( KnownPeer kp : getConnectablePeers () )
		{
			try
			{
				peers.add (InetAddress.getByName (kp.getAddress ()));
			}
			catch ( UnknownHostException e )
			{
			}
		}
		return peers;
	}
}
