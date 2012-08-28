package org.purser.server;

import hu.blummers.bitcoin.core.Hash;
import hu.blummers.bitcoin.core.WireFormat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mysema.query.jpa.impl.JPAQuery;

@Component
public class JpaSerializer {
	private static final Logger log = LoggerFactory
			.getLogger(JpaSerializer.class);

	@PersistenceContext
	EntityManager entityManager;

	private JpaBlock findBlock(String hash) {
		QJpaBlock block = QJpaBlock.jpaBlock;
		JPAQuery query = new JPAQuery(entityManager);
		return query.from(block).where(block.hash.eq(hash)).uniqueResult(block);
	}
/*
	private JpaTransaction findSourceTransaction(String hash, long index) {
		QJpaTransaction transaction = QJpaTransaction.jpaTransaction;
		JPAQuery query = new JPAQuery(entityManager);
		return query
				.from(transaction)
				.where(transaction.block.hash.eq(hash).and(
						transaction.ix.eq(index))).uniqueResult(transaction);
	}
*/
	public JpaBlock jpaBlockFromWire(byte[] bytes) {
		
		WireFormat.Reader batch = new WireFormat.Reader(bytes);
		JpaBlock block = new JpaBlock();

		block.fromWire(batch, entityManager);
		/*
		
		block.setVersion(batch.readUint32());
		block.setPrevious(findBlock(batch.readHash().toString()));
		block.setMerkleRoot(batch.readBytes(32)); // this is the right
													// direction.
		block.setCreateTime(batch.readUint32());
		block.setDifficultyTarget(batch.readUint32());
		block.setNonce(batch.readUint32());
		block.setHash(batch.hash(0, 80).toString());

		if (batch.eof())
			return block; // this is actually violaton of protocol, but bitcoinj
							// does it.

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
				input.setSignature(batch.readVarBytes());
				input.setSequence(batch.readUint32());
			}

			long nout = batch.readVarInt();
			List<JpaTransactionOutput> outs = new ArrayList<JpaTransactionOutput>();
			tx.setOutputs(outs);
			for (long j = 0; j < nout; ++j) {
				JpaTransactionOutput output = new JpaTransactionOutput();
				outs.add(output);

				output.setValue(batch.readUint64());
				output.setScript(batch.readVarBytes());
			}
			tx.setLockTime(batch.readUint32());
		}
*/
		return block;
	}

	public static byte[] hexStringHash(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}

		// in place reversal
		for (int i = 0, j = data.length - 1; i < data.length / 2; i++, j--) {

			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}

	public byte[] jpaBlockToWire(JpaBlock block) {
		WireFormat.Writer writer = new WireFormat.Writer(
				new ByteArrayOutputStream());
		
		block.toWire(writer);
		
		/*
		writer.writeUint32(block.getVersion());
		if (block.getPrevious() == null)
			writer.writeBytes(new byte[32]);
		else
			writer.writeHash(new Hash(block.getPrevious().getHash()));
		writer.writeBytes(block.getMerkleRoot());
		writer.writeUint32(block.getCreateTime());
		writer.writeUint32(block.getDifficultyTarget());
		writer.writeUint32(block.getNonce());
		if (block.getTransactions() != null
				&& !block.getTransactions().isEmpty()) {
			writer.writeUint32(1);
			writer.writeVarInt(block.getTransactions().size());
			for (JpaTransaction tx : block.getTransactions()) {
				writer.writeUint32(tx.getVersion());
				if (tx.getInputs() != null && !tx.getInputs().isEmpty()) {
					writer.writeVarInt(tx.getInputs().size());
					for (JpaTransactionInput in : tx.getInputs()) {
						if (in.getSource() != null) {
							writer.writeHash(new Hash(in.getSource().getBlock()
									.getHash()));
							writer.writeUint32(in.getSource().getIx());
						} else {
							writer.writeBytes(Hash.ZERO_HASH.toByteArray());
							writer.writeUint32(-1); // TODO: not sure of this...
													// check hash!
						}
						writer.writeVarBytes(in.getSignature());
						writer.writeUint32(in.getSequence());
					}
				} else {
					writer.writeVarInt(0);
				}
				if (tx.getOutputs() != null && !tx.getOutputs().isEmpty()) {
					writer.writeVarInt(tx.getOutputs().size());
					for (JpaTransactionOutput out : tx.getOutputs()) {
						writer.writeUint64(out.getValue());
						writer.writeVarBytes(out.getScript());
					}
				} else {
					writer.writeVarInt(0); // miner's dream...
				}
				writer.writeUint32(tx.getLockTime());
			}
		} else {
			writer.writeVarInt(0);
		}
		*/
		/*
		 * // check hash byte [] bytes = bs.toByteArray(); Batch batch = new
		 * Batch (bytes); if ( !batch.digest(0, 80).equals(block.getHash()) ) {
		 * log.error(" wire " + batch.digest(0, 80) + " vs JPA " +
		 * block.getHash()); }
		 */
		
		return writer.toByteArray();
	}
}
