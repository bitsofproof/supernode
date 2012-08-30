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

public class BlockFactory {
    public static final long NO_SEQUENCE = 0xFFFFFFFFL;
    
	public static JpaBlock getGenesisBlock ()
	{
		JpaBlock block = new JpaBlock();
		
		block.setChainWork(new byte [1]);
		block.setHeight(0);
		
		block.setCreateTime(1231006505L);
		block.setDifficultyTarget(0x1d00ffffL);
		block.setNonce(2083236893);
		block.setPrevious(null);

		List<JpaTransaction> transactions = new ArrayList<JpaTransaction>();
		block.setTransactions(transactions);
		JpaTransaction t = new JpaTransaction();		
		t.setVersion(1);
		
		List<JpaTransactionInput> inputs = new ArrayList<JpaTransactionInput>();
		t.setInputs(inputs);
		JpaTransactionInput input = new JpaTransactionInput();
		inputs.add(input);
		input.setSource(null);
		input.setSequence(NO_SEQUENCE);
		WireFormat.Writer writer = new WireFormat.Writer(new ByteArrayOutputStream());		
		writer.writeVarBytes(Hex.decode				 
                	(	"04" + // length of difficulty ?
                		"ffff001d" + //  difficulty target 
                		"01" + // length of ?
                		"04" + // ?
                		"45" + // text length
                		// text: "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
                		"5468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73"));						
		input.setScript(writer.toByteArray());
		
		List<JpaTransactionOutput>outputs = new ArrayList<JpaTransactionOutput>();
		t.setOutputs(outputs);
		JpaTransactionOutput output = new JpaTransactionOutput();
		outputs.add(output);
		output.setValue(new BigInteger("5000000000", 10));
		output.setScript(Hex.decode
                    ("04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f"));
		
		return block;
	}
}
