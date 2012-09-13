package hu.blummers.bitcoin.jpa;

import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.ValidationException;
import hu.blummers.bitcoin.core.WireFormat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name="tx")
public class JpaTransaction {

	@Id
	@GeneratedValue
	private Long id;
	
	private long version;
	
	private long lockTime;

	// this is not unique on the chain see http://r6.ca/blog/20120206T005236Z.html	
	@Column(length=64,nullable=false)
	private String hash;
	
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
	
	public String getHash() {
		return hash;
	}

	public void calculateHash ()
	{
		if ( hash != null )
			return;
		
		WireFormat.Writer writer = new WireFormat.Writer(new ByteArrayOutputStream());
		toWire (writer);
		WireFormat.Reader reader = new WireFormat.Reader(writer.toByteArray());
		hash = reader.hash().toString();
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
		
		hash = reader.hash(cursor, reader.getCursor() - cursor).toString();
	}
	
	public void validate (ChainStore store, boolean coinbase) throws ValidationException
	{
		for ( JpaTransactionOutput output : outputs )
			output.validate (store);

		if ( !coinbase )
			for ( JpaTransactionInput input : inputs )
				input.validate (store);

	}
}
