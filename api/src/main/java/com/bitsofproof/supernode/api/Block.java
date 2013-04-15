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
package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;

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
			for ( Transaction t : transactions )
			{
				t.computeHash ();
				tree.add (new Hash (t.getHash ()).toByteArray ());
			}
			BinaryAggregator<byte[]> aggregator = new BinaryAggregator<byte[]> ()
			{
				@Override
				public byte[] merge (byte[] a, byte[] b)
				{
					try
					{
						MessageDigest digest = MessageDigest.getInstance ("SHA-256");
						digest.update (a);
						return digest.digest (digest.digest (b));
					}
					catch ( NoSuchAlgorithmException e )
					{
						return null;
					}
				}
			};
			merkleRoot = new Hash (aggregator.aggregate (tree)).toString ();
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

	public BCSAPIMessage.Block toProtobuf ()
	{
		BCSAPIMessage.Block.Builder builder = BCSAPIMessage.Block.newBuilder ();
		builder.setBcsapiversion (1);
		builder.setVersion ((int) version);
		builder.setDifficulty ((int) difficultyTarget);
		builder.setNonce ((int) nonce);
		builder.setTimestamp ((int) createTime);
		builder.setMerkleRoot (ByteString.copyFrom (new Hash (merkleRoot).toByteArray ()));
		builder.setPreviousBlock (ByteString.copyFrom (new Hash (previousHash).toByteArray ()));
		if ( transactions != null )
		{
			for ( Transaction t : transactions )
			{
				builder.addTransactions (t.toProtobuf ());
			}
		}
		return builder.build ();
	}

	public static Block fromProtobuf (BCSAPIMessage.Block pb)
	{
		Block block = new Block ();
		block.setVersion (pb.getVersion ());
		block.setDifficultyTarget (pb.getDifficulty ());
		block.setNonce (pb.getNonce ());
		block.setCreateTime (pb.getTimestamp ());
		block.setPreviousHash (new Hash (pb.getPreviousBlock ().toByteArray ()).toString ());
		block.setMerkleRoot (new Hash (pb.getMerkleRoot ().toByteArray ()).toString ());
		if ( pb.getTransactionsCount () > 0 )
		{
			block.setTransactions (new ArrayList<Transaction> ());
			for ( BCSAPIMessage.Transaction t : pb.getTransactionsList () )
			{
				block.getTransactions ().add (Transaction.fromProtobuf (t));
			}
		}
		return block;
	}
}
