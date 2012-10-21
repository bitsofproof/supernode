package com.bitsofproof.supernode.core;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.util.encoders.Hex;

import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;

public class Script
{
	private final Stack<byte[]> stack = new Stack<byte[]> ();
	private final Stack<byte[]> alt = new Stack<byte[]> ();
	private final byte[] script;
	private Tx tx;
	private int inr;

	public enum Opcode
	{
		OP_FALSE (0), OP_PUSH1 (1), OP_PUSH2 (2), OP_PUSH3 (3), OP_PUSH4 (4), OP_PUSH5 (5), OP_PUSH6 (6), OP_PUSH7 (7), OP_PUSH8 (8), OP_PUSH9 (9), OP_PUSH10 (
				10), OP_PUSH11 (11), OP_PUSH12 (12), OP_PUSH13 (13), OP_PUSH14 (14), OP_PUSH15 (15), OP_PUSH16 (16), OP_PUSH17 (17), OP_PUSH18 (18), OP_PUSH19 (
				19), OP_PUSH20 (20), OP_PUSH21 (21), OP_PUSH22 (22), OP_PUSH23 (23), OP_PUSH24 (24), OP_PUSH25 (25), OP_PUSH26 (26), OP_PUSH27 (27), OP_PUSH28 (
				28), OP_PUSH29 (29), OP_PUSH30 (30), OP_PUSH31 (31), OP_PUSH32 (32), OP_PUSH33 (33), OP_PUSH34 (34), OP_PUSH35 (35), OP_PUSH36 (36), OP_PUSH37 (
				37), OP_PUSH38 (38), OP_PUSH39 (39), OP_PUSH40 (40), OP_PUSH41 (41), OP_PUSH42 (42), OP_PUSH43 (43), OP_PUSH44 (44), OP_PUSH45 (45), OP_PUSH46 (
				46), OP_PUSH47 (47), OP_PUSH48 (48), OP_PUSH49 (49), OP_PUSH50 (50), OP_PUSH51 (51), OP_PUSH52 (52), OP_PUSH53 (53), OP_PUSH54 (54), OP_PUSH55 (
				55), OP_PUSH56 (56), OP_PUSH57 (57), OP_PUSH58 (58), OP_PUSH59 (59), OP_PUSH60 (60), OP_PUSH61 (61), OP_PUSH62 (62), OP_PUSH63 (63), OP_PUSH64 (
				64), OP_PUSH65 (65), OP_PUSH66 (66), OP_PUSH67 (67), OP_PUSH68 (68), OP_PUSH69 (69), OP_PUSH70 (70), OP_PUSH71 (71), OP_PUSH72 (72), OP_PUSH73 (
				73), OP_PUSH74 (74), OP_PUSH75 (75),

		OP_PUSHDATA1 (76), OP_PUSHDATA2 (77), OP_PUSHDATA4 (78), OP_1NEGATE (79),

		OP_RESERVED (80),

		OP_1 (81), OP_2 (82), OP_3 (83), OP_4 (84), OP_5 (85), OP_6 (86), OP_7 (87), OP_8 (88), OP_9 (89), OP_10 (90), OP_11 (91), OP_12 (92), OP_13 (93),
		OP_14 (94), OP_15 (95), OP_16 (96),

		OP_NOP (97), OP_VER (98), OP_IF (99), OP_NOTIF (100), OP_VERIF (101), OP_VERNOTIF (102),

		OP_ELSE (103), OP_ENDIF (104), OP_VERIFY (105), OP_RETURN (106),

		OP_TOALTSTACK (107), OP_FROMALTSTACK (108), OP_2DROP (109), OP_2DUP (110), OP_3DUP (111), OP_2OVER (112), OP_2ROT (113), OP_2SWAP (114),
		OP_IFDUP (115), OP_DEPTH (116), OP_DROP (117), OP_DUP (118), OP_NIP (119), OP_OVER (120), OP_PICK (121), OP_ROLL (122), OP_ROT (123), OP_SWAP (124),
		OP_TUCK (125),

		OP_CAT (126), OP_SUBSTR (127), OP_LEFT (128), OP_RIGHT (129), OP_SIZE (130), OP_INVERT (131), OP_AND (132), OP_OR (133), OP_XOR (134),

		OP_EQUAL (135), OP_EQUALVERIFY (136),

		OP_RESERVED1 (137), OP_RESERVED2 (138),

