package hu.blummers.bitcoin.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

public class WireFormat {

	public static class Reader {
		private byte[] bytes;
		private int cursor;

		public Reader(byte[] bytes) {
			this.bytes = bytes;
			this.cursor = 0;
		}

		public int getCursor() {
			return cursor;
		}

		public boolean eof() {
			return cursor >= bytes.length;
		}

		public long readUint16() {
			long value = ((bytes[cursor] & 0xFFL) << 0)
					| ((bytes[cursor + 1] & 0xFFL) << 8);
			cursor += 2;
			return value;
		}

		public long readUint32() {
			long value = ((bytes[cursor] & 0xFFL) << 0)
					| ((bytes[cursor + 1] & 0xFFL) << 8)
					| ((bytes[cursor + 2] & 0xFFL) << 16)
					| ((bytes[cursor + 3] & 0xFFL) << 24);
			cursor += 4;
			return value;
		}

		public BigInteger readUint64() {
			return new BigInteger(reverse(readBytes(8)));
		}

		public long readVarInt() {
			int flag = 0xff & bytes[cursor++];
			long value;
			if (flag < 0xfd) {
				value = flag;
			} else if (flag == 0xfd) {
				value = readUint16();
			} else if (flag == 0xfe) {
				value = readUint32();
			} else {
				value = readUint32() | (readUint32() << 32);
			}
			return value;
		}

		public byte[] readBytes(int length) {
			byte[] b = new byte[length];
			System.arraycopy(bytes, cursor, b, 0, length);
			cursor += length;
			return b;
		}

		public Hash readHash() {
			return new Hash(readBytes(32));
		}

		public byte[] readVarBytes() {
			long len = readVarInt();
			return readBytes((int) len);
		}

		public String readString() {
			try {
				return new String(readVarBytes(), "UTF-8");
			} catch (UnsupportedEncodingException e) {
			}
			return null;
		}
		
		public String readZeroDelimitedString (int length)
		{
			byte [] buf = readBytes (length);
			int i;
			for ( i = 0; i < length; ++i )
				if ( buf [i] == 0 )
					break;
			if ( i == 0 )
				return new String ();
			
			byte [] sb = new byte [i];
			System.arraycopy(buf, 0, sb, 0, i);
			try {
				return new String (sb,"UTF-8");
			} catch (UnsupportedEncodingException e) {
				return new String ();
			}
		}

		public Hash hash(int offset, int length) {
			Hash hash = new Hash();
			return hash.hash(bytes, offset, length);
		}
		
		public Hash hash ()
		{
			return hash (0, bytes.length);
		}
		
		public String dump (int offset, int length)
		{
			StringBuffer buf = new StringBuffer(length * 2);
			for (int i = offset; i < length; ++i) {
				byte b = bytes [i];
				String s = Integer.toString(0xFF & b, 16);
				if (s.length() < 2)
					buf.append('0');
				buf.append(s);
			}
			return buf.toString();
		}
		
		public String dump ()
		{
			return dump (0, bytes.length);
		}
	}

	public static class Writer {
		private ByteArrayOutputStream bs;

		public Writer(ByteArrayOutputStream bs) {
			this.bs = bs;
		}

		public byte[] toByteArray() {
			return bs.toByteArray();
		}

		public void writeUint16(long n) {
			bs.write((int) (0xFF & n));
			bs.write((int) (0xFF & (n >> 8)));
		}

		public void writeUint32(long n) {
			bs.write((int) (0xFF & n));
			bs.write((int) (0xFF & (n >> 8)));
			bs.write((int) (0xFF & (n >> 16)));
			bs.write((int) (0xFF & (n >> 24)));
		}

		public void writeUint64(BigInteger n) {
			try {
				byte[] b = reverse(n.toByteArray());
				bs.write(b);
				if (b.length < 8) {
					for (int i = 0; i < 8 - b.length; i++)
						bs.write(0);
				}
			} catch (IOException e) {
			}
		}

		public void writeVarInt(long n) {
			if (isLessThanUnsigned(n, 0xfdl)) {
				bs.write((int) (0xFF & n));
			} else if (isLessThanUnsigned(n, 65536)) {
				bs.write(0xfd);
				writeUint16(n);
			} else if (isLessThanUnsigned(n, 4294967295L)) {
				bs.write(0xfe);
				writeUint32(n);
			} else {
				bs.write(0xff);
				byte[] b = new byte[4];
				b[0] = (byte) (n & 0xff);
				b[1] = (byte) ((n >> 8) & 0xff);
				b[2] = (byte) ((n >> 16) & 0xff);
				b[3] = (byte) ((n >> 24) & 0xff);
				try {
					bs.write(b);
				} catch (IOException e) {
				}
			}
		}

		public void writeBytes(byte[] b) {
			try {
				bs.write(b);
			} catch (IOException e) {
			}
		}

		public void writeHash(Hash h) {
			try {
				bs.write(h.toByteArray());
			} catch (IOException e) {
			}
		}

		public void writeVarBytes(byte[] b) {
			writeVarInt(b.length);
			try {
				bs.write(b);
			} catch (IOException e) {
			}
		}

		public void writeString(String s) {
			try {
				writeVarBytes(s.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
			}
		}
		public void writeZeroDelimitedString (String s, int length)
		{
			try {
				byte [] t = s.getBytes("UTF-8");			
				bs.write(t, 0, Math.min(length-1,t.length));
				for ( int i = 0; i < (length - t.length); ++i)
					bs.write(0);
			} catch (UnsupportedEncodingException e) {
			}
		}
	}

	private static boolean isLessThanUnsigned(long n1, long n2) {
		return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
	}

	// in place reverse using XOR
	private static byte[] reverse(byte[] data) {
		for (int i = 0, j = data.length - 1; i < data.length / 2; i++, j--) {
			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}
}
