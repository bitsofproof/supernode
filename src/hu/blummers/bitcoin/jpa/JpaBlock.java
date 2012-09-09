package hu.blummers.bitcoin.jpa;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.ValidationException;
import hu.blummers.bitcoin.core.WireFormat;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;


import com.mysema.query.jpa.impl.JPAQuery;


@Entity
@Table(name="blk")
public class JpaBlock {

	@Id	@GeneratedValue
	private Long id;
	
	@Column(length=64,nullable=false,unique=true)
	private String hash;

	long version;
	
	transient private String previousHash;

	@ManyToOne(targetEntity=JpaBlock.class,optional=true)
	private JpaBlock previous;
	
	@Column(length=64,nullable=false)
	private String merkleRoot;
	
	private long createTime;
	
	private long difficultyTarget;
	
	private long nonce;
	
	@OneToMany(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
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
	public String getMerkleRoot() {
		return merkleRoot;
	}
	public void setMerkleRoot(String merkleRoot) {
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
	public BigInteger getWork ()
	{
		return new BigInteger (chainWork);
	}
	public void setWork (BigInteger work)
	{
		chainWork = work.toByteArray();
	}
	
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
	}
	
	public void computeMerkleRoot ()
	{
		if ( merkleRoot != null )
			return;
		
        ArrayList<byte[]> tree = new ArrayList<byte[]>();
        // Start by adding all the hashes of the transactions as leaves of the tree.
        for (JpaTransaction t : transactions) {
        	t.calculateHash();
            tree.add(new Hash (t.getHash()).toByteArray());
        }
        int levelOffset = 0; // Offset in the list where the currently processed level starts.
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
	        // Step through each level, stopping when we reach the root (levelSize == 1).
	        for (int levelSize = transactions.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
	            // For each pair of nodes on that level:
	            for (int left = 0; left < levelSize; left += 2) {
	                // The right hand node can be the same as the left hand, in the case where we don't have enough
	                // transactions.
	                int right = Math.min(left + 1, levelSize - 1);
	                byte[] leftBytes = tree.get(levelOffset + left);
	                byte[] rightBytes = tree.get(levelOffset + right);
	                digest.update(leftBytes);
	                digest.update(rightBytes);
	                tree.add(digest.digest(digest.digest ()));
	            }
	            // Move to the next level.
	            levelOffset += levelSize;
	        }
		} catch (NoSuchAlgorithmException e) {}
        merkleRoot = new Hash (tree.get(tree.size() - 1)).toString();
	}
	
	
	public void computeHash ()
	{
		if ( hash != null )
			return;
		
		computeMerkleRoot ();
		
		WireFormat.Writer writer = new WireFormat.Writer(new ByteArrayOutputStream());
		writer.writeUint32(version);
		if ( previous != null )
			writer.writeHash (new Hash (previous.getHash()));
		else if ( previousHash != null )
			writer.writeHash(new Hash (previousHash));
		else
			writer.writeHash (Hash.ZERO_HASH);
		writer.writeHash(new Hash (merkleRoot));
		writer.writeUint32(createTime);
		writer.writeUint32(difficultyTarget);
		writer.writeUint32(nonce);

		WireFormat.Reader reader = new WireFormat.Reader(writer.toByteArray());
		hash = reader.hash().toString();
	}
	
	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32(version);
		if ( previous != null )
			writer.writeHash (new Hash (previous.getHash()));
		else if ( previousHash != null )
			writer.writeHash(new Hash (previousHash));
		else
			writer.writeHash (Hash.ZERO_HASH);
		writer.writeHash(new Hash (merkleRoot));
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
	
	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor();
		version = reader.readUint32();

		previousHash = reader.readHash().toString();
		previous = null;
		
		merkleRoot = reader.readHash().toString ();
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
				t.fromWire(reader);
				transactions.add(t);
			}
		}
		else
			transactions = null;
		
		hash = reader.hash(cursor, 80).toString(); 
	}
	
	public void validate (EntityManager entityManager) throws ValidationException
	{
		if ( previousHash == null )
			return;
		
		if ( !previousHash.equals(Hash.ZERO_HASH.toString()) )
		{
			QJpaBlock block = QJpaBlock.jpaBlock;
			JPAQuery query = new JPAQuery(entityManager);
			previous = query.from(block).where(block.hash.eq(previousHash)).uniqueResult(block);
			if ( previous == null )
				throw new ValidationException ("Previous block '" + previousHash +"' not found");
			
			previousHash = null;
		}
		boolean coinbase = true;
		for ( JpaTransaction t : transactions )
		{
			t.validate (entityManager, coinbase);
			coinbase = false;
		}
	}
}