		OP_1ADD (139), // 0x8b in out 1 is added to the input.
		OP_1SUB (140), // 0x8c in out 1 is subtracted from the input.
		OP_2MUL (141), // 0x8d in out The input is multiplied by 2. Currently
						// disabled.
		OP_2DIV (142), // 0x8e in out The input is divided by 2. Currently
						// disabled.
		OP_NEGATE (143), // 0x8f in out The sign of the input is flipped.
		OP_ABS (144), // 0x90 in out The input is made positive.
		OP_NOT (145), // 0x91 in out If the input is 0 or 1, it is flipped.
						// Otherwise the output will be 0.
		OP_0NOTEQUAL (146), // 0x92 in out Returns 0 if the input is 0. 1
							// otherwise.
		OP_ADD (147), // 0x93 a b out a is added to b.
		OP_SUB (148), // 0x94 a b out b is subtracted from a.
		OP_MUL (149), // 0x95 a b out a is multiplied by b. Currently disabled.
		OP_DIV (150), // 0x96 a b out a is divided by b. Currently disabled.
		OP_MOD (151), // 0x97 a b out Returns the remainder after dividing a by
						// b. Currently disabled.
		OP_LSHIFT (152), // 0x98 a b out Shifts a left b bits, preserving sign.
							// Currently disabled.
		OP_RSHIFT (153), // 0x99 a b out Shifts a right b bits, preserving sign.
							// Currently disabled.
		OP_BOOLAND (154), // 0x9a a b out If both a and b are not 0, the output
							// is 1. Otherwise 0.
		OP_BOOLOR (155), // 0x9b a b out If a or b is not 0, the output is 1.
							// Otherwise 0.
		OP_NUMEQUAL (156), // 0x9c a b out Returns 1 if the numbers are equal, 0
							// otherwise.
		OP_NUMEQUALVERIFY (157), // 0x9d a b out Same as OP_NUMEQUAL, but runs
									// OP_VERIFY afterward.
		OP_NUMNOTEQUAL (158), // 0x9e a b out Returns 1 if the numbers are not
								// equal, 0 otherwise.
		OP_LESSTHAN (159), // 0x9f a b out Returns 1 if a is less than b, 0
							// otherwise.
		OP_GREATERTHAN (160), // 0xa0 a b out Returns 1 if a is greater than b,
								// 0
								// otherwise.
		OP_LESSTHANOREQUAL (161), // 0xa1 a b out Returns 1 if a is less than or
									// equal to b, 0 otherwise.
		OP_GREATERTHANOREQUAL (162), // 0xa2 a b out Returns 1 if a is greater
										// than or equal to b, 0 otherwise.
		OP_MIN (163), // 0xa3 a b out Returns the smaller of a and b.
		OP_MAX (164), // 0xa4 a b out Returns the larger of a and b.
		OP_WITHIN (165), // 0xa5 x min max out Returns 1 if x is within the
							// specified range (left-inclusive), 0 otherwise.

		OP_RIPEMD160 (166), // 0xa6 in hash The input is hashed using
							// RIPEMD-160.
		OP_SHA1 (167), // 0xa7 in hash The input is hashed using SHA-1.
		OP_SHA256 (168), // 0xa8 in hash The input is hashed using SHA-256.
		OP_HASH160 (169), // 0xa9 in hash The input is hashed twice: first with
							// SHA-256 and then with RIPEMD-160.
		OP_HASH256 (170), // 0xaa in hash The input is hashed two times with
							// SHA-256.
		OP_CODESEPARATOR (171), // 0xab Nothing Nothing All of the signature
								// checking words will only match signatures to
								// the data after the most recently-executed
								// OP_CODESEPARATOR.
		OP_CHECKSIG (172), // 0xac sig pubkey True / false The entire
							// transaction's outputs, inputs, and script (from
							// the most recently-executed OP_CODESEPARATOR to
							// the end) are hashed. The signature used by
							// OP_CHECKSIG must be a valid signature for this
							// hash and public key. If it is, 1 is returned, 0
							// otherwise.
		OP_CHECKSIGVERIFY (173), // 0xad sig pubkey True / false Same as
									// OP_CHECKSIG, but OP_VERIFY is executed
									// afterward.
		OP_CHECKMULTISIG (174), // 0xae x sig1 sig2 ... <number of signatures>
								// pub1 pub2 <number of public keys> True /
								// False For each signature and public key pair,
								// OP_CHECKSIG is executed. If more public keys
								// than signatures are listed, some key/sig
								// pairs can fail. All signatures need to match
								// a public key. If all signatures are valid, 1
								// is returned, 0 otherwise. Due to a bug, one
								// extra unused value is removed from the stack.
		OP_CHECKMULTISIGVERIFY (175);// 0xaf x sig1 sig2 ... <number of
										// signatures> pub1 pub2 ... <number of
										// public keys> True / False Same as
										// OP_CHECKMULTISIG, but OP_VERIFY is
										// executed afterward.

