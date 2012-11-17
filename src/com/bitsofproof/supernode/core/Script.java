/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;

public class Script
{
	private Stack<byte[]> stack = new Stack<byte[]> ();
	private final Stack<byte[]> alt = new Stack<byte[]> ();
	private final Tx tx;
	private int inr;

	@SuppressWarnings ("unused")
	// unfortunatelly unused: https://bitcointalk.org/index.php?topic=120836.0
	private static final int SIGHASH_ALL = 1;

	private static final int SIGHASH_NONE = 2;
	private static final int SIGHASH_SINGLE = 3;
	private static final int SIGHASH_ANYONECANPAY = 0x80;

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
		OP_CHECKMULTISIGVERIFY (175), // 0xaf x sig1 sig2 ... <number of
										// signatures> pub1 pub2 ... <number of
										// public keys> True / False Same as
										// OP_CHECKMULTISIG, but OP_VERIFY is
										// executed afterward.
		OP_NOP1 (176), OP_NOP2 (177), OP_NOP3 (178), OP_NOP4 (179), OP_NOP5 (180), OP_NOP6 (181), OP_NOP7 (182), OP_NOP8 (183), OP_NOP9 (184), OP_NOP10 (185);

		private final int o;

		Opcode (int n)
		{
			this.o = n;
		}
	};

	public static class Reader
	{
		private final byte[] bytes;
		private int cursor;

		public Reader (byte[] script)
		{
			this.bytes = script;
			this.cursor = 0;
		}

		public boolean eof ()
		{
			return cursor == bytes.length;
		}

		public byte[] readBytes (int n)
		{
			byte[] b = new byte[n];
			System.arraycopy (bytes, cursor, b, 0, n);
			cursor += n;
			return b;
		}

		public void skipBytes (int n)
		{
			cursor += n;
		}

		public int readByte ()
		{
			return bytes[cursor++] & 0xff;
		}

		public long readInt16 ()
		{
			long value = ((bytes[cursor] & 0xFFL) << 0) | ((bytes[cursor + 1] & 0xFFL) << 8);
			cursor += 2;
			return value;
		}

		public long readInt32 ()
		{
			long value =
					((bytes[cursor] & 0xFFL) << 0) | ((bytes[cursor + 1] & 0xFFL) << 8) | ((bytes[cursor + 2] & 0xFFL) << 16)
							| ((bytes[cursor + 3] & 0xFFL) << 24);
			cursor += 4;
			return value;

		}
	}

	public static class Writer
	{
		private final ByteArrayOutputStream s;

		public Writer ()
		{
			s = new ByteArrayOutputStream ();
		}

		public Writer (ByteArrayOutputStream s)
		{
			this.s = s;
		}

		public void writeByte (int n)
		{
			s.write (n);
		}

		public void writeBytes (byte[] b)
		{
			try
			{
				s.write (b);
			}
			catch ( IOException e )
			{
			}
		}

		public void writeData (byte[] data)
		{
			if ( data.length <= 75 )
			{
				writeByte (data.length);
				writeBytes (data);
			}
			else if ( data.length <= 0xff )
			{
				writeByte (Opcode.OP_PUSHDATA1.o);
				writeByte (data.length);
				writeBytes (data);
			}
			else if ( data.length <= 0xffff )
			{
				writeByte (Opcode.OP_PUSHDATA2.o);
				writeInt16 (data.length);
				writeBytes (data);
			}
			else if ( data.length <= 0x7fffffff )
			{
				writeByte (Opcode.OP_PUSHDATA4.o);
				writeInt16 (data.length);
				writeBytes (data);
			}
		}

		public void writeToken (Token token)
		{
			s.write (token.op.o);
			if ( token.data != null )
			{
				try
				{
					s.write (token.data);
				}
				catch ( IOException e )
				{
				}
			}
		}

		public void writeInt16 (long n)
		{
			s.write ((int) (0xFFL & n));
			s.write ((int) (0xFFL & (n >> 8)));
		}

		public void writeInt32 (long n)
		{
			s.write ((int) (0xFF & n));
			s.write ((int) (0xFF & (n >> 8)));
			s.write ((int) (0xFF & (n >> 16)));
			s.write ((int) (0xFF & (n >> 24)));
		}

		public byte[] toByteArray ()
		{
			return s.toByteArray ();
		}
	}

	private static class Number
	{
		byte[] w;

		public Number (byte[] b)
		{
			w = new byte[b.length];
			System.arraycopy (b, 0, w, 0, b.length);
		}

		public Number (long n) throws ValidationException
		{
			if ( Math.abs (n) > 0xffffffffL )
			{
				throw new ValidationException ("Number overflow in script");
			}
			if ( n == 0 )
			{
				w = new byte[0];
				return;
			}
			boolean negative = false;
			if ( n < 0 )
			{
				negative = true;
				n = -n;
			}
			if ( n <= 0xff )
			{
				w = new byte[] { (byte) (n & 0xff) };
				w[0] |= negative ? 0x80 : 0;
				return;
			}
			if ( n <= 0xffff )
			{
				w = new byte[] { (byte) (n & 0xff), (byte) ((n >> 8) & 0xff) };
				w[1] |= negative ? 0x80 : 0;
				return;
			}
			if ( n <= 0xffffff )
			{
				w = new byte[] { (byte) (n & 0xff), (byte) ((n >> 8) & 0xff), (byte) ((n >> 16) & 0xff) };
				w[2] |= negative ? 0x80 : 0;
				return;
			}
			w = new byte[] { (byte) (n & 0xff), (byte) ((n >> 8) & 0xff), (byte) ((n >> 16) & 0xff), (byte) ((n >> 24) & 0xff) };
			if ( ((n >> 24) & 0x80) != 0 )
			{
				byte[] tmp = new byte[5];
				System.arraycopy (w, 0, tmp, 0, 4);
				w = tmp;
			}
			w[w.length - 1] |= negative ? 0x80 : 0;
		}

		public byte[] toByteArray ()
		{
			byte[] tmp = new byte[w.length];
			System.arraycopy (w, 0, tmp, 0, w.length);
			return tmp;
		}

		public long intValue () throws ValidationException
		{
			if ( w.length == 0 )
			{
				return 0;
			}
			boolean negative = false;
			if ( (w[w.length - 1] & 0x80) != 0 )
			{
				negative = true;
				w[w.length - 1] &= 0x7f;
			}
			int n = 0;
			if ( w.length > 0 )
			{
				n += w[0] & 0xff;
			}
			if ( w.length > 1 )
			{
				n += (w[1] & 0xff) << 8;
			}
			if ( w.length > 2 )
			{
				n += (w[2] & 0xff) << 16;
			}
			if ( w.length > 3 )
			{
				n += (w[3] & 0xff) << 24;
			}
			if ( negative )
			{
				n = -n;
			}
			return n;
		}
	}

	private void pushInt (long n) throws ValidationException
	{
		stack.push (new Number (n).toByteArray ());
	}

	private long popInt () throws ValidationException
	{
		return new Number (stack.pop ()).intValue ();
	}

	public static int intValue (byte[] n) throws ValidationException
	{
		return (int) new Number (n).intValue ();
	}

	private static boolean equals (byte[] a, byte[] b)
	{
		int l = Math.max (a.length, b.length);
		if ( a.length < l )
		{
			byte[] tmp = new byte[l];
			System.arraycopy (a, 0, tmp, 0, a.length);
			a = tmp;
		}
		if ( b.length < l )
		{
			byte[] tmp = new byte[l];
			System.arraycopy (b, 0, tmp, 0, b.length);
			b = tmp;
		}
		return Arrays.equals (a, b);
	}

	private boolean isFalse (byte[] b)
	{
		return b.length == 0 || b.length == 1 && (b[0] == 0x80 || b[0] == 0x00);
	}

	private boolean isTrue (byte[] b)
	{
		return !isFalse (b);
	}

	private boolean popBoolean ()
	{
		return isTrue (stack.pop ());
	}

	private boolean peekBoolean ()
	{
		return isTrue (stack.peek ());
	}

	public Script ()
	{
		this.tx = null;
	}

	public Script (Tx tx, int inr)
	{
		this.tx = tx;
		this.inr = inr;
	}

	public static byte[] fromReadable (String s)
	{
		Writer writer = new Writer ();
		StringTokenizer tokenizer = new StringTokenizer (s, " ");
		while ( tokenizer.hasMoreElements () )
		{
			String token = tokenizer.nextToken ();
			Opcode op = Opcode.OP_FALSE;
			if ( token.startsWith ("OP_") )
			{
				op = Opcode.valueOf (token);
				writer.writeByte (op.o);
			}
			else
			{
				writer.writeData (ByteUtils.fromHex (token));
			}
		}
		return writer.toByteArray ();
	}

	public static class Token
	{
		public Opcode op;
		public byte[] data;

		public Token ()
		{
		}

		public Token (Opcode op)
		{
			this.op = op;
			data = null;
		}
	}

	public static class Tokenizer
	{
		private final Reader reader;

		public Tokenizer (byte[] script)
		{
			reader = new Reader (script);
		}

		public boolean hashMoreElements ()
		{
			return !reader.eof ();
		}

		int getCursor ()
		{
			return reader.cursor;
		}

		@SuppressWarnings ("incomplete-switch")
		public Token nextToken () throws ValidationException
		{
			Token token = new Token ();

			int ix = reader.readByte ();
			if ( ix > 185 )
			{
				throw new ValidationException ("Invalid script" + ix + " opcode at " + reader.cursor);
			}
			Opcode op = Opcode.values ()[ix];
			token.op = op;
			if ( op.o <= 75 )
			{
				token.data = reader.readBytes (op.o);
				return token;
			}
			switch ( op )
			{
				case OP_PUSHDATA1:
				{
					token.data = reader.readBytes (reader.readByte ());
					break;
				}
				case OP_PUSHDATA2:
				{
					token.data = reader.readBytes ((int) reader.readInt16 ());
					break;
				}
				case OP_PUSHDATA4:
				{
					token.data = reader.readBytes ((int) reader.readInt32 ());
					break;
				}
			}
			return token;
		}
	}

	public static List<Token> parse (byte[] script) throws ValidationException
	{
		List<Token> p = new ArrayList<Token> ();
		Tokenizer tokenizer = new Tokenizer (script);
		while ( tokenizer.hashMoreElements () )
		{
			p.add (tokenizer.nextToken ());
		}
		return p;
	}

	public static String toReadable (byte[] script) throws ValidationException
	{
		List<Token> tokens = parse (script);
		StringBuffer b = new StringBuffer ();
		boolean first = true;
		for ( Token token : tokens )
		{
			if ( !first )
			{
				b.append (" ");
			}
			first = false;
			if ( token.data != null )
			{
				if ( token.data.length > 0 )
				{
					b.append (ByteUtils.toHex (token.data));
				}
				else
				{
					b.append ("OP_FALSE");
				}
			}
			else
			{
				b.append (token.op);
			}
		}
		return b.toString ();
	}

	public static boolean isPushOnly (byte[] script) throws ValidationException
	{
		for ( Token t : parse (script) )
		{
			if ( t.op.o > 75 || t.data != null )
			{
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings ("incomplete-switch")
	public static int sigOpCount (byte[] script) throws ValidationException
	{
		int nsig = 0;
		Opcode last = Opcode.OP_FALSE;
		Tokenizer tokenizer = new Tokenizer (script);
		while ( tokenizer.hashMoreElements () )
		{
			Token token = tokenizer.nextToken ();

			if ( token.data == null )
			{
				switch ( token.op )
				{
					case OP_CHECKSIG:
					case OP_CHECKSIGVERIFY:
						++nsig;
						break;
					case OP_CHECKMULTISIG:
					case OP_CHECKMULTISIGVERIFY:
						// https://en.bitcoin.it/wiki/BIP_0016
						if ( last.o >= 0 && last.o <= 16 )
						{
							nsig += last.o;
						}
						else
						{
							nsig += 20;
						}
						break;
				}
				last = token.op;
			}
		}
		return nsig;
	}

	private static byte[] deleteSignaturFromScript (byte[] script, byte[] sig) throws ValidationException
	{
		Tokenizer tokenizer = new Tokenizer (script);
		Writer writer = new Writer ();
		while ( tokenizer.hashMoreElements () )
		{
			Token token = tokenizer.nextToken ();
			if ( token.data != null && token.data.length == sig.length )
			{
				boolean found = true;
				for ( int i = 0; i < sig.length; ++i )
				{
					if ( sig[i] != token.data[i] )
					{
						found = false;
						break;
					}
				}
				if ( !found )
				{
					writer.writeToken (token);
				}
			}
			else
			{
				writer.writeToken (token);
			}
		}
		return writer.toByteArray ();
	}

	public static boolean isPayToScriptHash (byte[] script) throws ValidationException
	{
		List<Token> parsed = parse (script);
		return parsed.size () == 3 && parsed.get (0).op == Opcode.OP_HASH160 && parsed.get (1).data != null && parsed.get (1).data.length == 20
				&& parsed.get (2).op == Opcode.OP_EQUAL;
	}

	public static boolean isPayToKey (byte[] script) throws ValidationException
	{
		List<Token> parsed = parse (script);
		return parsed.size () == 2 && parsed.get (0).data != null && parsed.get (1).op == Opcode.OP_CHECKSIG;
	}

	public static boolean isPayToAddress (byte[] script) throws ValidationException
	{
		List<Token> parsed = parse (script);
		return parsed.size () == 5 && parsed.get (0).op == Opcode.OP_DUP && parsed.get (1).op == Opcode.OP_HASH160 && parsed.get (2).data != null
				&& parsed.get (3).op == Opcode.OP_EQUALVERIFY && parsed.get (4).op == Opcode.OP_CHECKSIG;
	}

	public static boolean isMultiSig (byte[] script) throws ValidationException
	{
		List<Token> parsed = parse (script);
		boolean multisig = false;
		int nkeys = -1;
		int nvotes = -1;
		for ( int i = 0; i < parsed.size (); ++i )
		{
			if ( parsed.get (i).op == Opcode.OP_CHECKMULTISIG || parsed.get (i).op == Opcode.OP_CHECKMULTISIGVERIFY )
			{
				nkeys = Script.intValue (parsed.get (i - 1).data);
				nvotes = Script.intValue (parsed.get (i - nkeys - 2).data);
				break;
			}
		}
		if ( nkeys >= 0 )
		{
			if ( parsed.size () != nkeys + 3 )
			{
				return false;
			}
		}
		if ( nkeys <= 0 || nkeys > 3 )
		{
			return false;
		}
		if ( nvotes < 0 || nvotes > nkeys )
		{
			return false;
		}
		return multisig;
	}

	public static boolean isStandard (byte[] script) throws ValidationException
	{
		return isPayToAddress (script) || isPayToKey (script) || isPayToScriptHash (script) || isMultiSig (script);
	}

	public static byte[] getPayToAddressScript (byte[] keyHash)
	{
		Writer writer = new Writer ();
		writer.writeToken (new Token (Opcode.OP_DUP));
		writer.writeToken (new Token (Opcode.OP_HASH160));
		writer.writeData (keyHash);
		writer.writeToken (new Token (Opcode.OP_EQUALVERIFY));
		writer.writeToken (new Token (Opcode.OP_CHECKSIG));
		return writer.toByteArray ();
	}

	public String toReadableConnected () throws ValidationException
	{
		StringBuffer b = new StringBuffer ();

		byte[] s1 = tx.getInputs ().get (inr).getScript ();
		byte[] s2 = tx.getInputs ().get (inr).getSource ().getScript ();

		b.append (Script.toReadable (s1));
		b.append (" | ");
		b.append (Script.toReadable (s2));
		return b.toString ();
	}

	public String dumpConnected ()
	{
		byte[] s1 = tx.getInputs ().get (inr).getScript ();
		byte[] s2 = tx.getInputs ().get (inr).getSource ().getScript ();
		byte[] c = new byte[s1.length + s2.length];
		System.arraycopy (s1, 0, c, 0, s1.length);
		System.arraycopy (s2, 0, c, s1.length, s2.length);
		return ByteUtils.toHex (c);
	}

	@SuppressWarnings ("unchecked")
	public boolean evaluate (boolean production) throws ValidationException
	{
		Stack<byte[]> copy = new Stack<byte[]> ();

		byte[] s1 = tx.getInputs ().get (inr).getScript ();
		byte[] s2 = tx.getInputs ().get (inr).getSource ().getScript ();

		if ( !evaluateSingleScript (s1) )
		{
			return false;
		}
		boolean psh = isPayToScriptHash (s1);
		if ( psh )
		{
			copy = (Stack<byte[]>) stack.clone ();
		}
		if ( !evaluateSingleScript (s2) )
		{
			return false;
		}
		if ( popBoolean () == false )
		{
			return false;
		}
		if ( psh )
		{
			if ( !isPushOnly (s1) )
			{
				throw new ValidationException ("input script for PTH should be push only.");
			}
			stack = copy;
			byte[] script = stack.pop ();
			if ( production )
			{
				if ( !isStandard (script) )
				{
					return false;
				}
			}
			if ( !evaluateSingleScript (script) )
			{
				return false;
			}
			return popBoolean ();
		}
		return true;
	}

	@SuppressWarnings ("incomplete-switch")
	public boolean evaluateSingleScript (byte[] script)
	{
		Tokenizer tokenizer = new Tokenizer (script);
		int codeseparator = 0;

		Stack<Boolean> ignoreStack = new Stack<Boolean> ();
		ignoreStack.push (false);

		try
		{
			while ( tokenizer.hashMoreElements () )
			{
				Token token = tokenizer.nextToken ();
				if ( token.data != null )
				{
					if ( !ignoreStack.peek () )
					{
						stack.push (token.data);
					}
					continue;
				}
				switch ( token.op )
				{
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
					switch ( token.op )
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
						case OP_FALSE:
							stack.push (new byte[0]);
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
							stack.pop ();
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
							long n = popInt ();
							stack.push (stack.get (stack.size () - 1 - (int) n));
						}
							break;
						case OP_ROLL:
						{
							long n = popInt ();
							byte[] a = stack.get (stack.size () - 1 - (int) n);
							stack.remove ((int) (stack.size () - 1 - n));
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
							pushInt (equals (stack.pop (), stack.pop ()) == true ? 1 : 0);
							if ( token.op == Opcode.OP_EQUALVERIFY )
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
							pushInt (popInt () != popInt () ? 1 : 0);
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
							stack.push (Hash.sha256 (stack.pop ()));
						}
							break;
						case OP_HASH160: // 0xa9 in hash The input is hashed
											// twice:
											// first with SHA-256 and then with
											// RIPEMD-160.
						{
							stack.push (Hash.keyHash (stack.pop ()));
						}
							break;
						case OP_HASH256: // 0xaa in hash The input is hashed two
											// times with SHA-256.
						{
							stack.push (Hash.hash (stack.pop ()));
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
							codeseparator = tokenizer.getCursor ();
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

							byte[] sts = scriptToSign (script, codeseparator);
							sts = deleteSignaturFromScript (sts, sig);

							pushInt (validateSignature (pubkey, sig, sts) ? 1 : 0);
							if ( token.op == Opcode.OP_CHECKSIGVERIFY )
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
												// / no break;
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
						{
							int nkeys = (int) popInt ();
							if ( nkeys <= 0 || nkeys > 20 )
							{
								return false;
							}
							byte[][] keys = new byte[nkeys][];
							for ( int i = 0; i < nkeys; ++i )
							{
								keys[i] = stack.pop ();
							}
							int required = (int) popInt ();
							if ( required <= 0 )
							{
								return false;
							}

							byte[] sts = scriptToSign (script, codeseparator);

							int havesig = 0;
							byte[][] sigs = new byte[nkeys][];
							for ( int i = 0; i < nkeys && stack.size () > 1; ++i )
							{
								sigs[i] = stack.pop ();
								++havesig;
								sts = deleteSignaturFromScript (sts, sigs[i]);
							}
							stack.pop (); // reproduce Satoshi client bug
							int successCounter = 0;
							for ( int i = 0; (required - successCounter) <= (nkeys - i) && i < nkeys; ++i )
							{
								for ( int j = 0; successCounter < required && j < havesig; ++j )
								{
									if ( validateSignature (keys[i], sigs[j], sts) )
									{
										++successCounter;
									}
								}
							}
							pushInt (successCounter == required ? 1 : 0);
						}
							break;
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

	private byte[] scriptToSign (byte[] script, int codeseparator) throws ValidationException
	{
		byte[] signedScript = new byte[script.length - codeseparator];
		System.arraycopy (script, codeseparator, signedScript, 0, script.length - codeseparator);
		return signedScript;
	}

	private boolean validateSignature (byte[] pubkey, byte[] sig, byte[] script) throws ValidationException
	{
		byte hashType = sig[sig.length - 1];
		Tx copy = tx.flatCopy ();

		// implicit SIGHASH_ALL
		int i = 0;
		for ( TxIn in : copy.getInputs () )
		{
			if ( i == inr )
			{
				in.setScript (script);
			}
			else
			{
				in.setScript (new byte[0]);
			}
			++i;
		}

		if ( (hashType & 0x1f) == SIGHASH_NONE )
		{
			copy.getOutputs ().clear ();
			i = 0;
			for ( TxIn in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		else if ( (hashType & 0x1f) == SIGHASH_SINGLE )
		{
			int onr = inr;
			if ( onr >= copy.getOutputs ().size () )
			{
				throw new ValidationException ("Must have 1-1 in and output for SIGHASH_SINGLE");
			}
			for ( i = copy.getOutputs ().size () - 1; i > onr; --i )
			{
				copy.getOutputs ().remove (i);
			}
			for ( i = 0; i < onr; ++i )
			{
				copy.getOutputs ().get (i).setScript (new byte[0]);
				copy.getOutputs ().get (i).setValue (-1L);
			}
			i = 0;
			for ( TxIn in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		if ( (hashType & SIGHASH_ANYONECANPAY) != 0 )
		{
			List<TxIn> oneIn = new ArrayList<TxIn> ();
			oneIn.add (copy.getInputs ().get (inr));
			copy.setInputs (oneIn);
		}

		WireFormat.Writer writer = new WireFormat.Writer ();
		copy.toWire (writer);

		byte[] txwire = writer.toByteArray ();
		byte[] hash;
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (txwire);
			a.update (new byte[] { hashType, 0, 0, 0 });
			hash = a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException e )
		{
			return false;
		}
		return ECKeyPair.verify (hash, sig, pubkey);
	}
}
