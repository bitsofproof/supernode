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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.core.Discovery;
import com.bitsofproof.supernode.core.PeerStore;
import com.mysema.query.jpa.impl.JPAQuery;

public class JpaPeerStore implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (JpaPeerStore.class);

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
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
	public void store (KnownPeer peer)
	{
		try
		{
			KnownPeer stored;
			if ( (stored = findPeer (InetAddress.getByName (peer.getAddress ()))) == null )
			{
				entityManager.persist (peer);
			}
			else
			{
				stored.setAgent (peer.getAgent ());
				stored.setBanned (peer.getBanned ());
				stored.setBanReason (peer.getBanReason ());
				stored.setConnected (peer.getConnected ());
				stored.setDisconnected (peer.getDisconnected ());
				stored.setHeight (peer.getHeight ());
				stored.setName (peer.getName ());
				stored.setPreference (peer.getPreference ());
				stored.setResponseTime (peer.getResponseTime ());
				stored.setServices (peer.getServices ());
				stored.setTrafficIn (peer.getTrafficIn ());
				stored.setTrafficOut (peer.getTrafficOut ());
				stored.setVersion (peer.getVersion ());
				entityManager.merge (stored);
			}
		}
		catch ( ConstraintViolationException e )
		{
		}
		catch ( UnknownHostException e )
		{
		}
	}

	@Override
	@Transactional (propagation = Propagation.REQUIRED)
	public KnownPeer findPeer (InetAddress address)
	{
		QKnownPeer kp = QKnownPeer.knownPeer;
		JPAQuery q = new JPAQuery (entityManager);
		return q.from (kp).where (kp.address.eq (address.getHostAddress ())).uniqueResult (kp);
	}

	@Override
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
