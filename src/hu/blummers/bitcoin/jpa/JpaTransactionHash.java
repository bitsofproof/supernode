package hu.blummers.bitcoin.jpa;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name="txhash")
public class JpaTransactionHash {	
	@Id	
	@GeneratedValue
	private Long id;
	
	@Column(length=64,nullable=false,unique=true)
	private String hash;
	
	//was @OneToMany(fetch=FetchType.EAGER)
	@OneToMany(fetch=FetchType.EAGER,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	private List<JpaTransaction> transactions;

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

	public List<JpaTransaction> getTransactions() {
		return transactions;
	}

	public void setTransactions(List<JpaTransaction> transactions) {
		this.transactions = transactions;
	}

}
