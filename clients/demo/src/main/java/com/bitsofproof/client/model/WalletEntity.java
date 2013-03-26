/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.client.model;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table (name = "wallet")
public class WalletEntity
{
	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, unique = true, nullable = false)
	private String name;

	@OneToOne (fetch = FetchType.EAGER, optional = false, cascade = CascadeType.ALL)
	private KeyEntity master;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	@Column (length = 32, nullable = false)
	private byte[] chainCode;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<WalletEntity> subs;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<KeyEntity> importedKeys;

	private int addressFlag = 0x0;
	private int p2shAddressFlag = 0x05;

	private int nextKey = 0;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public KeyEntity getMaster ()
	{
		return master;
	}

	public void setMaster (KeyEntity master)
	{
		this.master = master;
	}

	public List<WalletEntity> getSubs ()
	{
		return subs;
	}

	public void setSubs (List<WalletEntity> subs)
	{
		this.subs = subs;
	}

	public List<KeyEntity> getImportedKeys ()
	{
		return importedKeys;
	}

	public void setImportedKeys (List<KeyEntity> importedKeys)
	{
		this.importedKeys = importedKeys;
	}

	public byte[] getChainCode ()
	{
		return chainCode;
	}

	public void setChainCode (byte[] chainCode)
	{
		this.chainCode = chainCode;
	}

	public int getNextKey ()
	{
		return nextKey;
	}

	public void setNextKey (int nextKey)
	{
		this.nextKey = nextKey;
	}

	public String getName ()
	{
		return name;
	}

	public void setName (String name)
	{
		this.name = name;
	}

	public int getAddressFlag ()
	{
		return addressFlag;
	}

	public void setAddressFlag (int addressFlag)
	{
		this.addressFlag = addressFlag;
	}

	public int getP2shAddressFlag ()
	{
		return p2shAddressFlag;
	}

	public void setP2shAddressFlag (int p2shAddressFlag)
	{
		this.p2shAddressFlag = p2shAddressFlag;
	}

}
