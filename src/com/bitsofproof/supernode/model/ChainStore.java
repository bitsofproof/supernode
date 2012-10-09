package com.bitsofproof.supernode.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.ValidationException;
import com.mysema.query.jpa.impl.JPAQuery;

@Component("store")
@Transactional(propagation = Propagation.MANDATORY)
public class ChainStore  {
	private static final Logger log = LoggerFactory.getLogger(ChainStore.class);

	@PersistenceContext
	private EntityManager entityManager;

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private CachedHead currentHead = null;
	private Map<String, CachedHead> heads = new HashMap<String, CachedHead>();
	private Map<String, Member> members = new HashMap<String, Member>();
	private Map<BitcoinPeer, TreeSet<KnownMember>> knownByPeer = new HashMap<BitcoinPeer, TreeSet<KnownMember>>();
	private Map<BitcoinPeer, HashSet<String>> requestsByPeer = new HashMap<BitcoinPeer, HashSet<String>>();

	private Comparator<KnownMember> incomingOrder = new Comparator<KnownMember>() {
		@Override
		public int compare(KnownMember arg0, KnownMember arg1) {
			int diff = arg0.nr - arg1.nr;
			if (diff != 0)
				return diff;
			else
				return arg0.equals(arg1) ? 0 : arg0.hashCode() - arg1.hashCode();
		}
	};

	@Autowired
	PlatformTransactionManager transactionManager;

	public class CachedHead {
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
		public StoredMember(String hash, Long id, StoredMember previous, long time) {
			super(hash);
			this.id = id;
			this.previous = previous;
			this.time = time;
		}

		protected Long id;
		protected StoredMember previous;
		protected long time;

		public Long getId() {
			return id;
		}

		public StoredMember getPrevious() {
			return previous;
		}

		public long getTime() {
			return time;
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
		QBlock block = QBlock.block;
		JPAQuery q = new JPAQuery(entityManager);
		for (Block b : q.from(block).list(block)) {
			if (b.getPrevious() != null)
				members.put(b.getHash(), new StoredMember(b.getHash(), b.getId(), (StoredMember) members.get(b.getPrevious().getHash()), b.getCreateTime()));
			else
				members.put(b.getHash(), new StoredMember(b.getHash(), b.getId(), null, b.getCreateTime()));
		}

		log.trace("filling chain cache with heads");
		QHead head = QHead.head;
		q = new JPAQuery(entityManager);
		for (Head h : q.from(head).list(head)) {
			CachedHead sh = new CachedHead();
			sh.setChainWork(h.getChainWork());
			sh.setHeight(h.getHeight());
			sh.setLast((StoredMember) members.get(h.getLeaf()));
			heads.put(h.getLeaf(), sh);
			if (currentHead == null || currentHead.getChainWork() < sh.getChainWork())
				currentHead = sh;
		}
	}

