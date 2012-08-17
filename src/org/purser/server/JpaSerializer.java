package org.purser.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VarInt;
import com.mysema.query.jpa.impl.JPAQuery;

@Component
public class JpaSerializer {

	@PersistenceContext
	EntityManager entityManager;

	private class Batch {
		private byte[] bytes;
		private int cursor;

		Batch(byte[] bytes) {
			this.bytes = bytes;
			this.cursor = 0;
		}

		long readUint32() {
			long u = Utils.readUint32(bytes, cursor);
			cursor += 4;
			return u;
		}
		
		boolean eof ()
		{
			return cursor >= bytes.length;
		}

		Sha256Hash readHash() {
			byte[] hash = new byte[32];
			System.arraycopy(bytes, cursor, hash, 0, 32);
			// We have to flip it around, as it's been read off the wire in
			// little endian.
			// Not the most efficient way to do this but the clearest.
			hash = Utils.reverseBytes(hash);
			cursor += 32;
			return new Sha256Hash(hash);
		}

		BigInteger readUint64() {
			// Java does not have an unsigned 64 bit type. So scrape it off the
			// wire then flip.
			byte[] valbytes = new byte[8];
			System.arraycopy(bytes, cursor, valbytes, 0, 8);
			valbytes = Utils.reverseBytes(valbytes);
			cursor += valbytes.length;
			return new BigInteger(valbytes);
		}

		long readVarInt() {
			return readVarInt(0);
		}

		long readVarInt(int offset) {
			VarInt varint = new VarInt(bytes, cursor + offset);
			cursor += offset + varint.getSizeInBytes();
			return varint.value;
		}

		byte[] readBytes(int length) {
			byte[] b = new byte[length];
			System.arraycopy(bytes, cursor, b, 0, length);
			cursor += length;
			return b;
		}

		byte[] readByteArray() {
			long len = readVarInt();
			return readBytes((int) len);
		}

		String readStr() {
			VarInt varInt = new VarInt(bytes, cursor);
			if (varInt.value == 0) {
				cursor += 1;
				return "";
			}
			cursor += varInt.getSizeInBytes();
			byte[] characters = new byte[(int) varInt.value];
			System.arraycopy(bytes, cursor, characters, 0, characters.length);
			cursor += characters.length;
			try {
				return new String(characters, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e); // Cannot happen, UTF-8 is always
												// supported.
			}
		}

		String digest(int offset) {
			return new Sha256Hash(Utils.reverseBytes(Utils.doubleDigest(bytes,
					offset, cursor))).toString();
		}
	}

	private JpaBlock findBlock(String hash) {
		QJpaBlock block = QJpaBlock.jpaBlock;
		JPAQuery query = new JPAQuery(entityManager);
		return query.from(block).where(block.hash.eq(hash)).uniqueResult(block);
	}

	private JpaTransaction findSourceTransaction(String hash, long index) {
		QJpaTransaction transaction = QJpaTransaction.jpaTransaction;
		JPAQuery query = new JPAQuery(entityManager);
		return query
				.from(transaction)
				.where(transaction.block.hash.eq(hash).and(
						transaction.ix.eq(index))).uniqueResult(transaction);
	}

