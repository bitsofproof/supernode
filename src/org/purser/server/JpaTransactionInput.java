package org.purser.server;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

@Entity
public class JpaTransactionInput {
	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional=false)
	private JpaTransaction transaction;
	
	@ManyToOne(fetch=FetchType.LAZY,optional=false) 
	private JpaBlock block;
	
	private int index;
	
	private int sequence;
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] script;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public JpaTransaction getTransaction() {
		return transaction;
	}

	public void setTransaction(JpaTransaction transaction) {
		this.transaction = transaction;
	}

	public JpaBlock getBlock() {
		return block;
	}

	public void setBlock(JpaBlock block) {
		this.block = block;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getSequence() {
		return sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public byte[] getScript() {
		return script;
	}

	public void setScript(byte[] script) {
		this.script = script;
	}
	
	
}
