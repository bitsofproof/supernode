package com.bitsofproof.supernode.wallet;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Opcode;
import com.bitsofproof.supernode.common.ValidationException;

public class Address
{
	public enum Type
	{
		UNKNOWN, COMMON, P2SH
	};

	public enum Network
	{
		UNKNOWN, PRODUCTION, TEST
	}

	private final Network network;
	private final Type type;
	private final byte[] address;

	public Address (Network network, Type type, byte[] address) throws ValidationException
	{
		this.type = type;
		if ( address.length != 20 )
		{
			throw new ValidationException ("invalid digest length for an address");
		}
		this.address = Arrays.clone (address);
		this.network = network;
	}

	public Network getNetwork ()
	{
		return network;
	}

	public Type getType ()
	{
		return type;
	}

	public byte[] getAddress ()
	{
		return Arrays.clone (address);
	}

	public byte[] getAddressScript () throws ValidationException
	{
		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		if ( type == Address.Type.COMMON )
		{
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_DUP));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
			writer.writeData (address);
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUALVERIFY));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_CHECKSIG));
		}
		else if ( type == Address.Type.P2SH )
		{
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
			writer.writeData (address);
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUAL));
		}
		else
		{
			throw new ValidationException ("unknown sink address type");
		}
		return writer.toByteArray ();
	}

	@Override
	public String toString ()
	{
		try
		{
			return AddressConverter.toSatoshiStyle (this);
		}
		catch ( ValidationException e )
		{
			return e.getMessage ();
		}
	}
}
