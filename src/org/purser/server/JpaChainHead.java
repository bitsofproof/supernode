package org.purser.server;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="blkhead")
public class JpaChainHead {
	@Id
	@Column(length=64)
	private String hash;

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}	
}
