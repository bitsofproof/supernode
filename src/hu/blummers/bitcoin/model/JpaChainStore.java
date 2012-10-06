package hu.blummers.bitcoin.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.query.jpa.impl.JPAQuery;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ValidationException;
import hu.blummers.bitcoin.model.QJpaBlock;
import hu.blummers.bitcoin.model.QJpaHead;
import hu.blummers.bitcoin.model.QJpaTransaction;

@Component("store")
@Transactional(propagation = Propagation.MANDATORY)
public class JpaChainStore implements ChainStore {
	private static final Logger log = LoggerFactory.getLogger(JpaChainStore.class);

	@PersistenceContext
	EntityManager entityManager;

	private Head currentHead = null;
	private Map<String, Head> heads = new HashMap<String, Head>();
	private Map<String, Member> members = new HashMap<String, Member>();
	private Map<BitcoinPeer, TreeSet<KnownMember>> knownByPeer = new HashMap<BitcoinPeer, TreeSet<KnownMember>> ();
	private Map<BitcoinPeer, HashSet<String>> requestsByPeer = new HashMap<BitcoinPeer, HashSet<String>> ();

	private Comparator<KnownMember> incomingOrder = new Comparator<KnownMember> (){
		@Override
		public int compare(KnownMember arg0, KnownMember arg1) {
			int diff = arg0.nr - arg1.nr;
			if ( diff != 0 )
				return diff;
			else
				return arg0.equals(arg1) ? 0 : arg0.hashCode() - arg1.hashCode();
		}};
	
	@Autowired
	PlatformTransactionManager transactionManager;

	public class Head {
		private StoredMember last;
		private double chainWork;
		private long height;

		public StoredMember getLast() {
			return last;
		}

		public double getChainWork() {
			return chainWork;
		}

		public long getHeight() {
			return height;
		}

		public void setLast(StoredMember last) {
			this.last = last;
		}

		public void setChainWork(double chainWork) {
			this.chainWork = chainWork;
		}

		public void setHeight(long height) {
			this.height = height;
		}
		
	}

	public class Member {
		protected String hash;

		public Member(String hash) {
			super();
			this.hash = hash;
		}

		public String getHash() {
			return hash;
		}

		@Override
		public int hashCode() {
			return hash.hashCode();
		}
	}

	public class StoredMember extends Member {
		public StoredMember(String hash, Long id, StoredMember previous) {
			super(hash);
			this.id = id;
			this.previous = previous;
		}

		protected Long id;
		protected StoredMember previous;

		public Long getId() {
			return id;
		}

		public StoredMember getPrevious() {
			return previous;
		}
	}

	public class KnownMember extends Member {
		protected Set<BitcoinPeer> knownBy;
		protected int nr;

		public KnownMember(String hash, int nr, Set<BitcoinPeer> knownBy) {
			super(hash);
			this.knownBy = knownBy;
			this.nr = nr;
		}

		public Set<BitcoinPeer> getKnownBy() {
			return knownBy;
		}

		public int getNr() {
			return nr;
		}

	}

	public void cache() {
		log.trace("filling chain cache with stored blocks");
		QJpaBlock block = QJpaBlock.jpaBlock;
		JPAQuery q = new JPAQuery(entityManager);
		for (JpaBlock b : q.from(block).list(block)) {
			if ( b.getPrevious() != null )
				members.put(b.getHash(), new StoredMember(b.getHash(), b.getId(), (StoredMember) members.get(b.getPrevious().getHash())));
			else
				members.put(b.getHash(), new StoredMember(b.getHash(), b.getId(), null));	
		}

		log.trace("filling chain cache with heads");
		QJpaHead head = QJpaHead.jpaHead;
		q = new JPAQuery(entityManager);
		for (JpaHead h : q.from(head).list(head)) {
			Head sh = new Head();
			sh.setChainWork(h.getChainWork());
			sh.setHeight(h.getHeight());
			sh.setLast((StoredMember)members.get(h.getHash()));
			heads.put(h.getHash(), sh);
			if ( currentHead == null || currentHead.getChainWork() < sh.getChainWork() )
				currentHead = sh;
		}
	}
	
	public synchronized void addInventory (String hash, BitcoinPeer peer)
	{
		Member cached = members.get(hash);
		if ( cached == null )
		{
			HashSet<BitcoinPeer> peers = new HashSet<BitcoinPeer> ();
			members.put(hash, cached = new KnownMember (hash, members.size(), peers));
		}
		if ( !(cached instanceof KnownMember) )
			return;
		((KnownMember)cached).getKnownBy().add(peer);
		TreeSet<KnownMember> membersOfPeer = knownByPeer.get(peer);
		if ( membersOfPeer == null )
		{
			membersOfPeer = new TreeSet<KnownMember> (incomingOrder);
			knownByPeer.put(peer, membersOfPeer);
		}
		membersOfPeer.add((KnownMember)cached);
	}
	
