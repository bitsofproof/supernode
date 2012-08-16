package org.purser.server;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;


@Entity
public class JpaBlock {
	@Id
	@Column(length=64)
	private String hash;

	int version;
	
	@OneToOne(fetch=FetchType.LAZY,targetEntity=JpaBlock.class,optional=false)
	private JpaBlock previous;
	
	@Column(length=64)
	private String merkleRoot;
	
	private int createTime;
	
	private int difficultyTarget;
	
	private int nonce;
	
	@OneToMany(fetch=FetchType.LAZY,orphanRemoval=true,cascade=CascadeType.ALL)
	private List<JpaTransaction> transactions;

	@Lob @Basic(fetch=FetchType.EAGER)
	private byte [] chainWork;
	private int height;

	public String getHash() {
		return hash;
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

	public void setHash(String hash) {
		this.hash = hash;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
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

	public int getCreateTime() {
		return createTime;
	}

	public void setCreateTime(int createTime) {
		this.createTime = createTime;
	}

	public int getDifficultyTarget() {
		return difficultyTarget;
	}

	public void setDifficultyTarget(int difficultyTarget) {
		this.difficultyTarget = difficultyTarget;
	}

	public int getNonce() {
		return nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}
}
