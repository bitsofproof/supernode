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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class WireFormat
{

	public static class Address
	{
		public long time;
		public long services;
		public InetAddress address;
		public long port;
	}

	public static class Reader
	{
		private final byte[] bytes;
		private int cursor;

		public Reader (byte[] bytes)
		{
			this.bytes = bytes;
			this.cursor = 0;
		}

		public int getCursor ()
		{
			return cursor;
		}

		public boolean eof ()
		{
			return cursor >= bytes.length;
		}

		public byte[] readRest ()
		{
			byte[] rest = new byte[bytes.length - cursor];
			System.arraycopy (bytes, cursor, rest, 0, bytes.length - cursor);
			cursor = bytes.length;
			return rest;
		}

		public int readByte ()
		{
			return bytes[cursor++] & 0xff;
		}

		public long readUint16 ()
		{
			long value = ((bytes[cursor] & 0xFFL) << 0) | ((bytes[cursor + 1] & 0xFFL) << 8);
			cursor += 2;
			return value;
		}

		public long readUint32 ()
		{
			long value =
					((bytes[cursor] & 0xFFL) << 0) | ((bytes[cursor + 1] & 0xFFL) << 8) | ((bytes[cursor + 2] & 0xFFL) << 16)
							| ((bytes[cursor + 3] & 0xFFL) << 24);
			cursor += 4;
			return value;
		}

		public long readUint64 ()
		{
			return new BigInteger (ByteUtils.reverse (readBytes (8))).longValue ();
		}

		public long readVarInt ()
		{
			int flag = 0xff & bytes[cursor++];
			long value;
			if ( flag < 0xfd )
			{
				value = flag;
			}
			else if ( flag == 0xfd )
			{
				value = readUint16 ();
			}
			else if ( flag == 0xfe )
			{
				value = readUint32 ();
			}
			else
			{
				value = readUint32 () | (readUint32 () << 32);
			}
			return value;
		}

		public void skipBytes (int length)
		{
			cursor += length;
		}

		public byte[] readBytes (int length)
		{
			byte[] b = new byte[length];
			System.arraycopy (bytes, cursor, b, 0, length);
			cursor += length;
			return b;
		}

		public Hash readHash ()
		{
			return new Hash (readBytes (32));
		}

		public byte[] readVarBytes ()
		{
			long len = readVarInt ();
			return readBytes ((int) len);
		}

		public String readString ()
		{
			try
			{
				return new String (readVarBytes (), "UTF-8");
			}
			catch ( UnsupportedEncodingException e )
			{
			}
			return null;
		}

		public String readZeroDelimitedString (int length)
		{
			byte[] buf = readBytes (length);
			int i;
			for ( i = 0; i < length; ++i )
			{
				if ( buf[i] == 0 )
				{
					break;
				}
			}
			if ( i == 0 )
			{
				return new String ();
			}

			byte[] sb = new byte[i];
			System.arraycopy (buf, 0, sb, 0, i);
			try
			{
				return new String (sb, "UTF-8");
			}
			catch ( UnsupportedEncodingException e )
			{
				return new String ();
			}
		}

		public Hash hash (int offset, int length)
		{
			return new Hash (Hash.hash (bytes, offset, length));
		}

		public Hash hash ()
		{
			return hash (0, bytes.length);
		}

		public String dump (int offset, int length)
		{
			StringBuffer buf = new StringBuffer (length * 2);
			for ( int i = offset; i < length; ++i )
			{
				byte b = bytes[i];
				String s = Integer.toString (0xFF & b, 16);
				if ( s.length () < 2 )
				{
					buf.append ('0');
				}
				buf.append (s);
			}
			return buf.toString ();
		}

		public String dump ()
		{
			return dump (0, bytes.length);
		}

		public Address readAddress (long version, boolean versionMessage)
		{
			Address address = new Address ();
			if ( !versionMessage && version >= 31402 )
			{
				address.time = readUint32 ();
			}
			address.services = readUint64 ();
			byte[] a = readBytes (16);
			try
			{
				address.address = InetAddress.getByAddress (a);
			}
			catch ( UnknownHostException e )
			{
			}
			address.port = ((bytes[cursor + 1] & 0xFFL) << 0) | ((bytes[cursor] & 0xFFL) << 8);
			cursor += 2;
			return address;
		}
	}

	public static class Writer
	{
		private final ByteArrayOutputStream bs;

		public Writer ()
		{
			this.bs = new ByteArrayOutputStream ();
		}

		public Writer (ByteArrayOutputStream bs)
		{
			this.bs = bs;
		}

		public byte[] toByteArray ()
		{
			return bs.toByteArray ();
		}

		public void writeByte (int n)
		{
			bs.write (n);
		}

		public void writeUint16 (long n)
		{
			bs.write ((int) (0xFF & n));
			bs.write ((int) (0xFF & (n >> 8)));
		}

		public void writeUint32 (long n)
		{
			bs.write ((int) (0xFF & n));
			bs.write ((int) (0xFF & (n >> 8)));
			bs.write ((int) (0xFF & (n >> 16)));
			bs.write ((int) (0xFF & (n >> 24)));
		}

		public void writeUint64 (long n)
		{
			try
			{
				BigInteger bi = BigInteger.valueOf (n);
				byte[] b = ByteUtils.reverse (bi.toByteArray ());
				bs.write (b);
				if ( b.length < 8 )
				{
					for ( int i = 0; i < 8 - b.length; i++ )
					{
						bs.write (0);
					}
				}
			}
			catch ( IOException e )
			{
			}
		}

		public void writeVarInt (long n)
		{
			if ( isLessThanUnsigned (n, 0xfdl) )
			{
				bs.write ((int) (0xFF & n));
			}
			else if ( isLessThanUnsigned (n, 65536) )
			{
				bs.write (0xfd);
				writeUint16 (n);
			}
			else if ( isLessThanUnsigned (n, 4294967295L) )
			{
				bs.write (0xfe);
				writeUint32 (n);
			}
			else
			{
				bs.write (0xff);
				byte[] b = new byte[4];
				b[0] = (byte) (n & 0xff);
				b[1] = (byte) ((n >> 8) & 0xff);
				b[2] = (byte) ((n >> 16) & 0xff);
				b[3] = (byte) ((n >> 24) & 0xff);
				try
				{
					bs.write (b);
				}
				catch ( IOException e )
				{
				}
			}
		}

		public void writeBytes (byte[] b)
		{
			try
			{
				bs.write (b);
			}
			catch ( IOException e )
			{
			}
		}

		public void writeHash (Hash h)
		{
			try
			{
				bs.write (h.toByteArray ());
			}
			catch ( IOException e )
			{
			}
		}

		public void writeVarBytes (byte[] b)
		{
			writeVarInt (b.length);
			try
			{
				bs.write (b);
			}
			catch ( IOException e )
			{
			}
		}

		public void writeString (String s)
		{
			try
			{
				writeVarBytes (s.getBytes ("UTF-8"));
			}
			catch ( UnsupportedEncodingException e )
			{
			}
		}

		public void writeZeroDelimitedString (String s, int length)
		{
			byte[] str = new byte[length];
			for ( int i = 0; i < s.length () && i < (length - 1); ++i )
			{
				str[i] = (byte) (s.codePointAt (i) & 0xFF);
			}
			writeBytes (str);
		}

		public void writeAddress (Address address, long version, boolean versionMessage)
		{
			if ( !versionMessage && version > 31402 )
			{
				writeUint32 (address.time);
			}
			writeUint64 (address.services);
			byte[] a = address.address.getAddress ();
			if ( a.length == 4 )
			{
				byte[] prefix = new byte[10];
				writeBytes (prefix);
				writeUint16 (0xffffl);
			}
			writeBytes (a);
			bs.write ((int) (0xFF & (address.port >> 8)));
			bs.write ((int) (0xFF & address.port));
		}
	}

	private static boolean isLessThanUnsigned (long n1, long n2)
	{
		return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
	}
}
