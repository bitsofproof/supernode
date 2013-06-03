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
package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

@Entity
@Table (name = "head")
public class Head implements Serializable
{
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue
	private Long id;
	private long chainWork;
	private int height;

	private Long previousId;
	private int previousHeight;

	@Column (length = 64, nullable = false)
	private String leaf;

	public static Head fromLevelDB (byte[] data) throws ValidationException
	{
		LevelDBStore.HEAD p;
		try
		{
			p = LevelDBStore.HEAD.parseFrom (data);

			Head h = new Head ();
			h.id = p.getId ();
			h.chainWork = p.getChainWork ();
			h.height = p.getHeight ();
			h.leaf = new Hash (p.getLeaf ().toByteArray ()).toString ();
			if ( p.hasPreviousId () )
			{
				h.previousId = p.getPreviousId ();
			}
			h.previousHeight = p.getPreviousHeight ();

			return h;
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new ValidationException (e);
		}
	}

	public byte[] toLevelDB ()
	{
		LevelDBStore.HEAD.Builder builder = LevelDBStore.HEAD.newBuilder ();
		builder.setStoreVersion (1);
		builder.setId (id);
		builder.setChainWork (chainWork);
		builder.setHeight (height);
		builder.setLeaf (ByteString.copyFrom (new Hash (leaf).toByteArray ()));
		if ( previousId != null )
		{
			builder.setPreviousId (previousId);
			builder.setPreviousHeight (previousHeight);
		}
		return builder.build ().toByteArray ();
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getChainWork ()
	{
		return chainWork;
	}

	public void setChainWork (long chainWork)
	{
		this.chainWork = chainWork;
	}

	public int getHeight ()
	{
		return height;
	}

	public void setHeight (int height)
	{
		this.height = height;
	}

	public String getLeaf ()
	{
		return leaf;
	}

	public void setLeaf (String leaf)
	{
		this.leaf = leaf;
	}

	public Long getPreviousId ()
	{
		return previousId;
	}

	public void setPreviousId (Long previousId)
	{
		this.previousId = previousId;
	}

	public int getPreviousHeight ()
	{
		return previousHeight;
	}

	public void setPreviousHeight (int previousHeight)
	{
		this.previousHeight = previousHeight;
	}
}
