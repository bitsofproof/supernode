package org.purser.server;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.WireFormat;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

@Entity
public class JpaTransactionInput {
	@Id
	@GeneratedValue
	private Long id;

	@Column(length=64,nullable=false)
	private String sourceHash;
	
	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=true) 
	private JpaTransaction source;
	private long ix;
	
	private long sequence;	
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] script;
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public JpaTransaction getSource() {
		return source;
	}

	public void setSource(JpaTransaction source) {
		this.source = source;
	}

	public long getIx() {
		return ix;
	}

	public void setIx(long ix) {
		this.ix = ix;
	}

	public long getSequence() {
		return sequence;
	}

	public void setSequence(long sequence) {
		this.sequence = sequence;
	}

	public byte[] getScript() {
		return script;
	}

	public void setScript(byte[] script) {
		this.script = script;
	}

	public void toWire (WireFormat.Writer writer)
	{
		if ( sourceHash != null )
		{
			writer.writeHash(new Hash (sourceHash));
			writer.writeUint32(ix);
		}
		else
		{
			writer.writeBytes(Hash.ZERO_HASH.toByteArray());
			writer.writeUint32(-1);
		}
		writer.writeVarBytes(script);
		writer.writeUint32(sequence);
	}
	
	public void fromWire (WireFormat.Reader reader)
	{
		sourceHash = reader.readHash().toString();
		ix = reader.readUint32();
		script = reader.readVarBytes();
		sequence = reader.readUint32();
	}
}
