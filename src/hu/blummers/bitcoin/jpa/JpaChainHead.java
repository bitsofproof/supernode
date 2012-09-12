package hu.blummers.bitcoin.jpa;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name="blkhead")
public class JpaChainHead {
	@Id
	@GeneratedValue
	private Long id;
	private double chainWork;
	private int height;
	private int joinHeight;
	
	@ManyToOne(fetch=FetchType.LAZY,optional=true)
	private JpaChainHead previous;
	
	@OneToMany(fetch=FetchType.LAZY, orphanRemoval=false)
	private List <JpaBlock> blocks;
	
	@Column(length=64)
	private String hash;

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public double getChainWork() {
		return chainWork;
	}

	public void setChainWork(double chainWork) {
		this.chainWork = chainWork;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getJoinHeight() {
		return joinHeight;
	}

	public void setJoinHeight(int joinHeight) {
		this.joinHeight = joinHeight;
	}

	public JpaChainHead getPrevious() {
		return previous;
	}

	public void setPrevious(JpaChainHead previous) {
		this.previous = previous;
	}

	public List<JpaBlock> getBlocks() {
		return blocks;
	}

	public void setBlocks(List<JpaBlock> blocks) {
		this.blocks = blocks;
	}	
	
	
}
