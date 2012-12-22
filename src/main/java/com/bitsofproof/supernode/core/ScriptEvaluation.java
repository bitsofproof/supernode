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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import com.bitsofproof.supernode.api.ECKeyPair;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class ScriptEvaluation
{
	private Stack<byte[]> stack = new Stack<byte[]> ();
	private final Stack<byte[]> alt = new Stack<byte[]> ();
	private final Tx tx;
	private TxOut source;
	private int inr;

	private void pushInt (long n) throws ValidationException
	{
		stack.push (new ScriptFormat.Number (n).toByteArray ());
	}

	private long popInt () throws ValidationException
	{
		return new ScriptFormat.Number (stack.pop ()).intValue ();
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

	public ScriptEvaluation ()
	{
		this.tx = null;
	}

	public ScriptEvaluation (Tx tx, int inr)
	{
		this.tx = tx;
		this.inr = inr;
		this.source = tx.getInputs ().get (inr).getSource ();
	}

	public ScriptEvaluation (Tx tx, int inr, TxOut source)
	{
		this.tx = tx;
		this.inr = inr;
		this.source = source;
	}

	@SuppressWarnings ("unchecked")
	public boolean evaluate (boolean production) throws ValidationException
	{
		Stack<byte[]> copy = new Stack<byte[]> ();

		byte[] s1 = tx.getInputs ().get (inr).getScript ();
		byte[] s2 = source.getScript ();

		if ( !evaluateSingleScript (s1) )
		{
			return false;
		}
		boolean psh = ScriptFormat.isPayToScriptHash (s1);
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
			if ( !ScriptFormat.isPushOnly (s1) )
			{
				throw new ValidationException ("input script for PTH should be push only.");
			}
			stack = copy;
			byte[] script = stack.pop ();
			if ( production )
			{
				if ( !ScriptFormat.isStandard (script) )
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
		ScriptFormat.Tokenizer tokenizer = new ScriptFormat.Tokenizer (script);
		int codeseparator = 0;

		Stack<Boolean> ignoreStack = new Stack<Boolean> ();
		ignoreStack.push (false);

		try
		{
			while ( tokenizer.hashMoreElements () )
			{
				ScriptFormat.Token token = tokenizer.nextToken ();
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
							if ( token.op == ScriptFormat.Opcode.OP_EQUALVERIFY )
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
							sts = ScriptFormat.deleteSignatureFromScript (sts, sig);

							pushInt (validateSignature (pubkey, sig, sts) ? 1 : 0);
							if ( token.op == ScriptFormat.Opcode.OP_CHECKSIGVERIFY )
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
								sts = ScriptFormat.deleteSignatureFromScript (sts, sigs[i]);
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

		if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_NONE )
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
		else if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_SINGLE )
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
		if ( (hashType & ScriptFormat.SIGHASH_ANYONECANPAY) != 0 )
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
		try
		{
			return ECKeyPair.verify (hash, sig, pubkey);
		}
		catch ( Exception e )
		{
			return false;
		}
	}
}
