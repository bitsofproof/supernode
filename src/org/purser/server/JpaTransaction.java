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
	
	private int version;
	
	@ManyToOne(optional=false,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	private JpaBlock block;
	
	private long ix;
	
	private long lockTime;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL)
	private List<JpaTransactionInput> inputs;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL)
	private List<JpaTransactionOutput> outputs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public JpaBlock getBlock() {
		return block;
	}

	public void setBlock(JpaBlock block) {
		this.block = block;
	}


	public long getIx() {
		return ix;
	}

	public void setIx(long ix) {
		this.ix = ix;
	}

	public long getLockTime() {
		return lockTime;
	}

	public void setLockTime(long lockTime) {
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