		private final int o;

		Opcode (int n)
		{
			this.o = n;
		}
	};

	private void pushInt (long n)
	{
		if ( n > 0xffffffffL )
		{
			throw new IllegalArgumentException ("Number overflow in script");
		}
		pushNumber (new BigInteger (String.valueOf (n)));
	}

	private long popInt ()
	{
		if ( stack.peek ().length > 4 )
		{
			throw new IllegalArgumentException ("Number overflow in script");
		}
		return popNumber ().longValue ();
	}

	private BigInteger toNumber (byte[] b)
	{
		boolean negative = false;
		reverse (b);
		if ( (b[0] & 0x80) != 0 )
		{
			b[0] &= (byte) 0x7f;
		}
		BigInteger bi = new BigInteger (b);
		if ( negative )
		{
			bi = bi.negate ();
		}
		return bi;
	}

	private BigInteger popNumber ()
	{
		byte[] b = stack.pop ();
		return toNumber (b);
	}

	private void pushNumber (BigInteger n)
	{
		boolean negative = false;
		if ( n.compareTo (BigInteger.ZERO) < 0 )
		{
			negative = true;
			n = n.negate ();
		}
		if ( n.compareTo (BigInteger.ZERO) == 0 )
		{
			stack.push (new byte[0]);
		}
		else
		{
			byte[] b = n.toByteArray ();
			reverse (b);
			if ( negative )
			{
				if ( (b[b.length - 1] & 0x80) != 0 )
				{
					byte[] l = new byte[b.length + 1];
					System.arraycopy (b, 0, l, 0, b.length);
					b = l;
				}
				b[b.length - 1] |= (byte) 0x80;
			}
			stack.push (b);
		}
	}

	public boolean isTrue (byte[] b)
	{
		return b.length > 0 && !toNumber (b).equals (BigInteger.ZERO);
	}

	public boolean popBoolean ()
	{
		return isTrue (stack.pop ());
	}

	public boolean peekBoolean ()
	{
		return isTrue (stack.peek ());
	}

