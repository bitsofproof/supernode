package org.purser.server;

import hu.blummers.bitcoin.core.WireFormat;

import java.math.BigInteger;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name="txout")
public class JpaTransactionOutput {
	@Id
	@GeneratedValue
	private Long id;

	private BigInteger value;
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] script;
	
	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=false) 
	private JpaTransaction transaction;

	private long ix;
	
	@OneToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=true) 
	private JpaTransactionInput sink;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public BigInteger getValue() {
		return value;
	}

	public void setValue(BigInteger value) {
		this.value = value;
	}

	public byte[] getScript() {
		return script;
	}

	public void setScript(byte[] script) {
		this.script = script;
	}
	
	public JpaTransaction getTransaction() {
		return transaction;
	}

	public void setTransaction(JpaTransaction transaction) {
		this.transaction = transaction;
	}

	public long getIx() {
		return ix;
	}

	public void setIx(long ix) {
		this.ix = ix;
	}

	public JpaTransactionInput getSink() {
		return sink;
	}

	public void setSink(JpaTransactionInput sink) {
		this.sink = sink;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64(getValue());
		writer.writeVarBytes(getScript());
	}
	
	public void fromWire (WireFormat.Reader reader)
	{
		setValue (reader.readUint64());
		setScript (reader.readVarBytes());
	}
	public void validate (EntityManager entityManager) throws ValidationException
	{
		
	}
}
