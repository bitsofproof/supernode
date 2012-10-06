package hu.blummers.bitcoin.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.mysema.query.jpa.impl.JPAQuery;

import edu.emory.mathcs.backport.java.util.Collections;

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

	private Map<String, Head> heads = new HashMap<String, Head>();
	private Map<String, Member> members = new HashMap<String, Member>();
	private Map<BitcoinPeer, TreeSet<KnownMember>> knownByPeer = new HashMap<BitcoinPeer, TreeSet<KnownMember>> ();
	private Map<BitcoinPeer, HashSet<String>> requestsByPeer = new HashMap<BitcoinPeer, HashSet<String>> ();
	private Map<String, HashSet<OrphanMember>> pendingOn = new HashMap<String, HashSet<OrphanMember>>();

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

	private Executor orphanFlusher = Executors.newFixedThreadPool(1);

	public class Head {
		private StoredMember last;
		private double chainWork;

		public Head(StoredMember last, double chainWork) {
			super();
			this.last = last;
			this.chainWork = chainWork;
		}

		public StoredMember getLast() {
			return last;
		}

		public double getChainWork() {
			return chainWork;
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

	public class OrphanMember extends Member {
		protected JpaBlock block;

		public OrphanMember(String hash, JpaBlock block) {
			super(hash);
			this.block = block;
		}

		public JpaBlock getBlock() {
			return block;
		}

		public void setBlock(JpaBlock block) {
			this.block = block;
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
			heads.put(h.getHash(), new Head((StoredMember) members.get(h.getHash()), h.getChainWork()));
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
		HashSet<String> requests;
		if ( (requests = requestsByPeer.get(peer)) != null )
			if ( !requests.isEmpty() )
				return new ArrayList<String> ();
		
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
	
	@Override
	public synchronized int getNumberOfPeersWorking () {
		return requestsByPeer.values().size();
	}

	public synchronized void removePeer (BitcoinPeer peer)
	{
		requestsByPeer.remove(peer);
		knownByPeer.remove(peer);
	}
	
	public synchronized List<String> getLocator ()
	{
		List<String> locator = new ArrayList<String> ();
		Head longest = null;
		for ( Head h : heads.values() )
		{
			if ( longest == null || longest.getChainWork() < h.getChainWork() )
				longest = h;
		}
		StoredMember curr = longest.getLast();
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
		if ( curr != longest.getLast() )
			locator.add(curr.getHash());
		return locator;
	}

	@Override
	public synchronized void store(JpaBlock b) throws ValidationException {
		
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
			return;

		// find previous block
		Member cachedPrevious = members.get(b.getPreviousHash());
		JpaBlock prev = null;
		if ( cachedPrevious instanceof StoredMember )
		{
			prev = entityManager.find(JpaBlock.class, ((StoredMember) cachedPrevious).getId());
		}
		if (prev == null) {
			if (cached instanceof OrphanMember)
				return;

			if ( cached != null )
			{
				KnownMember kn = (KnownMember)cached;
				for ( BitcoinPeer peer : kn.getKnownBy() )
					knownByPeer.get(peer).remove(kn);
			}
			OrphanMember om;
			members.put(b.getHash(), om = new OrphanMember(b.getHash(), b));
			HashSet<OrphanMember> waiting = pendingOn.get(b.getPreviousHash());
			if ( waiting == null )
			{
				waiting = new HashSet<OrphanMember> ();
				pendingOn.put(b.getPreviousHash(), waiting);
			}
			waiting.add(om);
			log.trace("storing orphan block "+ b.getHash() + " pending on " + b.getPreviousHash());
		} else {
			b.setPrevious(prev);
			JpaHead head;
			boolean oldhead = false;
			if (prev.getHead().getHash().equals(prev.getHash())) {
				// continuing
				head = prev.getHead();
				oldhead = true;
			} else {
				// branching
				head = new JpaHead();
				head.setJoinHeight(prev.getHeight());
				head.setHeight(prev.getHeight());
				head.setChainWork(prev.getChainWork());
				head.setPrevious(prev.getHead());
			}
			head.setHash(b.getHash());
			head.setHeight(head.getHeight() + 1);
			head.setChainWork(head.getChainWork() + b.getDifficulty());

			b.setHead(head);
			b.setHeight(head.getHeight());
			b.setChainWork(head.getChainWork());

			boolean coinbase = true;
			for (JpaTransaction t : b.getTransactions()) {
				t.connect(this, coinbase);
				entityManager.persist(t);

				coinbase = false;
			}

			entityManager.persist(b);

			StoredMember m = new StoredMember(b.getHash(), b.getId(), (StoredMember) members.get(b.getPrevious().getHash()));
			
			Member member = members.get(b.getHash());
			if ( member instanceof OrphanMember )
				members.remove(b.getHash ());
			else
			{
				KnownMember kn;
				if ( (kn = (KnownMember)members.get(b.getHash())) != null )
				{
					for ( BitcoinPeer peer : kn.getKnownBy() )
						knownByPeer.get(peer).remove(kn);
				}
			}
			members.put(b.getHash(), m);

			if (oldhead)
				heads.remove(prev.getHash());
			heads.put(b.getHash(), new Head(m, head.getChainWork()));
			
			log.trace("stored block " + b.getHash());

			Set<OrphanMember> orphans;
			if ((orphans = pendingOn.get(b.getHash())) != null) {
				for (final OrphanMember o : orphans) {
					try {
						store(o.getBlock());
					} catch (ValidationException e) {
						log.error("rejected orphan block ", e);
					}
				}
				pendingOn.remove(b.getHash());
			}

		}
	}
	
	private synchronized void flushOrphans (final String hash)
	{
		new TransactionTemplate (transactionManager).execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				Set<OrphanMember> orphans;
				if ((orphans = pendingOn.get(hash)) != null) {
					for (final OrphanMember o : orphans) {
						try {
							store(o.getBlock());
						} catch (ValidationException e) {
							log.error("rejected orphan block ", e);
						}
					}
					pendingOn.remove(hash);
				}
			}
		});
	}

	@Override
	public String getHeadHash() {
		QJpaHead head = QJpaHead.jpaHead;

		JPAQuery q1 = new JPAQuery(entityManager);
		JpaHead h = q1.from(head).orderBy(head.chainWork.desc()).limit(1).list(head).get(0);
		return h.getHash();
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
	public int getChainHeight() {
		QJpaHead head = QJpaHead.jpaHead;

		JPAQuery q1 = new JPAQuery(entityManager);
		JpaHead h = q1.from(head).orderBy(head.chainWork.desc()).limit(1).list(head).get(0);
		return h.getHeight();
	}

}