	public synchronized List<String> getRequests (BitcoinPeer peer)
	{
		HashSet<String> requests = requestsByPeer.get(peer);
		if ( requests == null )
			requests = new HashSet<String> ();
		TreeSet<KnownMember> knownbyThisPeer = knownByPeer.get(peer);
		ArrayList<String> result = new ArrayList<String> ();
		if ( knownbyThisPeer != null )
		{
			for ( KnownMember kn : knownbyThisPeer )
			{
				requests.add(kn.getHash());
				result.add(kn.getHash());
			}
			requestsByPeer.put(peer, requests);
		}
		knownByPeer.get(peer).clear();
		
		return result;
	}
	
	public synchronized void removePeer (BitcoinPeer peer)
	{
		requestsByPeer.remove(peer);
		TreeSet<KnownMember> ms = knownByPeer.get(peer);
		if ( ms != null )
			for ( KnownMember m : ms )
				m.getKnownBy().remove(peer);
		knownByPeer.remove(peer);
	}
	
	public synchronized List<String> getLocator ()
	{
		List<String> locator = new ArrayList<String> ();
		StoredMember curr = currentHead.getLast();
		StoredMember prev = curr.getPrevious();
		for ( int i =0, step = 1; prev != null;  ++i )
		{
			locator.add(curr.getHash());
			for ( int j =0; prev != null && j < step; ++j )
			{
				curr = prev;
				prev = curr.getPrevious();
			}
			if ( i > 10 )
				step *= 2;
		}
		if ( curr != currentHead.getLast() )
			locator.add(curr.getHash());
		return locator;
	}

	@Override
	public synchronized long store(JpaBlock b) throws ValidationException {
		
		List<BitcoinPeer> finishedPeer = new ArrayList<BitcoinPeer> ();
		for ( Map.Entry<BitcoinPeer,HashSet<String>> e : requestsByPeer.entrySet() )
		{
			e.getValue().remove(b.getHash());
			if ( e.getValue().size() == 0 )
				finishedPeer.add(e.getKey ());
		}
		for ( BitcoinPeer p : finishedPeer )
			requestsByPeer.remove(p);

		
		Member cached = members.get(b.getHash());
		if (cached instanceof StoredMember)
			return currentHead.getHeight();

		// find previous block
		Member cachedPrevious = members.get(b.getPreviousHash());
		JpaBlock prev = null;
		if ( cachedPrevious instanceof StoredMember )
		{
			prev = entityManager.find(JpaBlock.class, ((StoredMember) cachedPrevious).getId());
		}
		if (prev != null) {
			
			b.setPrevious(prev);
			JpaHead head;
			Head usingHead = currentHead;
			if (prev.getHead().getHash().equals(prev.getHash())) {
				// continuing
				head = prev.getHead();
			} else {
				// branching
				head = new JpaHead();
				head.setJoinHeight(prev.getHeight());
				head.setHeight(prev.getHeight());
				head.setChainWork(prev.getChainWork());
				head.setPrevious(prev.getHead());
				heads.put(b.getHash(), usingHead = new Head ());
			}
			head.setHash(b.getHash());
			head.setHeight(head.getHeight() + 1);
			head.setChainWork(head.getChainWork() + b.getDifficulty());
			if ( head.getChainWork() > currentHead.getChainWork() )
			{
				currentHead = usingHead;
			}
			
			b.setHead(head);
			b.setHeight(head.getHeight());
			b.setChainWork(head.getChainWork());
			usingHead.setChainWork(head.getChainWork());
			usingHead.setHeight(head.getHeight());

			boolean coinbase = true;
			for (JpaTransaction t : b.getTransactions()) {
				t.connect(this, coinbase);
				entityManager.persist(t);

				coinbase = false;
			}

			entityManager.persist(b);

			StoredMember m = new StoredMember(b.getHash(), b.getId(), (StoredMember) members.get(b.getPrevious().getHash()));
			usingHead.setLast(m);
			
			KnownMember kn = (KnownMember)members.get(b.getHash());
			if ( kn != null )
			{
				for ( BitcoinPeer peer : kn.getKnownBy() )
				{
					knownByPeer.get(peer).remove(kn);
				}
			}
			members.put(b.getHash(), m);

			log.trace("stored block " + b.getHash());
		}
		return currentHead.getHeight();
	}
	
	@Override
	public String getHeadHash() {
		return currentHead.getLast().getHash();
	}

	@Override
	public void resetStore(Chain chain) {
		JpaBlock genesis = chain.getGenesis();
		JpaHead h = new JpaHead();
		h.setHash(chain.getGenesis().getHash());
		h.setHeight(1);
		h.setChainWork(genesis.getDifficulty());
		genesis.setHead(h);
		entityManager.persist(chain.getGenesis());
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
		JPAQuery query = new JPAQuery(entityManager);
		return query.from(tx).where(tx.hash.eq(hash)).list(tx);
	}

	@Override
	public long getChainHeight() {
		Head longest = null;
		for ( Head h : heads.values() )
		{
			if ( longest == null || longest.getChainWork() < h.getChainWork() )
				longest = h;
		}
		return longest.getHeight ();
	}

	@Override
	public synchronized int getNumberOfRequests(BitcoinPeer peer) {
		HashSet<String> s = requestsByPeer.get(peer);
		if ( s == null ) 
			return 0;
		return s.size();
	}

}
