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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "blk")
public class Blk implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false, unique = true)
	private String hash;

	long version;

	@Column (length = 64, nullable = false)
	private String previousHash;

	transient private byte[] wireTransactions;

	@Column (length = 64, nullable = false)
	private String merkleRoot;

	private long createTime;
	private long difficultyTarget;
	private double chainWork;
	private int height;
	private long nonce;

	@ManyToOne (optional = false, fetch = FetchType.LAZY)
	private Head head;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<Tx> transactions;

	private transient ArrayList<String> txHashes;
	private transient Long headId;

	public static Blk fromLevelDB (byte[] data, boolean txhashes)
	{
		Blk b = new Blk ();
		WireFormat.Reader reader = new WireFormat.Reader (data);
		b.hash = reader.readHash ().toString ();
		b.height = (int) reader.readUint32 ();
		b.version = reader.readUint32 ();
		b.previousHash = reader.readHash ().toString ();
		b.merkleRoot = reader.readHash ().toString ();
		b.createTime = reader.readUint32 ();
		b.difficultyTarget = reader.readUint32 ();
		b.nonce = reader.readUint32 ();
		b.headId = reader.readUint64 ();
		if ( txhashes )
		{
			long nt = reader.readVarInt ();
			b.txHashes = new ArrayList<String> ((int) nt);
			for ( long i = 0; i < nt; ++i )
			{
				b.txHashes.add (reader.readHash ().toString ());
			}
		}
		return b;
	}

	public byte[] toLevelDB ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeHash (new Hash (hash));
		writer.writeUint32 (height);
		writer.writeUint32 (version);
		writer.writeHash (new Hash (previousHash));
		writer.writeHash (new Hash (merkleRoot));
		writer.writeUint32 (createTime);
		writer.writeUint32 (difficultyTarget);
		writer.writeUint32 (nonce);
		writer.writeUint64 (headId = head.getId ());
		writer.writeVarInt (transactions.size ());
		for ( long i = 0; i < transactions.size (); ++i )
		{
			writer.writeHash (new Hash (transactions.get ((int) i).getHash ()));
		}
		return writer.toByteArray ();
	}

	public ArrayList<String> getTxHashes ()
	{
		return txHashes;
	}

	public JSONObject toJSON ()
	{
		parseTransactions ();

		JSONObject o = new JSONObject ();
		try
		{
			o.put ("hash", hash);
			o.put ("version", version);
			o.put ("previous", previousHash);
			o.put ("merkleRoot", merkleRoot);
			o.put ("createTime", createTime);
			o.put ("difficultyTarget", difficultyTarget);
			o.put ("chainWork", chainWork);
			o.put ("height", height);
			o.put ("nonce", nonce);
			List<JSONObject> txJSON = new ArrayList<JSONObject> ();
			for ( Tx t : transactions )
			{
				txJSON.add (t.toJSON ());
			}
			o.put ("transactions", txJSON);
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getHash ()
	{
		return hash;
	}

	public void setHash (String hash)
	{
		this.hash = hash;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public String getMerkleRoot ()
	{
		return merkleRoot;
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

	public List<Tx> getTransactions ()
	{
		return transactions;
	}

	public void setTransactions (List<Tx> transactions)
	{
		this.transactions = transactions;
	}

	public double getChainWork ()
	{
		return chainWork;
	}

	public void setChainWork (double chainWork)
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

	public Head getHead ()
	{
		return head;
	}

	public void setHead (Head head)
	{
		this.head = head;
	}

	public String getPreviousHash ()
	{
		return previousHash;
	}

	public void setPreviousHash (String previousHash)
	{
		this.previousHash = previousHash;
	}

	private String computeMerkleRoot ()
	{
		parseTransactions ();

		ArrayList<byte[]> tree = new ArrayList<byte[]> ();
		int nt = transactions.size ();

		// Start by adding all the hashes of the transactions as leaves of the tree.
		for ( Tx t : transactions )
		{
			tree.add (new Hash (t.getHash ()).toByteArray ());
		}
		int levelOffset = 0;
		try
		{
			MessageDigest digest = MessageDigest.getInstance ("SHA-256");

			// Step through each level, stopping when we reach the root (levelSize == 1).
			for ( int levelSize = nt; levelSize > 1; levelSize = (levelSize + 1) / 2 )
			{
				// For each pair of nodes on that level:
				for ( int left = 0; left < levelSize; left += 2 )
				{
					// The right hand node can be the same as the left hand, in the case where we don't have enough
					// transactions.
					int right = Math.min (left + 1, levelSize - 1);
					byte[] leftBytes = tree.get (levelOffset + left);
					byte[] rightBytes = tree.get (levelOffset + right);
					digest.update (leftBytes);
					digest.update (rightBytes);
					tree.add (digest.digest (digest.digest ()));
				}
				// Move to the next level.
				levelOffset += levelSize;
			}
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
		return new Hash (tree.get (tree.size () - 1)).toString ();
	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	public static Blk fromWireDump (String s)
	{
		WireFormat.Reader reader = new WireFormat.Reader (ByteUtils.fromHex (s));
		Blk b = new Blk ();
		b.fromWire (reader);
		return b;
	}

	public void toWire (WireFormat.Writer writer)
	{
		toWireHeaderOnly (writer);
		if ( wireTransactions != null )
		{
			writer.writeBytes (wireTransactions);
		}
		else
		{
			writer.writeVarInt (transactions.size ());
			for ( Tx t : transactions )
			{
				t.toWire (writer);
			}
		}
	}

	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor ();
		version = reader.readUint32 ();

		previousHash = reader.readHash ().toString ();
		merkleRoot = reader.readHash ().toString ();
		createTime = reader.readUint32 ();
		difficultyTarget = reader.readUint32 ();
		nonce = reader.readUint32 ();

		wireTransactions = reader.readRest ();
		transactions = null;

		hash = reader.hash (cursor, 80).toString ();
	}

	public void parseTransactions ()
	{
		if ( wireTransactions == null )
		{
			return;
		}

		WireFormat.Reader reader = new WireFormat.Reader (wireTransactions);
		long nt = reader.readVarInt ();
		transactions = new ArrayList<Tx> ();
		for ( long i = 0; i < nt; ++i )
		{
			Tx t = new Tx ();
			t.fromWire (reader);
			t.setIx (i);
			if ( i == 0 )
			{
				for ( TxOut out : t.getOutputs () )
				{
					out.setCoinbase (true);
				}
			}
			transactions.add (t);
		}
		wireTransactions = null;
	}

	public void checkMerkleRoot () throws ValidationException
	{
		if ( !merkleRoot.equals (computeMerkleRoot ()) )
		{
			throw new ValidationException ("merkle root mismatch");
		}
	}

	public void checkHash () throws ValidationException
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWireHeaderOnly (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
		if ( !reader.hash ().toString ().equals (hash) )
		{
			throw new ValidationException ("block hash mismatch");
		}
	}

	public void computeHash ()
	{
		merkleRoot = computeMerkleRoot ();

		WireFormat.Writer writer = new WireFormat.Writer ();
		toWireHeaderOnly (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());

		hash = reader.hash ().toString ();
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

	public Long getHeadId ()
	{
		return headId;
	}

	public void setHeadId (Long headId)
	{
		this.headId = headId;
	}

}
