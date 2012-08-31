package org.purser.server;

import hu.blummers.bitcoin.core.WireFormat;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.mysema.query.jpa.impl.JPAQuery;

@Entity
@Table(name="tx")
public class JpaTransaction {

	@Id
	@GeneratedValue
	private Long id;
	
	private long version;
	
	private long lockTime;

	// this should really be one-to-on and part of this class but unfortunately not unique on the chain see http://r6.ca/blog/20120206T005236Z.html
	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=false) 
	private JpaTransactionHash hash;
	
	@ManyToOne(fetch=FetchType.LAZY,cascade={CascadeType.MERGE,CascadeType.DETACH,CascadeType.PERSIST,CascadeType.REFRESH},optional=true) 
	private JpaBlock block;

	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL)
	private List<JpaTransactionInput> inputs;
	
	@OneToMany(fetch=FetchType.LAZY,cascade=CascadeType.ALL)
	private List<JpaTransactionOutput> outputs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public long getLockTime() {
		return lockTime;
	}

	public void setLockTime(long lockTime) {
		this.lockTime = lockTime;
	}

	public List<JpaTransactionInput> getInputs() {
		return inputs;
	}

	public void setInputs(List<JpaTransactionInput> inputs) {
		this.inputs = inputs;
	}

	public List<JpaTransactionOutput> getOutputs() {
		return outputs;
	}

	public void setOutputs(List<JpaTransactionOutput> outputs) {
		this.outputs = outputs;
	}
	
	public JpaTransactionHash getHash() {
		return hash;
	}

	public void setHash(JpaTransactionHash hash) {
		this.hash = hash;
	}

	public JpaBlock getBlock() {
		return block;
	}

	public void setBlock(JpaBlock block) {
		this.block = block;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32(version);
		if ( inputs != null )
		{
			writer.writeVarInt(inputs.size());
			for ( JpaTransactionInput input : inputs )
				input.toWire(writer);
		}
		else
			writer.writeVarInt(0);

		if ( outputs != null )
		{
			writer.writeVarInt(outputs.size());
			for ( JpaTransactionOutput output : outputs )
				output.toWire(writer);
		}
		else
			writer.writeVarInt(0);
			
		writer.writeUint32(lockTime);	
	}

	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor();
		
		version = reader.readUint32();
		long nin = reader.readVarInt();
		if ( nin > 0 )
		{
			inputs = new ArrayList<JpaTransactionInput> ();
			for ( int i = 0; i < nin; ++i )
			{
				JpaTransactionInput input = new JpaTransactionInput ();
				input.fromWire(reader);
				input.setTransaction(this);
				inputs.add(input);
			}
		}
		else
			inputs = null;
		
		long nout = reader.readVarInt();
		if ( nout > 0 )
		{
			outputs = new ArrayList<JpaTransactionOutput> ();
			for ( int i = 0; i < nout; ++i )
			{
				JpaTransactionOutput output = new JpaTransactionOutput ();
				output.fromWire(reader);
				output.setTransaction(this);
				output.setIx(i);
				outputs.add(output);
			}
		}
		else
			outputs = null;
		
		lockTime = reader.readUint32();
		
		hash = new JpaTransactionHash ();
		hash.setHash(reader.hash(cursor, reader.getCursor() - cursor).toString());
		hash.setTransactions(new ArrayList<JpaTransaction> ());
		hash.getTransactions().add(this);
	}
	
	public void validate (EntityManager entityManager, boolean coinbase) throws ValidationException
	{
		QJpaTransactionHash ht = QJpaTransactionHash.jpaTransactionHash;
		JPAQuery query = new JPAQuery(entityManager);
		JpaTransactionHash storedHash = query.from(ht).where(ht.hash.eq(hash.getHash())).uniqueResult(ht);
		if ( storedHash != null )
			hash = storedHash;

		for ( JpaTransactionOutput output : outputs )
			output.validate (entityManager);

		if ( !coinbase )
			for ( JpaTransactionInput input : inputs )
				input.validate (entityManager);

	}
}
