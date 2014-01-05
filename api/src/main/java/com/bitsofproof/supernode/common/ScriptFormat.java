/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.supernode.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Address.Type;

public class ScriptFormat
{
	// unfortunatelly unused: https://bitcointalk.org/index.php?topic=120836.0
	public static final int SIGHASH_ALL = 1;
	public static final int SIGHASH_NONE = 2;
	public static final int SIGHASH_SINGLE = 3;
	public static final int SIGHASH_ANYONECANPAY = 0x80;
	public static final int SCRIPT_VERIFY_NONE = 0;
	public static final int SCRIPT_VERIFY_P2SH = 1;
	public static final int SCRIPT_VERIFY_STRICTENC = 2;
	public static final int SCRIPT_VERIFY_EVEN_S = 4;
	public static final int SCRIPT_VERIFY_NOCACHE = 8;

	public static enum Opcode
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

	public static class Reader
	{
		private final byte[] bytes;
		int cursor;

		public Reader (byte[] s)
		{
			this.bytes = new byte[s.length];
			System.arraycopy (s, 0, this.bytes, 0, s.length);
			this.cursor = 0;
		}

		public boolean eof ()
		{
			return cursor == bytes.length;
		}

		public byte[] readBytes (int n)
		{
			if ( n < 0 || (cursor + n) > bytes.length )
			{
				throw new ArrayIndexOutOfBoundsException (cursor + n);
			}
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
				writeInt32 (data.length);
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
					if ( token.op.o == Opcode.OP_PUSHDATA1.o )
					{
						writeByte (token.data.length);
					}
					if ( token.op.o == Opcode.OP_PUSHDATA2.o )
					{
						writeInt16 (token.data.length);
					}
					if ( token.op.o == Opcode.OP_PUSHDATA4.o )
					{
						writeInt32 (token.data.length);
					}
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

		public int getCursor ()
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

	public static class Number
	{
		byte[] w;

		public Number (byte[] b)
		{
			w = new byte[b.length];
			System.arraycopy (b, 0, w, 0, b.length);
		}

		public Number (long n) throws ValidationException
		{
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
			if ( n <= 0x7f )
			{
				w = new byte[] { (byte) (n & 0xff) };
				w[0] |= negative ? 0x80 : 0;
				return;
			}
			if ( n <= 0x7fff )
			{
				w = new byte[] { (byte) (n & 0xff), (byte) ((n >> 8) & 0xff) };
				w[1] |= negative ? 0x80 : 0;
				return;
			}
			if ( n <= 0x7fffff )
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

	public static int intValue (byte[] n) throws ValidationException
	{
		return (int) new ScriptFormat.Number (n).intValue ();
	}

	public static List<ScriptFormat.Token> parse (byte[] script) throws ValidationException
	{
		List<ScriptFormat.Token> p = new ArrayList<ScriptFormat.Token> ();
		ScriptFormat.Tokenizer tokenizer = new ScriptFormat.Tokenizer (script);
		while ( tokenizer.hashMoreElements () )
		{
			p.add (tokenizer.nextToken ());
		}
		return p;
	}

	public static boolean isPushOnly (byte[] script) throws ValidationException
	{
		for ( ScriptFormat.Token t : parse (script) )
		{
			if ( t.op.o > 78 )
			{
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings ("incomplete-switch")
	public static int sigOpCount (byte[] script, boolean accurate)
	{
		int nsig = 0;
		try
		{
			ScriptFormat.Opcode last = ScriptFormat.Opcode.OP_FALSE;
			ScriptFormat.Tokenizer tokenizer = new ScriptFormat.Tokenizer (script);
			while ( tokenizer.hashMoreElements () )
			{
				ScriptFormat.Token token = tokenizer.nextToken ();

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
							if ( accurate && last.o >= 0 && last.o <= 16 )
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
		}
		catch ( Exception e )
		{
			// count until possible.
		}
		return nsig;
	}

	public static byte[] fromReadable (String s)
	{
		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		StringTokenizer tokenizer = new StringTokenizer (s, " ");
		while ( tokenizer.hasMoreElements () )
		{
			String token = tokenizer.nextToken ();
			if ( token.startsWith ("0x") )
			{
				byte[] data = ByteUtils.fromHex (token.substring (2));
				writer.writeBytes (data);
			}
			else if ( token.startsWith ("'") )
			{
				String str = token.substring (1, token.length () - 1);
				try
				{
					writer.writeData (str.getBytes ("US-ASCII"));
				}
				catch ( UnsupportedEncodingException e )
				{
				}
			}
			else if ( (token.startsWith ("-") || token.startsWith ("0") || token.startsWith ("1") || token.startsWith ("2") || token.startsWith ("3")
					|| token.startsWith ("4") || token.startsWith ("5") || token.startsWith ("6") || token.startsWith ("7") || token.startsWith ("8") || token
						.startsWith ("9"))
					&& !token.equals ("0NOTEQUAL")
					&& !token.equals ("1NEGATE")
					&& !token.equals ("2DROP")
					&& !token.equals ("2DUP")
					&& !token.equals ("3DUP")
					&& !token.equals ("2OVER")
					&& !token.equals ("2ROT")
					&& !token.equals ("2SWAP")
					&& !token.equals ("1ADD")
					&& !token.equals ("1SUB") && !token.equals ("2MUL") && !token.equals ("2DIV") && !token.equals ("2SWAP") )
			{
				try
				{
					long n = Long.valueOf (token).longValue ();
					if ( n >= 1 && n <= 16 )
					{
						writer.writeByte (Opcode.OP_1.o + (int) n - 1);
					}
					else
					{
						writer.writeData (new Number (n).toByteArray ());
					}
				}
				catch ( NumberFormatException e )
				{
				}
				catch ( ValidationException e )
				{
				}
			}
			else
			{
				ScriptFormat.Opcode op;
				if ( token.startsWith ("OP_") )
				{
					op = ScriptFormat.Opcode.valueOf (token);
				}
				else
				{
					op = ScriptFormat.Opcode.valueOf ("OP_" + token);
				}
				writer.writeByte (op.o);
			}
		}
		return writer.toByteArray ();
	}

	public static String toReadable (byte[] script) throws ValidationException
	{
		List<ScriptFormat.Token> tokens = null;
		try
		{
			tokens = parse (script);
		}
		catch ( Exception e )
		{
			return "0x" + ByteUtils.toHex (script);
		}
		StringBuffer b = new StringBuffer ();
		boolean first = true;
		for ( ScriptFormat.Token token : tokens )
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
					// TODO: this works only for 1 byte length
					b.append ("0x" + ByteUtils.toHex (new byte[] { (byte) (token.op.o & 0xff) }) + ByteUtils.toHex (token.data));
				}
				else
				{
					b.append ("0x0");
				}
			}
			else
			{
				b.append (token.op.toString ());
			}
		}
		return b.toString ();
	}

	public static boolean isPayToScriptHash (byte[] script)
	{
		try
		{
			List<ScriptFormat.Token> parsed = parse (script);
			return parsed.size () == 3 && parsed.get (0).op == ScriptFormat.Opcode.OP_HASH160 && (parsed.get (1).data != null && parsed.get (1).op.o <= 75)
					&& parsed.get (1).data.length == 20 && parsed.get (2).op == ScriptFormat.Opcode.OP_EQUAL;
		}
		catch ( ValidationException e )
		{
			return false;
		}
	}

	public static boolean isPayToKey (byte[] script)
	{
		try
		{
			List<ScriptFormat.Token> parsed = parse (script);
			return parsed.size () == 2 && parsed.get (0).data != null && parsed.get (0).data.length >= 33 && parsed.get (0).data.length <= 120
					&& parsed.get (1).op == ScriptFormat.Opcode.OP_CHECKSIG;
		}
		catch ( ValidationException e )
		{
			return false;
		}
	}

	public static boolean isPayToAddress (byte[] script)
	{
		try
		{
			List<ScriptFormat.Token> parsed = parse (script);
			return parsed.size () == 5 && parsed.get (0).op == ScriptFormat.Opcode.OP_DUP && parsed.get (1).op == ScriptFormat.Opcode.OP_HASH160
					&& parsed.get (2).data != null && parsed.get (2).data.length == 20 && parsed.get (3).op == ScriptFormat.Opcode.OP_EQUALVERIFY
					&& parsed.get (4).op == ScriptFormat.Opcode.OP_CHECKSIG;
		}
		catch ( ValidationException e )
		{
			return false;
		}
	}

	public static Address getAddress (byte[] script)
	{
		try
		{
			List<ScriptFormat.Token> parsed = parse (script);
			if ( parsed.size () == 5 && parsed.get (0).op == ScriptFormat.Opcode.OP_DUP && parsed.get (1).op == ScriptFormat.Opcode.OP_HASH160
					&& parsed.get (2).data != null && parsed.get (2).data.length == 20 && parsed.get (3).op == ScriptFormat.Opcode.OP_EQUALVERIFY
					&& parsed.get (4).op == ScriptFormat.Opcode.OP_CHECKSIG )
			{
				return new Address (Type.COMMON, parsed.get (2).data);
			}
			if ( parsed.size () == 3 && parsed.get (0).op == ScriptFormat.Opcode.OP_HASH160 && (parsed.get (1).data != null && parsed.get (1).op.o <= 75)
					&& parsed.get (1).data.length == 20 && parsed.get (2).op == ScriptFormat.Opcode.OP_EQUAL )
			{
				return new Address (Type.P2SH, parsed.get (1).data);
			}
		}
		catch ( Exception e )
		{
		}
		return null;
	}

	public static boolean isMultiSig (byte[] script)
	{
		try
		{
			List<ScriptFormat.Token> parsed = parse (script);
			int nkeys = -1;
			int nvotes = -1;
			for ( int i = 0; i < parsed.size (); ++i )
			{
				if ( parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIG || parsed.get (i).op == ScriptFormat.Opcode.OP_CHECKMULTISIGVERIFY )
				{
					nkeys = parsed.get (i - 1).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1;
					nvotes = parsed.get (i - nkeys - 2).op.ordinal () - ScriptFormat.Opcode.OP_1.ordinal () + 1;
					break;
				}
			}
			if ( nkeys <= 0 || nkeys > 3 )
			{
				return false;
			}
			if ( parsed.size () != nkeys + 3 )
			{
				return false;
			}
			if ( nvotes < 0 || nvotes > nkeys )
			{
				return false;
			}
		}
		catch ( ValidationException e )
		{
			return false;
		}
		return true;
	}

	public static boolean isStandard (byte[] script)
	{
		return isPayToAddress (script) || isPayToKey (script) || isPayToScriptHash (script) || isMultiSig (script);
	}

	public static byte[] deleteSignatureFromScript (byte[] script, byte[] sig) throws ValidationException
	{
		ScriptFormat.Tokenizer tokenizer = new ScriptFormat.Tokenizer (script);
		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		while ( tokenizer.hashMoreElements () )
		{
			ScriptFormat.Token token = tokenizer.nextToken ();
			if ( token.data != null && token.op.o <= 75 && token.data.length == sig.length )
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

	public static boolean isCanonicalSignature (byte[] sig, int flags) throws ValidationException
	{
		if ( (flags & SCRIPT_VERIFY_STRICTENC) == 0 )
		{
			return true;
		}

		// See https://bitcointalk.org/index.php?topic=8392.msg127623#msg127623
		// A canonical signature exists of: <30> <total len> <02> <len R> <R> <02> <len S> <S> <hashtype>
		// Where R and S are not negative (their first byte has its highest bit not set), and not
		// excessively padded (do not start with a 0 byte, unless an otherwise negative number follows,
		// in which case a single 0 byte is necessary and even required).
		if ( sig.length < 9 )
		{
			throw new ValidationException ("Non-canonical signature: too short");
		}
		if ( sig.length > 73 )
		{
			throw new ValidationException ("Non-canonical signature: too long");
		}
		byte nHashType = (byte) (sig[sig.length - 1] & (~(ScriptFormat.SIGHASH_ANYONECANPAY)));
		if ( nHashType < ScriptFormat.SIGHASH_ALL || nHashType > ScriptFormat.SIGHASH_SINGLE )
		{
			throw new ValidationException ("Non-canonical signature: unknown hashtype byte");
		}
		if ( sig[0] != 0x30 )
		{
			throw new ValidationException ("Non-canonical signature: wrong type");
		}
		if ( sig[1] != sig.length - 3 )
		{
			throw new ValidationException ("Non-canonical signature: wrong length marker");
		}
		int nLenR = sig[3];
		if ( 5 + nLenR - sig.length >= 0 )
		{
			throw new ValidationException ("Non-canonical signature: S length misplaced");
		}
		int nLenS = sig[5 + nLenR];
		if ( (nLenR + nLenS + 7) - sig.length != 0 )
		{
			throw new ValidationException ("Non-canonical signature: R+S length mismatch");
		}
		// const unsigned char *R = &vchSig[4];
		// if (R[-2] != 0x02)
		if ( sig[2] != 0x02 )
		{
			throw new ValidationException ("Non-canonical signature: R value type mismatch");
		}
		if ( nLenR == 0 )
		{
			throw new ValidationException ("Non-canonical signature: R length is zero");
		}
		// if (R[0] & 0x80)
		if ( (sig[4] & 0x80) != 0 )
		{
			throw new ValidationException ("Non-canonical signature: R value negative");
		}
		// if (nLenR > 1 && (R[0] == 0x00) && !(R[1] & 0x80))
		if ( nLenR > 1 && (sig[4] == 0x00) && (sig[5] & 0x80) == 0 )
		{
			throw new ValidationException ("Non-canonical signature: R value excessively padded");
		}
		// const unsigned char *S = &vchSig[6+nLenR];
		// if (S[-2] != 0x02)
		if ( sig[nLenR + 6 - 2] != 0x02 )
		{
			throw new ValidationException ("Non-canonical signature: S value type mismatch");
		}
		if ( nLenS == 0 )
		{
			throw new ValidationException ("Non-canonical signature: S length is zero");
		}
		// if (S[0] & 0x80)
		if ( (sig[6 + nLenR] & 0x80) != 0 )
		{
			throw new ValidationException ("Non-canonical signature: S value negative");
		}
		// if (nLenS > 1 && (S[0] == 0x00) && !(S[1] & 0x80))
		if ( nLenS > 1 && (sig[6 + nLenR] == 0x00) && (sig[6 + nLenR + 1] & 0x80) == 0 )
		{
			throw new ValidationException ("Non-canonical signature: S value excessively padded");
		}

		if ( (flags & SCRIPT_VERIFY_EVEN_S) != 0 )
		{
			// if (S[nLenS-1] & 1)
			if ( (sig[6 + nLenR + nLenS - 1] & 1) != 0 )
			{
				throw new ValidationException ("Non-canonical signature: S value odd");
			}
		}

		return true;
	}
}