	public JpaBlock jpaBlockFromWire(byte[] bytes) {
		Batch batch = new Batch(bytes);
		JpaBlock block = new JpaBlock();

		block.setVersion(batch.readUint32());
		block.setPrevious(findBlock(batch.readHash().toString()));
		block.setMerkleRoot(batch.readHash().getBytes());
		block.setCreateTime(batch.readUint32());
		block.setDifficultyTarget(batch.readUint32());
		block.setNonce(batch.readUint32());
		block.setHash(batch.digest(0));

		if ( batch.eof() )
			return block; // this is actually violaton of protocol, but bitcoinj does it.  
		
		long nt = batch.readVarInt();
		List<JpaTransaction> transactions = new ArrayList<JpaTransaction>();
		block.setTransactions(transactions);

		for (long i = 0; i < nt; ++i) {
			JpaTransaction tx = new JpaTransaction();
			transactions.add(tx);
			
			tx.setIx(i);
			tx.setBlock(block);
			tx.setVersion((int) batch.readUint32());
			long nin = batch.readVarInt();
			List<JpaTransactionInput> inputs = new ArrayList<JpaTransactionInput>();
			tx.setInputs(inputs);
			for (long j = 0; j < nin; ++j) {
				JpaTransactionInput input = new JpaTransactionInput();
				inputs.add(input);
				
				input.setSource(findSourceTransaction(batch.readHash()
						.toString(), batch.readUint32()));
				input.setScript(batch.readByteArray());
				input.setSequence(batch.readUint32());
			}

			long nout = batch.readVarInt();
			List<JpaTransactionOutput> outs = new ArrayList<JpaTransactionOutput>();
			tx.setOutputs(outs);
			for (long j = 0; j < nout; ++j) {
				JpaTransactionOutput output = new JpaTransactionOutput();
				outs.add (output);
				
				output.setValue(batch.readUint64());
				output.setScript(batch.readByteArray());
			}
			tx.setLockTime(batch.readUint32());
		}

		return block;
	}

	public static byte[] hexStringHash(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16)  << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		
		// in place reversal
		for( int i = 0, j = data.length - 1; i < data.length / 2; i++, j-- ) {
			 
			data[ i ] ^= data[ j ];
			data[ j ] ^= data[ i ];
			data[ i ] ^= data[ j ];
	    }
		return data;
	}	
	
	public byte[] jpaBlockToWire(JpaBlock block) {
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		try {
			Utils.uint32ToByteStreamLE(block.getVersion(), bs);
			if ( block.getPrevious() == null )
				bs.write(new byte [32]);
			else
				bs.write(hexStringHash(block.getPrevious().getHash()));
			bs.write(block.getMerkleRoot());
			Utils.uint32ToByteStreamLE(block.getCreateTime(), bs);
			Utils.uint32ToByteStreamLE(block.getDifficultyTarget(), bs);
			Utils.uint32ToByteStreamLE(block.getNonce(), bs);
			if ( block.getTransactions() != null && !block.getTransactions().isEmpty() )
			{
				bs.write(new VarInt (block.getTransactions().size()).encode());
				for ( JpaTransaction tx : block.getTransactions() )
				{
					Utils.uint32ToByteStreamLE(tx.getVersion(), bs);
					if ( tx.getInputs () != null && !tx.getInputs ().isEmpty() )
					{
						bs.write(new VarInt (tx.getInputs().size()).encode());
						for ( JpaTransactionInput in : tx.getInputs() )
						{
							if ( in.getSource() != null )
							{
								bs.write(hexStringHash(in.getSource().getBlock().getHash()));
								Utils.uint32ToByteStreamLE(in.getSource().getIx(), bs);
							}
							else
							{
								bs.write(Sha256Hash.ZERO_HASH.getBytes());
								Utils.uint32ToByteStreamLE(-1, bs); //TODO: not sure of this... check hash!
							}
							bs.write(new VarInt (in.getScript().length).encode());
							bs.write(in.getScript());
							Utils.uint32ToByteStreamLE(in.getSequence(), bs);
						}
					}
					else
					{
						bs.write(new VarInt(0).encode());
					}
					if ( tx.getOutputs() != null && !tx.getOutputs ().isEmpty())
					{
						bs.write(new VarInt (tx.getOutputs().size()).encode());
						for ( JpaTransactionOutput out : tx.getOutputs() )
						{
							Utils.uint64ToByteStreamLE(out.getValue(), bs);
							bs.write(new VarInt (out.getScript().length).encode());
							bs.write(out.getScript());
						}
					}
					else
					{
						bs.write(new VarInt(0).encode()); // miner's dream...
					}
					Utils.uint32ToByteStreamLE(tx.getLockTime(), bs);
				}				
			}
			else
			{
				bs.write(new VarInt(0).encode());
			}
		} catch (IOException e) {
			// can not happen
		}
		return bs.toByteArray();
	}
}
