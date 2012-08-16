package org.purser.server;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

@Entity
public class JpaTransaction {

	@Id
	@GeneratedValue
	private Long id;
	
	@ManyToOne(optional=false)
	private JpaBlock block;
	
	private int lockTime;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL,orphanRemoval=true)
	private List<JpaTransactionInput> inputs;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL,orphanRemoval=true)
	private List<JpaTransactionOutput> outputs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public JpaBlock getBlock() {
		return block;
	}

	public void setBlock(JpaBlock block) {
		this.block = block;
	}

	public int getLockTime() {
		return lockTime;
	}

	public void setLockTime(int lockTime) {
		this.lockTime = lockTime;
	}

	public List<JpaTransactionInput> getInputs() {
		return inputs;
	}

	public void setInputs(List<JpaTransactionInput> inputs) {
		this.inputs = inputs;
	}

	public List<JpaTransactionOutput> getOutputs() {
		return outputs;
	}

	public void setOutputs(List<JpaTransactionOutput> outputs) {
		this.outputs = outputs;
	}

}
