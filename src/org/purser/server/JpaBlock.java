package org.purser.server;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.WireFormat;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import com.mysema.query.jpa.impl.JPAQuery;


@Entity
public class JpaBlock {
	@Id
	@GeneratedValue
	private Long id;
	
	@Column(length=64,nullable=false,unique=true)
	private String hash;

	long version;
	
	@OneToOne(fetch=FetchType.LAZY,targetEntity=JpaBlock.class,optional=true,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	private JpaBlock previous;
	
	@Lob @Basic(fetch=FetchType.LAZY)
	private byte [] merkleRoot;
	
	private long createTime;
	
	private long difficultyTarget;
	
	private long nonce;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL)
	private List<JpaTransaction> transactions;

	@Lob @Basic(fetch=FetchType.LAZY)
	private byte [] chainWork;
	private int height;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	public JpaBlock getPrevious() {
		return previous;
	}
	public void setPrevious(JpaBlock previous) {
		this.previous = previous;
	}
	public byte[] getMerkleRoot() {
		return merkleRoot;
	}
	public void setMerkleRoot(byte[] merkleRoot) {
		this.merkleRoot = merkleRoot;
	}
	public long getCreateTime() {
		return createTime;
	}
	public void setCreateTime(long createTime) {
		this.createTime = createTime;
	}
	public long getDifficultyTarget() {
		return difficultyTarget;
	}
	public void setDifficultyTarget(long difficultyTarget) {
		this.difficultyTarget = difficultyTarget;
	}
	public long getNonce() {
		return nonce;
	}
	public void setNonce(long nonce) {
		this.nonce = nonce;
	}
	public List<JpaTransaction> getTransactions() {
		return transactions;
	}
	public void setTransactions(List<JpaTransaction> transactions) {
		this.transactions = transactions;
	}
	public byte[] getChainWork() {
		return chainWork;
	}
	public void setChainWork(byte[] chainWork) {
		this.chainWork = chainWork;
	}
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
	}
	
	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32(version);
		if ( previous != null )
			writer.writeHash(new Hash (previous.getHash()));
		else
			writer.writeHash (Hash.ZERO_HASH);
		writer.writeBytes(merkleRoot);
		writer.writeUint32(createTime);
		writer.writeUint32(difficultyTarget);
		writer.writeUint32(nonce);
		if ( transactions != null )
		{
			writer.writeVarInt(transactions.size());
			for ( JpaTransaction t : transactions )
				t.toWire(writer);
		}
		else
			writer.writeVarInt(0);
	}
	
	public void fromWire (WireFormat.Reader reader, EntityManager entityManager)
	{
		int cursor = reader.getCursor();
		version = reader.readUint32();

		QJpaBlock block = QJpaBlock.jpaBlock;
		JPAQuery query = new JPAQuery(entityManager);
		previous = query.from(block).where(block.hash.eq(reader.readHash().toString())).uniqueResult(block);
		
		merkleRoot = reader.readHash().toByteArray();
		createTime = reader.readUint32();
		difficultyTarget = reader.readUint32();
		nonce = reader.readUint32();
		long nt = reader.readVarInt();
		if ( nt > 0 )
		{
			transactions = new ArrayList<JpaTransaction> ();
			for ( long i = 0; i < nt; ++i )
			{
				JpaTransaction t = new JpaTransaction ();
				t.fromWire(reader, entityManager);
				transactions.add(t);
			}
		}
		else
			transactions = null;
		
		hash = reader.hash(cursor, 80).toString(); 
	}
}
