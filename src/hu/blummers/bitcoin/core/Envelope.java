package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.purser.server.ValidationException;


import edu.emory.mathcs.backport.java.util.Arrays;

public class Envelope
{
	public static Message read (InputStream in, Chain chain) throws IOException, ValidationException
	{
		byte [] magic = new byte [4];
		
		if ( in.read(magic) != 4 )
			return null;
		
		WireFormat.Reader reader = new WireFormat.Reader(magic);
		if ( reader.readUint32() != chain.getMagic() )
			throw new ValidationException ("Wrong magic for this chain");
		
		byte [] head = new byte [20];
		if ( in.read(head) != 20 )
			return null;
		reader = new WireFormat.Reader(head);
		String command = reader.readZeroDelimitedString(12);
		long length = reader.readUint32();
		byte [] checksum = reader.readBytes(4);
		byte [] data = new byte [(int)length];
		
		if ( in.read(data) != data.length )
			throw new ValidationException ("Message length mismatch");
		
		if ( !Arrays.equals(calculateCheckSum (data), checksum) )
			throw new ValidationException ("Message checksum failed");

		return getMessage (chain, command, new WireFormat.Reader(data));
	}
	
	public static void write (OutputStream out, Chain chain, Message m)
	{
		WireFormat.Writer writer = new WireFormat.Writer(new ByteArrayOutputStream ());
		writer.writeUint32(chain.getMagic());
		writer.writeZeroDelimitedString(m.getCommand(), 12);
		WireFormat.Writer payload = new WireFormat.Writer(new ByteArrayOutputStream ());
		m.toWire(payload);
		byte [] data = payload.toByteArray();
		writer.writeUint32(data.length);
		writer.writeBytes(calculateCheckSum(data));
		writer.writeBytes(data);
	}

	private static byte [] calculateCheckSum (byte [] data)
	{
		byte [] c = new byte [4];
		System.arraycopy(new Hash ().digest(data).toByteArray(), 0, c, 0, 4);
		return c;
	}
	
	private static Message getMessage (Chain chain, String command, WireFormat.Reader reader)
	{
		Message m = null;
		if ( command.equals("version") )
			m = new VersionMessage ();
		
		if ( m.getCommand().equals(command) )
			throw new RuntimeException ("Envelope failed to create the right message");
		
		m.fromWire(reader);
		return m;
	}
	
	/*
	 *         if (command.equals("version")) {
            return new VersionMessage(params, payloadBytes);
        } else if (command.equals("inv")) {
            message = new InventoryMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("block")) {
            message = new Block(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("getdata")) {
            message = new GetDataMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("tx")) {
            Transaction tx = new Transaction(params, payloadBytes, null, parseLazy, parseRetain, length);
            if (hash != null)
                tx.setHash(new Sha256Hash(Utils.reverseBytes(hash)));
            message = tx;
        } else if (command.equals("addr")) {
            message = new AddressMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("ping")) {
            return new Ping();
        } else if (command.equals("verack")) {
            return new VersionAck(params, payloadBytes);
        } else if (command.equals("headers")) {
            return new HeadersMessage(params, payloadBytes);
        } else if (command.equals("alert")) {
            return new AlertMessage(params, payloadBytes);
        } else {
            log.warn("No support for deserializing message with name {}", command);
            return new UnknownMessage(params, command, payloadBytes);
        }

			*/
}
