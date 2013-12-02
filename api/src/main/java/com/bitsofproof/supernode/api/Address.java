package com.bitsofproof.supernode.api;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.Hash;
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
			return toSatoshiStyle (this);
		}
		catch ( ValidationException e )
		{
			return network.name () + ":" + type.name () + ":" + ByteUtils.toHex (bytes);
		}
	}

	public static Address fromSatoshiStyle (String s) throws ValidationException
	{
		try
		{
			Network network = Network.PRODUCTION;
			Address.Type type = Type.COMMON;
			byte[] raw = ByteUtils.fromBase58 (s);
			if ( (raw[0] & 0xff) == 0x0 )
			{
				network = Network.PRODUCTION;
				type = Address.Type.COMMON;
			}
			if ( (raw[0] & 0xff) == 5 )
			{
				network = Network.PRODUCTION;
				type = Address.Type.P2SH;
			}
			if ( (raw[0] & 0xff) == 0x6f )
			{
				network = Network.TEST;
				type = Address.Type.COMMON;
			}
			if ( (raw[0] & 0xff) == 196 )
			{
				network = Network.TEST;
				type = Address.Type.P2SH;
			}
			byte[] check = Hash.hash (raw, 0, raw.length - 4);
			for ( int i = 0; i < 4; ++i )
			{
				if ( check[i] != raw[raw.length - 4 + i] )
				{
					throw new ValidationException ("Address checksum mismatch");
				}
			}
			byte[] keyDigest = new byte[raw.length - 5];
			System.arraycopy (raw, 1, keyDigest, 0, raw.length - 5);
			return new Address (network, type, keyDigest);
		}
		catch ( Exception e )
		{
			throw new ValidationException (e);
		}
	}

	public static byte[] fromSatoshiStyle (String s, int addressFlag) throws ValidationException
	{
		try
		{
			byte[] raw = ByteUtils.fromBase58 (s);
			if ( raw[0] != (byte) (addressFlag & 0xff) )
			{
				throw new ValidationException ("invalid address for this chain");
			}
			byte[] check = Hash.hash (raw, 0, raw.length - 4);
			for ( int i = 0; i < 4; ++i )
			{
				if ( check[i] != raw[raw.length - 4 + i] )
				{
					throw new ValidationException ("Address checksum mismatch");
				}
			}
			byte[] keyDigest = new byte[raw.length - 5];
			System.arraycopy (raw, 1, keyDigest, 0, raw.length - 5);
			return keyDigest;
		}
		catch ( Exception e )
		{
			throw new ValidationException (e);
		}
	}

	public static String toSatoshiStyle (byte[] keyDigest, int addressFlag)
	{
		byte[] addressBytes = new byte[1 + keyDigest.length + 4];
		addressBytes[0] = (byte) (addressFlag & 0xff);
		System.arraycopy (keyDigest, 0, addressBytes, 1, keyDigest.length);
		byte[] check = Hash.hash (addressBytes, 0, keyDigest.length + 1);
		System.arraycopy (check, 0, addressBytes, keyDigest.length + 1, 4);
		return ByteUtils.toBase58 (addressBytes);
	}

	public static String toSatoshiStyle (Address address) throws ValidationException
	{
		byte[] keyDigest = address.toByteArray ();
		int addressFlag;
		if ( address.getNetwork () == Network.PRODUCTION )
		{
			if ( address.getType () == Address.Type.COMMON )
			{
				addressFlag = 0x0;
			}
			else if ( address.getType () == Address.Type.P2SH )
			{
				addressFlag = 0x5;
			}
			else
			{
				throw new ValidationException ("unknown address type");
			}
		}
		else if ( address.getNetwork () == Network.TEST )
		{
			if ( address.getType () == Address.Type.COMMON )
			{
				addressFlag = 0x6f;
			}
			else if ( address.getType () == Address.Type.P2SH )
			{
				addressFlag = 196;
			}
			else
			{
				throw new ValidationException ("unknown address type");
			}
		}
		else
		{
			throw new ValidationException ("unknown network");
		}
		byte[] addressBytes = new byte[1 + keyDigest.length + 4];
		addressBytes[0] = (byte) (addressFlag & 0xff);
		System.arraycopy (keyDigest, 0, addressBytes, 1, keyDigest.length);
		byte[] check = Hash.hash (addressBytes, 0, keyDigest.length + 1);
		System.arraycopy (check, 0, addressBytes, keyDigest.length + 1, 4);
		return ByteUtils.toBase58 (addressBytes);
	}
}
