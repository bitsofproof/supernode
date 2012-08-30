package org.purser.server;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name="txhash")
public class JpaTransactionHash {	
	@Id @Column(length=64,nullable=false)
	private String hash;
	
	@OneToMany(fetch=FetchType.EAGER,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	private List<JpaTransaction> transactions;

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public List<JpaTransaction> getTransactions() {
		return transactions;
	}

	public void setTransactions(List<JpaTransaction> transactions) {
		this.transactions = transactions;
	}

}
