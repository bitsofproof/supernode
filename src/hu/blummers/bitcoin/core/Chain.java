package hu.blummers.bitcoin.core;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.purser.server.JpaBlock;
import org.purser.server.JpaTransaction;
import org.purser.server.JpaTransactionInput;
import org.purser.server.JpaTransactionOutput;
import org.spongycastle.util.encoders.Hex;

public class Chain {
	private final JpaBlock genesis;
	private final long magic;
	private final int port;
	private final int addressType;
	private final int privateKeyType;
	private final int difficultyReviewBlocks;
	private final int targetBlockTime;
	private final byte[] alertKey;
	
    public static final byte[] SATOSHI_KEY = Hex.decode("04fc9702847840aaf195de8442ebecedf5b095cdbb9bc716bda9110971b28a49e0ead8564ff0db22209e0374782c093bb899692d524e9d6a6956e7c5ecbcd68284");

	public static final Chain production = new Chain(
			satoshiBlock (),
			0xf9beb4d9L,
			8333,
			0,
			128,
			2015,
			1209600,
			SATOSHI_KEY
			);
	
	private static JpaBlock satoshiBlock ()
	{
		JpaBlock block = new JpaBlock();
		
		block.setChainWork(new byte [1]);
		block.setHeight(0);
		
		block.setVersion(1);
		block.setCreateTime(1231006505L);
		block.setDifficultyTarget(0x1d00ffffL);
		block.setNonce(2083236893);
		block.setPrevious(null);

		List<JpaTransaction> transactions = new ArrayList<JpaTransaction>();
		block.setTransactions(transactions);
		JpaTransaction t = new JpaTransaction();
		transactions.add(t);
		t.setVersion(1);
		
		List<JpaTransactionInput> inputs = new ArrayList<JpaTransactionInput>();
		t.setInputs(inputs);
		JpaTransactionInput input = new JpaTransactionInput();
		inputs.add(input);
		input.setSource(null);
		input.setSequence(0xFFFFFFFFL);
		WireFormat.Writer writer = new WireFormat.Writer(new ByteArrayOutputStream());		
		input.setScript(Hex.decode				 
                	(	"04" + // mimic public key structure
                		"ffff001d" + //  difficulty target 
                		"010445" + // ??
                		// text: "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
                		"5468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73"));						
		
		List<JpaTransactionOutput>outputs = new ArrayList<JpaTransactionOutput>();
		t.setOutputs(outputs);
		JpaTransactionOutput output = new JpaTransactionOutput();
		outputs.add(output);
		output.setValue(new BigInteger("5000000000", 10));
		output.setScript(Hex.decode
                    ("4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac"));
		block.computeHash();
		return block;
	}
	

	public static BigInteger difficultyToNumber(long difficulty) {
		int size = ((int) (difficulty >> 24)) & 0xFF;
		byte[] b = new byte[size+4];
		b[3] = (byte) size;
		if (size >= 1)
			b[4] = (byte) ((difficulty >> 16) & 0xFF);
		if (size >= 2)
			b[5] = (byte) ((difficulty >> 8) & 0xFF);
		if (size >= 3)
			b[6] = (byte) ((difficulty >> 0) & 0xFF);
		WireFormat.Reader reader = new WireFormat.Reader(b);
		int length = (int) reader.readUint32();
		byte[] buf = new byte[length];
		System.arraycopy(b, 4, buf, 0, length);
		return new BigInteger(buf);
	}

	private Chain(JpaBlock genesis, long magic,
			int port, int addressType, int privateKeyType,
			int difficultyReviewBlocks, int targetBlockTime, byte[] alertKey) {
		super();
		this.genesis = genesis;
		this.magic = magic;
		this.port = port;
		this.addressType = addressType;
		this.privateKeyType = privateKeyType;
		this.difficultyReviewBlocks = difficultyReviewBlocks;
		this.targetBlockTime = targetBlockTime;
		this.alertKey = alertKey;
	}

	public JpaBlock getGenesis() {
		return genesis;
	}

	public long getMagic() {
		return magic;
	}

	public int getPort() {
		return port;
	}

	public int getAddressType() {
		return addressType;
	}

	public int getPrivateKeyType() {
		return privateKeyType;
	}

	public int getDifficultyReviewBlocks() {
		return difficultyReviewBlocks;
	}

	public int getTargetBlockTime() {
		return targetBlockTime;
	}

	public byte[] getAlertKey() {
		return alertKey;
	}
}
