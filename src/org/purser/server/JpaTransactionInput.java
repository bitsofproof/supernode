package org.purser.server;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.WireFormat;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import com.mysema.query.jpa.impl.JPAQuery;

@Entity
public class JpaTransactionInput {
	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=true) 
	private JpaTransaction source;
	private long ix;
	
	private long sequence;	
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] signature;
	
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

	public byte[] getSignature() {
		return signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public void toWire (WireFormat.Writer writer)
	{
		if ( source != null )
		{
			writer.writeHash(new Hash (source.getHash()));
			writer.writeUint32(ix);
		}
		else
		{
			writer.writeBytes(Hash.ZERO_HASH.toByteArray());
			writer.writeUint32(-1);
		}
		writer.writeVarBytes(signature);
		writer.writeUint32(sequence);
	}
	
	public void fromWire (WireFormat.Reader reader, EntityManager entityManager)
	{
		QJpaTransaction transaction = QJpaTransaction.jpaTransaction;
		JPAQuery query = new JPAQuery(entityManager);
		source = query
				.from(transaction)
				.where(transaction.hash.eq(reader.readHash().toString())).uniqueResult(transaction);
		ix = reader.readUint32();
		signature = reader.readVarBytes();
		sequence = reader.readUint32();
	}
}
