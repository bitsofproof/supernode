package org.purser.server;

import hu.blummers.bitcoin.core.WireFormat;

import java.math.BigInteger;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

@Entity
public class JpaTransactionOutput {
	@Id
	@GeneratedValue
	private Long id;

	private BigInteger value;
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] script;

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
}
