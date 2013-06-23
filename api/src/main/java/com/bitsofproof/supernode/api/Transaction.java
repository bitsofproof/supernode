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

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Opcode;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.google.protobuf.ByteString;

public class Transaction implements Serializable, Cloneable
{
	private static final long serialVersionUID = 690918485496086537L;

	private long version = 1;

	private long lockTime = 0;
	private String hash;
	private String blockHash;
	private boolean doubleSpend = false;
	private int height = 0;

	private List<TransactionInput> inputs;
	private List<TransactionOutput> outputs;

	public static Transaction createCoinbase (Key receiver, long value, int blockHeight)
	{
		Transaction cb = new Transaction ();

		cb.setInputs (new ArrayList<TransactionInput> ());
		cb.setOutputs (new ArrayList<TransactionOutput> ());

		TransactionOutput out = new TransactionOutput ();
		out.setValue (value);
		cb.getOutputs ().add (out);

		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_DUP));
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
		writer.writeData (receiver.getAddress ());
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUALVERIFY));
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_CHECKSIG));
		out.setScript (writer.toByteArray ());

		TransactionInput in = new TransactionInput ();
		in.setSourceHash (Hash.ZERO_HASH_STRING);
		in.setIx (0);
		cb.getInputs ().add (in);

		writer = new ScriptFormat.Writer ();
		writer.writeInt32 (blockHeight);
		in.setScript (writer.toByteArray ());

		cb.computeHash ();
		return cb;
	}

	public static class TransactionSource
	{
		private final String source;
		private final long ix;
		private final TransactionOutput output;

		public TransactionSource (String source, long ix, TransactionOutput output)
		{
			this.source = source;
			this.ix = ix;
			this.output = output;
		}

		public TransactionOutput getOutput ()
		{
			return output;
		}

		public String getSource ()
		{
			return source;
		}

		public long getIx ()
		{
			return ix;
		}
	}

	public static class TransactionSink
	{
		private final byte[] address;
		private final long value;

		public TransactionSink (byte[] address, long value)
		{
			super ();
			this.address = Arrays.clone (address);
			this.value = value;
		}

		public byte[] getAddress ()
		{
			return Arrays.clone (address);
		}

		public long getValue ()
		{
			return value;
		}
	}

	public static Transaction createSpend (AddressToKeyMap am, List<TransactionSource> sources, List<TransactionSink> sinks, long fee)
			throws ValidationException
	{
		if ( fee < 0 || fee > 1000000 )
		{
			throw new ValidationException ("You unlikely want to do that");
		}
		Transaction transaction = new Transaction ();
		transaction.setInputs (new ArrayList<TransactionInput> ());
		transaction.setOutputs (new ArrayList<TransactionOutput> ());

		long sumOut = 0;
		for ( TransactionSink s : sinks )
		{
			TransactionOutput o = new TransactionOutput ();
			o.setValue (s.getValue ());
			sumOut += s.getValue ();

			ScriptFormat.Writer writer = new ScriptFormat.Writer ();
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_DUP));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
			if ( s.getAddress ().length != 20 )
			{
				throw new ValidationException ("Sink is not an address");
			}
			writer.writeData (s.getAddress ());
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUALVERIFY));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_CHECKSIG));
			o.setScript (writer.toByteArray ());

			transaction.getOutputs ().add (o);
		}

		long sumInput = 0;
		for ( TransactionSource s : sources )
		{
			TransactionOutput o = s.getOutput ();
			TransactionInput i = new TransactionInput ();
			i.setSourceHash (s.getSource ());
			i.setIx (s.getIx ());
			sumInput += o.getValue ();

			transaction.getInputs ().add (i);
		}
		if ( sumInput != (sumOut + fee) )
		{
			throw new ValidationException ("Sum of sinks (+fee) does not match sum of sources");
		}

		int j = 0;
		for ( TransactionSource s : sources )
		{
			TransactionInput i = transaction.getInputs ().get (j);
			ScriptFormat.Writer sw = new ScriptFormat.Writer ();
			byte[] address = s.getOutput ().getOutputAddress ();
			if ( address == null )
			{
				throw new ValidationException ("Can only spend pay to address outputs");
			}
			Key key = am.getKeyForAddress (address);
			if ( key == null )
			{
				throw new ValidationException ("Have no key to spend this output");
			}
			byte[] sig = key.sign (hashTransaction (transaction, j, ScriptFormat.SIGHASH_ALL, s.getOutput ().getScript ()));
			byte[] sigPlusType = new byte[sig.length + 1];
			System.arraycopy (sig, 0, sigPlusType, 0, sig.length);
			sigPlusType[sigPlusType.length - 1] = (byte) (ScriptFormat.SIGHASH_ALL & 0xff);
			sw.writeData (sigPlusType);
			sw.writeData (key.getPublic ());
			i.setScript (sw.toByteArray ());
			++j;
		}

		transaction.computeHash ();
		return transaction;
	}

	private static byte[] hashTransaction (Transaction transaction, int inr, int hashType, byte[] script) throws ValidationException
	{
		Transaction copy = null;
		try
		{
			copy = transaction.clone ();
		}
		catch ( CloneNotSupportedException e1 )
		{
			return null;
		}

		// implicit SIGHASH_ALL
		int i = 0;
		for ( TransactionInput in : copy.getInputs () )
		{
			if ( i == inr )
			{
				in.setScript (script);
			}
			else
			{
				in.setScript (new byte[0]);
			}
			++i;
		}

		if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_NONE )
		{
			copy.getOutputs ().clear ();
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		else if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_SINGLE )
		{
			int onr = inr;
			if ( onr >= copy.getOutputs ().size () )
			{
				// this is a Satoshi client bug.
				// This case should throw an error but it instead retuns 1 that is not checked and interpreted as below
				return ByteUtils.fromHex ("0100000000000000000000000000000000000000000000000000000000000000");
			}
			for ( i = copy.getOutputs ().size () - 1; i > onr; --i )
			{
				copy.getOutputs ().remove (i);
			}
			for ( i = 0; i < onr; ++i )
			{
				copy.getOutputs ().get (i).setScript (new byte[0]);
				copy.getOutputs ().get (i).setValue (-1L);
			}
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		if ( (hashType & ScriptFormat.SIGHASH_ANYONECANPAY) != 0 )
		{
			List<TransactionInput> oneIn = new ArrayList<TransactionInput> ();
			oneIn.add (copy.getInputs ().get (inr));
			copy.setInputs (oneIn);
		}

		WireFormat.Writer writer = new WireFormat.Writer ();
		copy.toWire (writer);

		byte[] txwire = writer.toByteArray ();
		byte[] hash = null;
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (txwire);
			a.update (new byte[] { (byte) (hashType & 0xff), 0, 0, 0 });
			hash = a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
		return hash;
	}

	public long getVersion ()
	{
		return version;
	}

	public String getBlockHash ()
	{
		return blockHash;
	}

	public void setBlockHash (String blockHash)
	{
		this.blockHash = blockHash;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public long getLockTime ()
	{
		return lockTime;
	}

	public void setLockTime (long lockTime)
	{
		this.lockTime = lockTime;
	}

	public void computeHash ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
		hash = reader.hash ().toString ();
	}

	public String getHash ()
	{
		return hash;
	}

	public void setHash (String hash)
	{
		this.hash = hash;
	}

	public List<TransactionInput> getInputs ()
	{
		return inputs;
	}

	public void setInputs (List<TransactionInput> inputs)
	{
		this.inputs = inputs;
	}

	public List<TransactionOutput> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (List<TransactionOutput> outputs)
	{
		this.outputs = outputs;
	}

	public boolean isDoubleSpend ()
	{
		return doubleSpend;
	}

	public void setDoubleSpend (boolean doubleSpend)
	{
		this.doubleSpend = doubleSpend;
	}

	public int getHeight ()
	{
		return height;
	}

	public void setHeight (int height)
	{
		this.height = height;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		if ( inputs != null )
		{
			writer.writeVarInt (inputs.size ());
			for ( TransactionInput input : inputs )
			{
				input.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		if ( outputs != null )
		{
			writer.writeVarInt (outputs.size ());
			for ( TransactionOutput output : outputs )
			{
				output.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		writer.writeUint32 (lockTime);
	}

	public static Transaction fromWire (WireFormat.Reader reader)
	{
		Transaction t = new Transaction ();

		int cursor = reader.getCursor ();

		t.version = reader.readUint32 ();
		long nin = reader.readVarInt ();
		if ( nin > 0 )
		{
			t.inputs = new ArrayList<TransactionInput> ();
			for ( int i = 0; i < nin; ++i )
			{
				t.inputs.add (TransactionInput.fromWire (reader));
			}
		}
		else
		{
			t.inputs = null;
		}

		long nout = reader.readVarInt ();
		if ( nout > 0 )
		{
			t.outputs = new ArrayList<TransactionOutput> ();
			for ( long i = 0; i < nout; ++i )
			{
				t.outputs.add (TransactionOutput.fromWire (reader));
			}
		}
		else
		{
			t.outputs = null;
		}

		t.lockTime = reader.readUint32 ();

		t.hash = reader.hash (cursor, reader.getCursor () - cursor).toString ();

		return t;
	}

	public static Transaction fromWireDump (String dump)
	{
		return fromWire (new WireFormat.Reader (ByteUtils.fromHex (dump)));
	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	@Override
	public Transaction clone () throws CloneNotSupportedException
	{
		Transaction t = (Transaction) super.clone ();

		t.version = version;
		if ( inputs != null )
		{
			t.inputs = new ArrayList<TransactionInput> (inputs.size ());
			for ( TransactionInput i : inputs )
			{
				t.inputs.add (i.clone ());
			}
		}
		if ( outputs != null )
		{
			t.outputs = new ArrayList<TransactionOutput> (outputs.size ());
			for ( TransactionOutput o : outputs )
			{
				t.outputs.add (o.clone ());
			}
		}

		t.lockTime = lockTime;

		t.hash = hash;

		t.blockHash = blockHash;

		return t;
	}

	public BCSAPIMessage.Transaction toProtobuf ()
	{
		BCSAPIMessage.Transaction.Builder builder = BCSAPIMessage.Transaction.newBuilder ();
		builder.setBcsapiversion (1);
		builder.setLocktime ((int) lockTime);
		builder.setVersion ((int) version);
		if ( inputs != null )
		{
			for ( TransactionInput i : inputs )
			{
				builder.addInputs (i.toProtobuf ());
			}
		}
		if ( outputs != null && outputs.size () > 0 )
		{
			for ( TransactionOutput o : outputs )
			{
				builder.addOutputs (o.toProtobuf ());
			}
		}
		if ( blockHash != null )
		{
			builder.setBlock (ByteString.copyFrom (new Hash (blockHash).toByteArray ()));
		}
		if ( doubleSpend )
		{
			builder.setDoubleSpend (true);
		}
		if ( height != 0 )
		{
			builder.setHeight (height);
		}
		return builder.build ();
	}

	public static Transaction fromProtobuf (BCSAPIMessage.Transaction pt)
	{
		Transaction transaction = new Transaction ();
		transaction.setLockTime (pt.getLocktime ());
		transaction.setVersion (pt.getVersion ());
		if ( pt.getInputsCount () > 0 )
		{
			transaction.setInputs (new ArrayList<TransactionInput> ());
			for ( BCSAPIMessage.TransactionInput i : pt.getInputsList () )
			{
				transaction.getInputs ().add (TransactionInput.fromProtobuf (i));
			}
		}

		if ( pt.getOutputsCount () > 0 )
		{
			transaction.setOutputs (new ArrayList<TransactionOutput> ());
			for ( BCSAPIMessage.TransactionOutput o : pt.getOutputsList () )
			{
				transaction.getOutputs ().add (TransactionOutput.fromProtobuf (o));
			}
		}
		if ( pt.hasBlock () )
		{
			transaction.blockHash = new Hash (pt.getBlock ().toByteArray ()).toString ();
		}
		if ( pt.hasDoubleSpend () && pt.getDoubleSpend () )
		{
			transaction.doubleSpend = true;
		}
		if ( pt.hasHeight () )
		{
			transaction.height = pt.getHeight ();
		}
		return transaction;
	}
}
