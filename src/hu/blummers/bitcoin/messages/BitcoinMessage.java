package hu.blummers.bitcoin.messages;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


import edu.emory.mathcs.backport.java.util.Arrays;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ValidationException;
import hu.blummers.bitcoin.core.WireFormat;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;
import hu.blummers.p2p.P2P;

public class BitcoinMessage implements P2P.Message {
	private final String command;
	private final Chain chain;
	
    public static final int MAX_SIZE = 0x02000000;
	
	public BitcoinMessage (Chain chain, String command)
	{
		this.command = command;
		this.chain = chain;
	}
	
	public String getCommand () {
		return command;
	}
	@Override
	public byte [] toByteArray ()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		WireFormat.Writer writer = new WireFormat.Writer(out);
		writer.writeUint32(chain.getMagic());
		writer.writeZeroDelimitedString(getCommand(), 12);
		WireFormat.Writer payload = new WireFormat.Writer(new ByteArrayOutputStream());
		toWire(payload);
		byte[] data = payload.toByteArray();
		writer.writeUint32(data.length);

		byte[] checksum = new byte[4];
		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-256");
			System.arraycopy(sha.digest(sha.digest(data)), 0, checksum, 0, 4);
		} catch (NoSuchAlgorithmException e) {
		}
		writer.writeBytes(checksum);

		writer.writeBytes(data);
		return writer.toByteArray();
	}
	
	public static BitcoinMessage fromStream (InputStream readIn, Chain chain, long version) throws ValidationException, IOException
	{
		byte[] head = new byte[24];
		if (readIn.read(head) != head.length)
			throw new ValidationException("Invalid package header");
		WireFormat.Reader reader = new WireFormat.Reader(head);
		long mag = reader.readUint32();
		if (mag != chain.getMagic())
			throw new ValidationException("Wrong magic for this chain" + mag + " vs " + chain.getMagic());

		String command = reader.readZeroDelimitedString(12);
		BitcoinMessage m = MessageFactory.createMessage(chain, command);
		long length = reader.readUint32();
		byte[] checksum = reader.readBytes(4);
		if (length > 0 && length < MAX_SIZE) {
			byte[] buf = new byte[(int) length];
			if (readIn.read(buf) != buf.length)
				throw new ValidationException("Package length mismatch");
			byte[] cs = new byte[4];
			MessageDigest sha;
			try {
				sha = MessageDigest.getInstance("SHA-256");
				System.arraycopy(sha.digest(sha.digest(buf)), 0, cs, 0, 4);
			} catch (NoSuchAlgorithmException e) {
			}
			if (!Arrays.equals(cs, checksum))
				throw new ValidationException("Checksum mismatch");

			if (m != null) {
				m.fromWire(new WireFormat.Reader(buf), version);
			}
		}
		return m;
	}

	

	public Chain getChain() {
		return chain;
	}

	public void validate () throws ValidationException {}
	public void toWire (WireFormat.Writer writer) {}
	public void fromWire (WireFormat.Reader reader, long version) {}
}
