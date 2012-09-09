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
@Table(name="adr")
public class JpaAddress {
	@Id	@GeneratedValue
	private Long id;
	
	@Column(length=40)
	private String address;
	
	@OneToMany(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH})
	List<JpaTransactionOutput> outs;

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

	public List<JpaTransactionOutput> getOuts() {
		return outs;
	}

	public void setOuts(List<JpaTransactionOutput> outs) {
		this.outs = outs;
	}

}