	public void addInventory(List<String> hashes, BitcoinPeer peer) {
		try {
			lock.writeLock().lock();

			for (String hash : hashes) {
				Member cached = members.get(hash);
				if (cached == null) {
					HashSet<BitcoinPeer> peers = new HashSet<BitcoinPeer>();
					members.put(hash, cached = new KnownMember(hash, members.size(), peers));
				} else if (!(cached instanceof KnownMember))
					return;
				((KnownMember) cached).getKnownBy().add(peer);
				TreeSet<KnownMember> membersOfPeer = knownByPeer.get(peer);
				if (membersOfPeer == null) {
					membersOfPeer = new TreeSet<KnownMember>(incomingOrder);
					knownByPeer.put(peer, membersOfPeer);
				}
				membersOfPeer.add((KnownMember) cached);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public synchronized List<String> getRequests(BitcoinPeer peer) {
		try {
			lock.writeLock().lock();
			HashSet<String> requests = requestsByPeer.get(peer);
			if (requests == null)
				requests = new HashSet<String>();
			TreeSet<KnownMember> knownbyThisPeer = knownByPeer.get(peer);
			ArrayList<String> result = new ArrayList<String>();
			if (knownbyThisPeer != null) {
				for (KnownMember kn : knownbyThisPeer) {
					requests.add(kn.getHash());
					result.add(kn.getHash());
				}
				requestsByPeer.put(peer, requests);
				knownbyThisPeer.clear();
			}

			return result;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public synchronized void removePeer(BitcoinPeer peer) {
		try {
			lock.writeLock().lock();

			requestsByPeer.remove(peer);
			TreeSet<KnownMember> ms = knownByPeer.get(peer);
			if (ms != null)
				for (KnownMember m : ms)
					m.getKnownBy().remove(peer);
			knownByPeer.remove(peer);
		} finally {
			lock.writeLock().unlock();
		}
	}

	public synchronized List<String> getLocator() {
		try {
			lock.readLock().lock();

			List<String> locator = new ArrayList<String>();
			StoredMember curr = currentHead.getLast();
			StoredMember prev = curr.getPrevious();
			for (int i = 0, step = 1; prev != null; ++i) {
				locator.add(curr.getHash());
				for (int j = 0; prev != null && j < step; ++j) {
					curr = prev;
					prev = curr.getPrevious();
				}
				if (i > 10)
					step *= 2;
			}
			if (curr != currentHead.getLast())
				locator.add(curr.getHash());
			return locator;
		} finally {
			lock.readLock().unlock();
		}
	}

	public synchronized long store(Block b) throws ValidationException {

		b.computeHash();

		try {
			lock.writeLock().lock();

			Member cached = members.get(b.getHash());
			if (cached instanceof StoredMember)
				return currentHead.getHeight();

			for (TreeSet<KnownMember> k : knownByPeer.values()) {
				k.remove(cached);
			}

			List<BitcoinPeer> finishedPeer = new ArrayList<BitcoinPeer>();
			for (Map.Entry<BitcoinPeer, HashSet<String>> e : requestsByPeer.entrySet()) {
				e.getValue().remove(b.getHash());
				if (e.getValue().size() == 0)
					finishedPeer.add(e.getKey());
			}
			for (BitcoinPeer p : finishedPeer)
				requestsByPeer.remove(p);

			// find previous block
			Member cachedPrevious = members.get(b.getPreviousHash());
			Block prev = null;
			if (cachedPrevious instanceof StoredMember) {
				prev = entityManager.find(Block.class, ((StoredMember) cachedPrevious).getId());
			}
			if (prev != null) {
				if (b.getCreateTime() > System.currentTimeMillis() / 1000)
					throw new ValidationException("Future generation attempt or lagging system clock.");

				b.setPrevious(prev);
				boolean branching = false;
				Head head;
				if (prev.getHead().getLeaf().equals(prev.getHash())) {
					// continuing
					head = prev.getHead();

					head.setLeaf(b.getHash());
					head.setHeight(head.getHeight() + 1);
					head.setChainWork(head.getChainWork() + Difficulty.getDifficulty(b.getDifficultyTarget()));
					head = entityManager.merge(head);
				} else {
					// branching
					branching = true;
					head = new Head();
					head.setTrunk(prev.getHash());
					head.setHeight(prev.getHeight());
					head.setChainWork(prev.getChainWork());
					head.setPrevious(prev.getHead());

					head.setLeaf(b.getHash());
					head.setHeight(head.getHeight() + 1);
					head.setChainWork(head.getChainWork() + Difficulty.getDifficulty(b.getDifficultyTarget()));
					entityManager.persist(head);
				}

				b.setHead(head);
				b.setHeight(head.getHeight());
				b.setChainWork(head.getChainWork());
				if (prev != null) {
					if (b.getHeight() % 2016 == 0) {
						StoredMember c = null;
						StoredMember p = (StoredMember) cachedPrevious;
						for (int i = 0; i < 2015; ++i) {
							c = p;
							p = c.getPrevious();
						}

						long next = Difficulty.getNextTarget(prev.getCreateTime() - p.getTime(), prev.getDifficultyTarget());
						if (next != b.getDifficultyTarget()) {
							throw new ValidationException("Difficulty does not match expectation");
						}
					} else {
						if (b.getDifficultyTarget() != prev.getDifficultyTarget())
							throw new ValidationException("Illegal attempt to change difficulty");
					}
				}
				if (new Hash(b.getHash()).toBigInteger().compareTo(Difficulty.getTarget(b.getDifficultyTarget())) > 0)
					throw new ValidationException("Insufficuent proof of work for current difficulty");

				b.parseTransactions();
				boolean coinbase = true;
				Map<String, Transaction> blockTransactions = new HashMap<String, Transaction>();
				for (Transaction t : b.getTransactions()) {
					t.calculateHash();
					blockTransactions.put(t.getHash(), t);
					if (coinbase) {
						coinbase = false;
						continue;
					}

					if (t.getInputs() != null) {
						for (TransactionInput i : t.getInputs()) {
							Transaction sourceTransaction;
							TransactionOutput transactionOutput = null;
							if ((sourceTransaction = blockTransactions.get(i.getSourceHash())) != null) {
								if (i.getIx() < sourceTransaction.getOutputs().size())
									transactionOutput = sourceTransaction.getOutputs().get((int) i.getIx());
							} else {
								QTransaction tx = QTransaction.transaction;
								QTransactionOutput txout = QTransactionOutput.transactionOutput;
								JPAQuery query = new JPAQuery(entityManager);

								transactionOutput = query.from(txout).join(txout.transaction, tx)
										.where(tx.hash.eq(i.getSourceHash()).and(txout.ix.eq(i.getIx()))).orderBy(tx.id.desc()).limit(1).uniqueResult(txout);
							}
							if (transactionOutput == null)
								throw new ValidationException("Transaction input refers to unknown output ");
							i.setSource(transactionOutput);
							if (i.getSource().getSink() != null)
								throw new ValidationException("Double spending attempt");
						}
					}
				}

				entityManager.persist(b);

				for (Transaction t : b.getTransactions())
					for (TransactionInput i : t.getInputs())
						if (i.getSource() != null) {
							i.getSource().setSink(i);
							entityManager.merge(i.getSource());
						}

				StoredMember m = new StoredMember(b.getHash(), b.getId(), (StoredMember) members.get(b.getPrevious().getHash()), b.getCreateTime());
				members.put(b.getHash(), m);

				CachedHead usingHead = currentHead;
				if (branching) {
					heads.put(b.getHash(), usingHead = new CachedHead());
				}
				if (head.getChainWork() > currentHead.getChainWork()) {
					currentHead = usingHead;
				}

				usingHead.setLast(m);
				usingHead.setChainWork(head.getChainWork());
				usingHead.setHeight(head.getHeight());

				log.trace("stored block " + b.getHash());
			}
			return currentHead.getHeight();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public String getHeadHash() {
		try {
			lock.readLock().lock();

			return currentHead.getLast().getHash();
		} finally {
			lock.readLock().unlock();
		}
	}

	public void resetStore(Chain chain) {
		Block genesis = chain.getGenesis();
		Head h = new Head();
		h.setLeaf(genesis.getHash());
		h.setHeight(0);
		h.setChainWork(Difficulty.getDifficulty(genesis.getDifficultyTarget()));
		entityManager.persist(h);
		genesis.setHead(h);
		entityManager.persist(genesis);
	}

	public Block get(String hash) {

		Member cached = null;
		try {
			lock.readLock().lock();
			cached = members.get(hash);
		} finally {
			lock.readLock().unlock();
		}

		if (cached instanceof StoredMember)
			return entityManager.find(Block.class, ((StoredMember) cached).getId());

		QBlock block = QBlock.block;

		JPAQuery query = new JPAQuery(entityManager);

		return query.from(block).where(block.hash.eq(hash)).uniqueResult(block);
	}

	public long getChainHeight() {
		try {
			lock.readLock().lock();
			CachedHead longest = null;
			for (CachedHead h : heads.values()) {
				if (longest == null || longest.getChainWork() < h.getChainWork())
					longest = h;
			}
			return longest.getHeight();
		} finally {
			lock.readLock().unlock();
		}
	}

	public synchronized int getNumberOfRequests(BitcoinPeer peer) {
		try {
			lock.readLock().lock();
			HashSet<String> s = requestsByPeer.get(peer);
			if (s == null)
				return 0;
			return s.size();
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

}
