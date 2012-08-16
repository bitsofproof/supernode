package org.purser.server;

import java.math.BigInteger;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity
public class JpaStoredBlock {
	@Id
	@Column(length=64)
	private String hash;
	private BigInteger chainWork;
	private int height;
	
	@Lob @Basic(fetch=FetchType.LAZY)
	private byte [] header;

	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public BigInteger getChainWork() {
		return chainWork;
	}

	public void setChainWork(BigInteger chainWork) {
		this.chainWork = chainWork;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public byte[] getHeader() {
		return header;
	}

	public void setHeader(byte[] header) {
		this.header = header;
	}
	
	
}