	// in place reverse using XOR
	private static byte[] reverse (byte[] data)
	{
		for ( int i = 0, j = data.length - 1; i < data.length / 2; i++, j-- )
		{
			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}

	public Script (byte[] script)
	{
		this.script = script;
		this.tx = null;
	}

	public Script (Tx tx, int inr)
	{
		byte[] sign = tx.getInputs ().get (inr).getScript ();
		byte[] out = tx.getInputs ().get (inr).getSource ().getScript ();
		script = new byte[sign.length + out.length];
		System.arraycopy (sign, 0, script, 0, sign.length);
		System.arraycopy (out, 0, script, sign.length, out.length);
		this.tx = tx;
		this.inr = inr;
	}

	public Script (String s)
	{
		WireFormat.Writer writer = new WireFormat.Writer (new ByteArrayOutputStream ());
		StringTokenizer tokenizer = new StringTokenizer (s, " ");
		while ( tokenizer.hasMoreElements () )
		{
			String token = tokenizer.nextToken ();
			Opcode op = Opcode.valueOf (token);
			if ( op.o <= 75 && op.o > 0 )
			{
				writer.writeByte (op.o);
				writer.writeBytes (Hex.decode (tokenizer.nextToken ()));
			}
			else
			{
				switch ( op )
				{
					case OP_PUSHDATA1:
					{
						byte[] b = Hex.decode (tokenizer.nextToken ());
						writer.writeByte (b[0]);
						writer.writeBytes (Hex.decode (tokenizer.nextToken ()));
					}
						break;
					case OP_PUSHDATA2:
					{
						byte[] b = Hex.decode (tokenizer.nextToken ());
						writer.writeScriptInt16 (b[0] << 8 | b[1]);
						writer.writeBytes (Hex.decode (tokenizer.nextToken ()));
					}
						break;
					case OP_PUSHDATA4:
					{
						byte[] b = Hex.decode (tokenizer.nextToken ());
						writer.writeScriptInt32 (b[3] << 24 | b[2] << 16 | b[1] << 8 | b[0]);
						writer.writeBytes (Hex.decode (tokenizer.nextToken ()));
					}
						break;
					default:
						writer.writeByte (op.o);
				}
			}
		}
		script = writer.toByteArray ();
	}

	@Override
	public String toString ()
	{
		StringBuffer b = new StringBuffer ();
		WireFormat.Reader reader = new WireFormat.Reader (script);
		while ( !reader.eof () )
		{
			Opcode op = Opcode.values ()[reader.readScriptOpcode ()];
			if ( op.o <= 75 )
			{
				b.append ("OP_PUSH" + op.o);
				if ( op.o > 0 )
				{
					b.append (" ");
					try
					{
						b.append (new String (Hex.encode (reader.readBytes (op.o)), "US-ASCII"));
						b.append (" ");
					}
					catch ( UnsupportedEncodingException e )
					{
					}
				}
			}
			else
			{
				switch ( op )
				{
					case OP_PUSHDATA1:
					{
						int n = reader.readScriptOpcode ();
						b.append ("OP_PUSHDATA1 ");
						b.append (" ");
						try
						{
							b.append (new String (Hex.encode (reader.readBytes (n)), "US-ASCII"));
							b.append (" ");
						}
						catch ( UnsupportedEncodingException e )
						{
						}
					}
						break;
					case OP_PUSHDATA2:
					{
						b.append ("OP_PUSHDATA2 ");
						b.append (" ");
						long n = reader.readScriptInt16 ();
						try
						{
							b.append (new String (Hex.encode (reader.readBytes ((int) n)), "US-ASCII"));
							b.append (" ");
						}
						catch ( UnsupportedEncodingException e )
						{
						}
					}
						break;
					case OP_PUSHDATA4:
					{
						b.append ("OP_PUSHDATA4 ");
						b.append (" ");
						long n = reader.readScriptInt32 ();
						try
						{
							b.append (new String (Hex.encode (reader.readBytes ((int) n)), "US-ASCII"));
							b.append (" ");
						}
						catch ( UnsupportedEncodingException e )
						{
						}
					}
						break;
					default:
						b.append (op.toString ());
						b.append (" ");
				}
			}
		}
		return b.toString ();
	}

	private static byte[] findAndDeleteSignatureAndSeparator (byte[] script, byte[] data)
	{
		WireFormat.Reader reader = new WireFormat.Reader (script);
		WireFormat.Writer writer = new WireFormat.Writer (new ByteArrayOutputStream ());
		while ( !reader.eof () )
		{
			int op = reader.readScriptOpcode ();
			if ( op <= 75 )
			{
				boolean found = false;
				byte[] b = reader.readBytes (op);
				if ( op == data.length )
				{
					int i = 0;
					for ( ; i < b.length; ++i )
					{
						if ( b[i] != data[i] )
						{
							break;
						}
					}
					if ( i == b.length )
					{
						found = true;
					}
				}
				if ( !found )
				{
					writer.writeByte (op);
					writer.writeBytes (b);
				}
			}
			else
			{
				Opcode code = Opcode.values ()[op];
				switch ( code )
				{
					case OP_PUSHDATA1:
					{
						int n = reader.readScriptOpcode ();
						writer.writeByte (op);
						writer.writeBytes (reader.readBytes (n));
					}
						break;
					case OP_PUSHDATA2:
					{
						long n = reader.readScriptInt16 ();
						writer.writeByte (op);
						writer.writeBytes (reader.readBytes ((int) n));
					}
						break;
					case OP_PUSHDATA4:
					{
						long n = reader.readScriptInt32 ();
						writer.writeByte (op);
						writer.writeBytes (reader.readBytes ((int) n));
					}
						break;
					case OP_CODESEPARATOR:
						break;
					default:
						writer.writeByte (op);
						break;
				}
			}
		}
		return writer.toByteArray ();
	}

	public boolean evaluate ()
	{
		System.out.println (toString ());
		WireFormat.Reader reader = new WireFormat.Reader (script);

		Stack<Boolean> ignoreStack = new Stack<Boolean> ();
		ignoreStack.push (false);

		int offset = -1;
		int codeseparator = 0;
		try
		{
			while ( !reader.eof () )
			{
				++offset;
				Opcode op = Opcode.values ()[reader.readScriptOpcode ()];
				if ( op.o <= 75 )
				{
					if ( ignoreStack.peek () )
					{
						reader.skipBytes (op.o);
					}
					else
					{
						stack.push (reader.readBytes (op.o));
					}
				}
				switch ( op )
				{
					case OP_PUSHDATA1:
					{
						int n = reader.readScriptOpcode ();
						if ( ignoreStack.peek () )
						{
							reader.skipBytes (n);
						}
						else
						{
							stack.push (reader.readBytes (n));
						}
					}
						break;
					case OP_PUSHDATA2:
					{
						long n = reader.readScriptInt16 ();
						if ( ignoreStack.peek () )
						{
							reader.skipBytes ((int) n);
						}
						else
						{
							stack.push (reader.readBytes ((int) n));
						}
					}
						break;
					case OP_PUSHDATA4:
					{
						long n = reader.readScriptInt32 ();
						if ( ignoreStack.peek () )
						{
							reader.skipBytes ((int) n);
						}
						else
						{
							stack.push (reader.readBytes ((int) n));
						}
					}
						break;
					case OP_IF:
						if ( !ignoreStack.peek () && popBoolean () )
						{
							ignoreStack.push (false);
						}
						else
						{
							ignoreStack.push (true);
						}
						break;
					case OP_NOTIF:
						if ( !ignoreStack.peek () && !popBoolean () )
						{
							ignoreStack.push (false);
						}
						else
						{
							ignoreStack.push (true);
						}
						break;
					case OP_ENDIF:
						ignoreStack.pop ();
						break;
					case OP_ELSE:
						ignoreStack.push (!ignoreStack.pop ());
						break;
				}
				if ( !ignoreStack.peek () )
				{
					switch ( op )
					{
						case OP_VERIFY:
							if ( !isTrue (stack.peek ()) )
							{
								return false;
							}
							else
							{
								stack.pop ();
							}
							break;
						case OP_RETURN:
							return false;
						case OP_NOP:
							break;
						case OP_1NEGATE:
							pushInt (-1);
							break;
						case OP_1:
							pushInt (1);
							break;
						case OP_2:
							pushInt (2);
							break;
						case OP_3:
							pushInt (3);
							break;
						case OP_4:
							pushInt (4);
							break;
						case OP_5:
							pushInt (5);
							break;
						case OP_6:
							pushInt (6);
							break;
						case OP_7:
							pushInt (7);
							break;
						case OP_8:
							pushInt (8);
							break;
						case OP_9:
							pushInt (9);
							break;
						case OP_10:
							pushInt (10);
							break;
						case OP_11:
							pushInt (11);
							break;
						case OP_12:
							pushInt (12);
							break;
						case OP_13:
							pushInt (13);
							break;
						case OP_14:
							pushInt (14);
							break;
						case OP_15:
							pushInt (15);
							break;
						case OP_16:
							pushInt (16);
							break;
						case OP_VER:
						case OP_RESERVED:
						case OP_VERIF:
						case OP_VERNOTIF:
							return false;
						case OP_TOALTSTACK:
							alt.push (stack.pop ());
							break;
						case OP_FROMALTSTACK:
							stack.push (alt.pop ());
							break;
						case OP_2DROP:
							stack.pop ();
							stack.pop ();
							break;
						case OP_2DUP:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							stack.push (a2);
							stack.push (a1);
							stack.push (a2);
							stack.push (a1);
						}
							break;
						case OP_3DUP:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							byte[] a3 = stack.pop ();
							stack.push (a3);
							stack.push (a2);
							stack.push (a1);
							stack.push (a3);
							stack.push (a2);
							stack.push (a1);
						}
							break;
						case OP_2OVER:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							byte[] a3 = stack.pop ();
							byte[] a4 = stack.pop ();
							stack.push (a4);
							stack.push (a3);
							stack.push (a2);
							stack.push (a1);
							stack.push (a4);
							stack.push (a3);
						}
							break;
						case OP_2ROT:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							byte[] a3 = stack.pop ();
							byte[] a4 = stack.pop ();
							byte[] a5 = stack.pop ();
							byte[] a6 = stack.pop ();
							stack.push (a4);
							stack.push (a3);
							stack.push (a2);
							stack.push (a1);
							stack.push (a6);
							stack.push (a5);
						}
							break;
						case OP_2SWAP:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							byte[] a3 = stack.pop ();
							byte[] a4 = stack.pop ();
							stack.push (a2);
							stack.push (a1);
							stack.push (a4);
							stack.push (a3);
						}
							break;
						case OP_IFDUP:
							if ( peekBoolean () )
							{
								stack.push (stack.peek ());
							}
							break;
						case OP_DEPTH:
							pushInt (stack.size ());
							break;
						case OP_DROP:
							stack.pop ();
							break;
						case OP_DUP:
						{
							stack.push (stack.peek ());
						}
							break;
						case OP_NIP:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							stack.push (a1);
						}
							break;
						case OP_OVER:
						{
							byte[] a1 = stack.pop ();
							byte[] a2 = stack.pop ();
							stack.push (a2);
							stack.push (a1);
							stack.push (a2);
						}
							break;
						case OP_PICK:
						{
							int n = popNumber ().intValue ();
							stack.push (stack.get (stack.size () - 1 - n));
						}
							break;
						case OP_ROLL:
						{
							int n = popNumber ().intValue ();
							byte[] a = stack.get (stack.size () - 1 - n);
							stack.remove (stack.size () - 1 - n);
							stack.push (a);
						}
							break;
						case OP_ROT:
						{
							byte[] a = stack.get (stack.size () - 1 - 2);
							stack.remove (stack.size () - 1 - 2);
							stack.push (a);
						}
							break;
						case OP_SWAP:
						{
							byte[] a = stack.pop ();
							byte[] b = stack.pop ();
							stack.push (a);
							stack.push (b);
						}
							break;
						case OP_TUCK:
						{
							byte[] a = stack.pop ();
							byte[] b = stack.pop ();
							stack.push (a);
							stack.push (b);
							stack.push (a);
						}
							break;
						case OP_CAT:
						case OP_SUBSTR:
						case OP_LEFT:
						case OP_RIGHT:
							return false;
						case OP_SIZE:
							pushInt (stack.peek ().length);
							break;
						case OP_INVERT:
						case OP_AND:
						case OP_OR:
						case OP_XOR:
							return false;
						case OP_EQUAL:
						case OP_EQUALVERIFY:
						{
							byte[] a = stack.pop ();
							byte[] b = stack.pop ();
							if ( a.length != b.length )
							{
								pushInt (0);
							}
							else
							{
								int i;
								for ( i = 0; i < a.length; ++i )
								{
									if ( a[i] != b[i] )
									{
										pushInt (0);
										break;
									}
								}
								if ( i == a.length )
								{
									pushInt (1);
								}
							}
							if ( op == Opcode.OP_EQUALVERIFY )
							{
								if ( !isTrue (stack.peek ()) )
								{
									return false;
								}
								else
								{
									stack.pop ();
								}
								break;
							}
						}
						case OP_RESERVED1:
						case OP_RESERVED2:
							return false;
						case OP_1ADD:// 0x8b in out 1 is added to the input.
							pushInt (popInt () + 1);
							break;
						case OP_1SUB:// 0x8c in out 1 is subtracted from the
										// input.
							pushInt (popInt () - 1);
							break;
						case OP_2MUL:// 0x8d in out The input is multiplied by
										// 2.
										// Currently disabled.
						case OP_2DIV:// 0x8e in out The input is divided by 2.
										// Currently disabled.
							return false;
						case OP_NEGATE:// 0x8f in out The sign of the input is
										// flipped.
							pushInt (-popInt ());
							break;
						case OP_ABS:// 0x90 in out The input is made positive.
							pushInt (Math.abs (popInt ()));
							break;
						case OP_NOT: // 0x91 in out If the input is 0 or 1, it
										// is
										// flipped. Otherwise the output will be
										// 0.
							pushInt (popInt () == 0 ? 1 : 0);
							break;
						case OP_0NOTEQUAL:// 0x92 in out Returns 0 if the input
											// is
											// 0. 1 otherwise.
							pushInt (popInt () == 0 ? 0 : 1);
							break;
						case OP_ADD:// 0x93 a b out a is added to b.
							pushInt (popInt () + popInt ());
							break;
						case OP_SUB:// 0x94 a b out b is subtracted from a.
						{
							long a = popInt ();
							long b = popInt ();
							pushInt (b - a);
						}
							break;
						case OP_MUL:// 0x95 a b out a is multiplied by b.
									// Currently
									// disabled.
						case OP_DIV: // 0x96 a b out a is divided by b.
										// Currently
										// disabled.
						case OP_MOD:// 0x97 a b out Returns the remainder after
									// dividing a by b. Currently disabled.
						case OP_LSHIFT:// 0x98 a b out Shifts a left b bits,
										// preserving sign. Currently disabled.
						case OP_RSHIFT:// 0x99 a b out Shifts a right b bits,
										// preserving sign. Currently disabled.
							return false;

