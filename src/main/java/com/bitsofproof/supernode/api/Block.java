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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Block implements Serializable, Cloneable
{
	private static final long serialVersionUID = 2846027750944390897L;

	private String hash;
	private long version;
	private String previousHash;
	private String merkleRoot;
	private long createTime;
	private long difficultyTarget;
	private long nonce;
	List<Transaction> transactions;

	@Override
	public Block clone () throws CloneNotSupportedException
	{
		Block c = (Block) super.clone ();
		c.hash = hash;
		c.version = version;
		c.previousHash = previousHash;
		c.merkleRoot = merkleRoot;
		c.difficultyTarget = difficultyTarget;
		c.nonce = nonce;
		if ( transactions != null )
		{
			c.transactions = new ArrayList<Transaction> (transactions.size ());
			for ( Transaction t : transactions )
			{
				c.transactions.add (t.clone ());
			}
		}
		return c;
	}

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
		if ( transactions != null )
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

	public static Block fromWireDump (String dump)
	{
		return fromWire (new WireFormat.Reader (ByteUtils.fromHex (dump)));
	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("hash", hash);
			o.put ("version", version);
			o.put ("previous", previousHash);
			o.put ("merkleRoot", merkleRoot);
			o.put ("createTime", createTime);
			o.put ("difficultyTarget", difficultyTarget);
			o.put ("nonce", nonce);
			if ( transactions != null )
			{
				List<JSONObject> txJSON = new ArrayList<JSONObject> ();
				for ( Transaction t : transactions )
				{
					txJSON.add (t.toJSON ());
				}
				o.put ("transactions", txJSON);
			}
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public static Block fromJSON (JSONObject o) throws JSONException
	{
		Block block = new Block ();
		block.version = o.getLong ("version");
		block.createTime = o.getLong ("createTime");
		block.difficultyTarget = o.getLong ("difficultyTarget");
		block.nonce = o.getLong ("nonce");
		block.previousHash = o.getString ("previous");
		block.transactions = new ArrayList<Transaction> ();
		JSONArray tl = o.getJSONArray ("transactions");
		if ( tl != null )
		{
			for ( int i = 0; i < tl.length (); ++i )
			{
				block.transactions.add (Transaction.fromJSON (tl.getJSONObject (i)));
			}
		}
		block.computeHash ();
		return block;
	}
}
