package org.purser.server;

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
@Table(name="address")
public class JpaAddress {
	@Id	
	@GeneratedValue
	private Long id;
	
	@Column(length=40,nullable=false, unique=true)
	private String address;
	
	@OneToMany(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	private List<JpaTransactionOutput> txouts;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public List<JpaTransactionOutput> getTxouts() {
		return txouts;
	}

	public void setTxouts(List<JpaTransactionOutput> txouts) {
		this.txouts = txouts;
	}
}