						case OP_BOOLAND:// 0x9a a b out If both a and b are not
										// 0,
										// the output is 1. Otherwise 0.
							pushInt (popInt () != 0 && popInt () != 0 ? 1 : 0);
							break;
						case OP_BOOLOR:// 0x9b a b out If a or b is not 0, the
										// output is 1. Otherwise 0.
							pushInt (popInt () != 0 || popInt () != 0 ? 1 : 0);
							break;
						case OP_NUMEQUAL:// 0x9c a b out Returns 1 if the
											// numbers
											// are equal, 0 otherwise.
							pushInt (popInt () == popInt () ? 1 : 0);
							break;
						case OP_NUMEQUALVERIFY:// 0x9d a b out Same as
												// OP_NUMEQUAL,
												// but runs OP_VERIFY afterward.
							if ( popInt () != popInt () )
							{
								return false;
							}
							break;
						case OP_NUMNOTEQUAL:// 0x9e a b out Returns 1 if the
											// numbers
											// are not equal, 0 otherwise.
							pushInt (popInt () == popInt () ? 1 : 0);
							break;

						case OP_LESSTHAN:// 0x9f a b out Returns 1 if a is less
											// than
											// b, 0 otherwise.
						{
							long a = popInt ();
							long b = popInt ();
							pushInt (b < a ? 1 : 0);
						}
							break;
						case OP_GREATERTHAN:// 0xa0 a b out Returns 1 if a is
											// greater than b, 0 otherwise.
						{
							long a = popInt ();
							long b = popInt ();
							pushInt (b > a ? 1 : 0);
						}
							break;
						case OP_LESSTHANOREQUAL:// 0xa1 a b out Returns 1 if a
												// is
												// less than or equal to b, 0
												// otherwise.
						{
							long a = popInt ();
							long b = popInt ();
							pushInt (b <= a ? 1 : 0);
						}
							break;
						case OP_GREATERTHANOREQUAL:// 0xa2 a b out Returns 1 if
													// a is
													// greater than or equal to
													// b, 0
													// otherwise.
						{
							long a = popInt ();
							long b = popInt ();
							pushInt (b >= a ? 1 : 0);
						}
							break;
						case OP_MIN:// 0xa3 a b out Returns the smaller of a and
									// b.
							pushInt (Math.min (popInt (), popInt ()));
							break;
						case OP_MAX:// 0xa4 a b out Returns the larger of a and
									// b.
							pushInt (Math.max (popInt (), popInt ()));
							break;
						case OP_WITHIN: // 0xa5 x min max out Returns 1 if x is
										// within the specified range
										// (left-inclusive), 0 otherwise.
						{
							long a = popInt ();
							long b = popInt ();
							long c = popInt ();
							pushInt (c >= b && c < a ? 1 : 0);
						}
							break;
						case OP_RIPEMD160: // 0xa6 in hash The input is hashed
											// using
											// RIPEMD-160.
						{
							RIPEMD160Digest digest = new RIPEMD160Digest ();
							byte[] data = stack.pop ();
							digest.update (data, 0, data.length);
							byte[] hash = new byte[20];
							digest.doFinal (hash, 0);
							stack.push (hash);
						}
							break;
						case OP_SHA1: // 0xa7 in hash The input is hashed using
										// SHA-1.
						{
							try
							{
								MessageDigest a = MessageDigest.getInstance ("SHA-1");
								stack.push (a.digest (stack.pop ()));
							}
							catch ( NoSuchAlgorithmException e )
							{
								return false;
							}
						}
							break;
						case OP_SHA256: // 0xa8 in hash The input is hashed
										// using
										// SHA-256.
						{
							try
							{
								MessageDigest a = MessageDigest.getInstance ("SHA-256");
								stack.push (a.digest (stack.pop ()));
							}
							catch ( NoSuchAlgorithmException e )
							{
								return false;
							}
						}
							break;
						case OP_HASH160: // 0xa9 in hash The input is hashed
											// twice:
											// first with SHA-256 and then with
											// RIPEMD-160.
						{
							try
							{
								MessageDigest a = MessageDigest.getInstance ("SHA-256");
								RIPEMD160Digest digest = new RIPEMD160Digest ();
								digest.update (a.digest (stack.pop ()), 0, 32);
								byte[] hash = new byte[20];
								digest.doFinal (hash, 0);
								stack.push (hash);
							}
							catch ( NoSuchAlgorithmException e )
							{
								return false;
							}
						}
							break;
						case OP_HASH256: // 0xaa in hash The input is hashed two
											// times with SHA-256.
						{
							try
							{
								MessageDigest a = MessageDigest.getInstance ("SHA-256");
								stack.push (a.digest (a.digest (stack.pop ())));
							}
							catch ( NoSuchAlgorithmException e )
							{
								return false;
							}
						}
							break;
						case OP_CODESEPARATOR: // 0xab Nothing Nothing All of
												// the
												// signature checking words will
												// only match signatures to the
												// data
												// after the most
												// recently-executed
												// OP_CODESEPARATOR.
							codeseparator = offset + 1;
							break;
						case OP_CHECKSIGVERIFY: // 0xad sig pubkey True / false
							// Same
							// as OP_CHECKSIG, but OP_VERIFY
							// is
							// executed afterward.
							// / no break;
						case OP_CHECKSIG: // 0xac sig pubkey True / false The
											// entire
											// transaction's outputs, inputs,
											// and
											// script (from the most
											// recently-executed
											// OP_CODESEPARATOR to
											// the end) are hashed. The
											// signature
											// used by OP_CHECKSIG must be a
											// valid
											// signature for this hash and
											// public
											// key. If it is, 1 is returned, 0
											// otherwise.
						{
							byte[] pubkey = stack.pop ();
							byte[] sig = stack.pop ();
							byte[] signedScript = new byte[script.length - codeseparator];
							System.arraycopy (script, codeseparator, signedScript, 0, script.length - codeseparator);
							signedScript = findAndDeleteSignatureAndSeparator (signedScript, sig);
							String s = new Script (signedScript).toString ();
							System.out.println (s);
							Map<TxIn, byte[]> originalScripts = new HashMap<TxIn, byte[]> ();
							for ( TxIn in : tx.getInputs () )
							{
								originalScripts.put (in, in.getScript ());
								if ( in.getIx () == inr )
								{
									in.setScript (signedScript);
								}
								else
								{
									in.setScript (new byte[0]);
								}
							}
							WireFormat.Writer writer = new WireFormat.Writer (new ByteArrayOutputStream ());
							tx.toWire (writer);
							for ( Map.Entry<TxIn, byte[]> e : originalScripts.entrySet () )
							{
								e.getKey ().setScript (e.getValue ());
							}
							byte[] txwire = writer.toByteArray ();
							byte[] hash;
							try
							{
								MessageDigest a = MessageDigest.getInstance ("SHA-256");
								a.update (txwire);
								a.update (new byte[4]);
								hash = a.digest (a.digest ());
							}
							catch ( NoSuchAlgorithmException e )
							{
								return false;
							}

							boolean valid = ECKeyPair.verify (hash, sig, pubkey);
							pushInt (valid ? 1 : 0);
							if ( op == Opcode.OP_CHECKSIGVERIFY )
							{
								if ( !isTrue (stack.peek ()) )
								{
									return false;
								}
								else
								{
									stack.pop ();
								}
							}
						}
							break;
						case OP_CHECKMULTISIG: // 0xae x sig1 sig2 ... <number
												// of
												// signatures> pub1 pub2 <number
												// of
												// public keys> True / False For
												// each signature and public key
												// pair, OP_CHECKSIG is
												// executed. If
												// more public keys than
												// signatures
												// are listed, some key/sig
												// pairs
												// can fail. All signatures need
												// to
												// match a public key. If all
												// signatures are valid, 1 is
												// returned, 0 otherwise. Due to
												// a
												// bug, one extra unused value
												// is
												// removed from the stack.
							break;
						case OP_CHECKMULTISIGVERIFY:// 0xaf x sig1 sig2 ...
													// <number
													// of signatures> pub1 pub2
													// ...
													// <number of public keys>
													// True
													// / False Same as
													// OP_CHECKMULTISIG, but
													// OP_VERIFY is executed
													// afterward.
							break;

					}
					if ( op.o >= 176 && op.o <= 185 )
					{
						continue;
					}
					if ( op.o > 185 )
					{
						return false;
					}

				}
			}
		}
		catch ( Exception e )
		{
			return false;
		}
		return true;
	}
}
