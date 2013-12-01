package com.bitsofproof.supernode.wallet;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.api.Network;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Opcode;
import com.bitsofproof.supernode.common.ValidationException;

public class Address
{
	public enum Type
	{
		COMMON, P2SH
	};

	private final Type type;
	private final byte[] bytes;

	private Network network = Network.PRODUCTION;

	public Network getNetwork ()
	{
		return network;
	}

	public void setNetwork (Network network)
	{
		this.network = network;
	}

	public Address (Network network, Type type, byte[] address) throws ValidationException
	{
		this.network = network;
		this.type = type;
		if ( address.length != 20 )
		{
			throw new ValidationException ("invalid digest length for an address");
		}
		this.bytes = Arrays.clone (address);
	}

	public Address (Type type, byte[] address) throws ValidationException
	{
		this.type = type;
		if ( address.length != 20 )
		{
			throw new ValidationException ("invalid digest length for an address");
		}
		this.bytes = Arrays.clone (address);
	}

	public Address (Network network, Address address) throws ValidationException
	{
		this.network = network;
		this.type = address.type;
		this.bytes = Arrays.clone (address.bytes);
	}

	public Type getType ()
	{
		return type;
	}

	public byte[] toByteArray ()
	{
		return Arrays.clone (bytes);
	}

	public byte[] getAddressScript () throws ValidationException
	{
		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		if ( type == Address.Type.COMMON )
		{
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_DUP));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
			writer.writeData (bytes);
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUALVERIFY));
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_CHECKSIG));
		}
		else if ( type == Address.Type.P2SH )
		{
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
			writer.writeData (bytes);
			writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUAL));
		}
		else
		{
			throw new ValidationException ("unknown sink address type");
		}
		return writer.toByteArray ();
	}

	@Override
	public int hashCode ()
	{
		return Arrays.hashCode (bytes) + type.ordinal ();
	}

	@Override
	public boolean equals (Object obj)
	{
		if ( this == obj )
		{
			return true;
		}
		if ( obj == null || getClass () != obj.getClass () )
		{
			return false;
		}
		return Arrays.areEqual (bytes, ((Address) obj).bytes) && type == ((Address) obj).type;
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
			return network.name () + ":" + type.name () + ":" + ByteUtils.toHex (bytes);
		}
	}
}
