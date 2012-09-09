package hu.blummers.bitcoin.jpa;

import java.util.List;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.ValidationException;
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
import javax.persistence.OneToOne;
import javax.persistence.Table;


import com.mysema.query.jpa.impl.JPAQuery;

@Entity
@Table(name="txin")
public class JpaTransactionInput {
	@Id
	@GeneratedValue
	private Long id;

	transient private String sourceHash;
	transient private long ix;
	
	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=false) 
	private JpaTransaction transaction;
	
	@OneToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=true) 
	private JpaTransactionOutput source;
	
	private long sequence;	
	
	@Lob  @Basic(fetch=FetchType.LAZY)
	private byte [] script;
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getIx() {
		return ix;
	}

	public void setIx(long ix) {
		this.ix = ix;
	}

	public String getSourceHash() {
		return sourceHash;
	}

	public void setSourceHash(String sourceHash) {
		this.sourceHash = sourceHash;
	}

	public JpaTransactionOutput getSource() {
		return source;
	}

	public void setSource(JpaTransactionOutput source) {
		this.source = source;
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

	public JpaTransaction getTransaction() {
		return transaction;
	}

	public void setTransaction(JpaTransaction transaction) {
		this.transaction = transaction;
	}

	public void toWire (WireFormat.Writer writer)
	{
		if ( source != null )
		{
			writer.writeHash(new Hash (source.getTransaction().getHash()));
			writer.writeUint32(source.getIx());
		}
		else if ( sourceHash != null )
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
		source = null;
		script = reader.readVarBytes();
		sequence = reader.readUint32();
	}
	
	public void validate (EntityManager entityManager) throws ValidationException
	{
		if ( sourceHash == null )
			return;
		QJpaTransaction tx = QJpaTransaction.jpaTransaction;
		JPAQuery query = new JPAQuery (entityManager);
		List<JpaTransaction> tl = query.from(tx).where(tx.hash.eq(sourceHash)).list(tx);
		if ( tl == null || tl.isEmpty() )
			throw new ValidationException ("Transaction input refers to unknown transaction '" + sourceHash + "'");
			
		JpaTransaction latest = tl.get(tl.size()-1);
		if ( latest.getOutputs().size() <= ix )
		{
			throw new ValidationException ("Transaction input refers to unknown output index "+ ix +" of transaction '" + sourceHash + "'");
		}
		JpaTransactionOutput prevout = latest.getOutputs().get((int)ix);
		if ( prevout.getSink() != null )
			throw new ValidationException ("Transaction input refers to spent output "+ ix +" of transaction '" + sourceHash + "'");
		source = prevout;
		source.setSink(this);
		sourceHash = null;
		
	}
}
