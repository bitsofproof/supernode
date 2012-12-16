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
package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class Block implements Serializable, Cloneable
{
	private static final long serialVersionUID = 8656709442771257759L;

	private String hash;
	private long version;
	private String previousHash;
	private String merkleRoot;
	private long createTime;
	private long difficultyTarget;
	private long nonce;
	List<Transaction> transactions;

	public String getHash ()
	{
		return hash;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public String getPreviousHash ()
	{
		return previousHash;
	}

	public void setPreviousHash (String previousHash)
	{
		this.previousHash = previousHash;
	}

	public void computeHash ()
	{
		computeMerkleRoot ();

		WireFormat.Writer writer = new WireFormat.Writer ();
		toWireHeaderOnly (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());

		hash = reader.hash ().toString ();

		if ( transactions != null )
		{
			for ( Transaction t : transactions )
			{
				t.setBlockHash (hash);
			}
		}
	}

	public void computeMerkleRoot ()
	{
		ArrayList<byte[]> tree = new ArrayList<byte[]> ();
		int nt = transactions.size ();

		for ( Transaction t : transactions )
		{
			tree.add (new Hash (t.getHash ()).toByteArray ());
		}
		int levelOffset = 0;
		try
		{
			MessageDigest digest = MessageDigest.getInstance ("SHA-256");

			for ( int levelSize = nt; levelSize > 1; levelSize = (levelSize + 1) / 2 )
			{
				for ( int left = 0; left < levelSize; left += 2 )
				{
					int right = Math.min (left + 1, levelSize - 1);
					byte[] leftBytes = tree.get (levelOffset + left);
					byte[] rightBytes = tree.get (levelOffset + right);
					digest.update (leftBytes);
					digest.update (rightBytes);
					tree.add (digest.digest (digest.digest ()));
				}
				levelOffset += levelSize;
			}
		}
		catch ( NoSuchAlgorithmException e )
		{
		}

		merkleRoot = new Hash (tree.get (tree.size () - 1)).toString ();
	}

	public String getMerkleRoot ()
	{
		return merkleRoot;
	}

	public void setHash (String hash)
	{
		this.hash = hash;
	}

	public void setMerkleRoot (String merkleRoot)
	{
		this.merkleRoot = merkleRoot;
	}

	public long getCreateTime ()
	{
		return createTime;
	}

	public void setCreateTime (long createTime)
	{
		this.createTime = createTime;
	}

	public long getDifficultyTarget ()
	{
		return difficultyTarget;
	}

	public void setDifficultyTarget (long difficultyTarget)
	{
		this.difficultyTarget = difficultyTarget;
	}

	public long getNonce ()
	{
		return nonce;
	}

	public void setNonce (long nonce)
	{
		this.nonce = nonce;
	}

	public List<Transaction> getTransactions ()
	{
		return transactions;
	}

	public void setTransactions (List<Transaction> transactions)
	{
		this.transactions = transactions;
	}

	public void toWireHeaderOnly (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		writer.writeHash (new Hash (previousHash));
		writer.writeHash (new Hash (merkleRoot));
		writer.writeUint32 (createTime);
		writer.writeUint32 (difficultyTarget);
		writer.writeUint32 (nonce);
	}

	public void toWire (WireFormat.Writer writer)
	{
		toWireHeaderOnly (writer);
		if ( transactions != null )
		{
			writer.writeVarInt (transactions.size ());
			for ( Transaction t : transactions )
			{
				t.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}
	}

	public static Block fromWire (WireFormat.Reader reader)
	{
		Block b = new Block ();

		int cursor = reader.getCursor ();
		b.version = reader.readUint32 ();

		b.previousHash = reader.readHash ().toString ();
		b.merkleRoot = reader.readHash ().toString ();
		b.createTime = reader.readUint32 ();
		b.difficultyTarget = reader.readUint32 ();
		b.nonce = reader.readUint32 ();
		b.hash = reader.hash (cursor, 80).toString ();
		long nt = reader.readVarInt ();
		if ( nt > 0 )
		{
			b.transactions = new ArrayList<Transaction> ();
			for ( long i = 0; i < nt; ++i )
			{
				b.transactions.add (Transaction.fromWire (reader));
			}
		}
		return b;
	}
}
