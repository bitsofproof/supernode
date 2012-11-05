/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "txout")
public class TxOut implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	private long value;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	// scriptPubKey
	private byte[] script;

	@ManyToOne (fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.DETACH, CascadeType.PERSIST, CascadeType.REFRESH }, optional = false)
	private Tx transaction;

	private long ix;

	private Long votes;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	List<Owner> owners;

	public String toJSON () throws ValidationException
	{
		StringBuffer b = new StringBuffer ();
		b.append ("{");
		b.append ("\"value\":" + getValue () + ",");
		b.append ("\"script\":\"" + Script.toReadable (script) + "\"");
		b.append ("}");
		return b.toString ();
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64 (getValue ());
		writer.writeVarBytes (getScript ());
	}

	public void fromWire (WireFormat.Reader reader)
	{
		value = reader.readUint64 ();
		script = reader.readVarBytes ();
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getValue ()
	{
		return value;
	}

	public void setValue (long value)
	{
		this.value = value;
	}

	public byte[] getScript ()
	{
		return script;
	}

	public void setScript (byte[] script)
	{
		this.script = script;
	}

	public Tx getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (Tx transaction)
	{
		this.transaction = transaction;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
	}

	public Long getVotes ()
	{
		return votes;
	}

	public void setVotes (Long votes)
	{
		this.votes = votes;
	}

	public List<Owner> getOwners ()
	{
		return owners;
	}

	public void setOwners (List<Owner> owners)
	{
		this.owners = owners;
	}

	protected TxOut flatCopy (Tx tc)
	{
		TxOut c = new TxOut ();
		c.ix = ix;
		c.script = script;
		c.transaction = tc;
		c.value = value;

		return c;
	}

}
