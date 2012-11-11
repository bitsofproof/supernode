package com.bccapi.api;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.WireFormat;

/**
 * AccountStatement contains a detailed list of transaction records that send bitcoins to and from a wallet account and the accounts current balance. Instances
 * are retrieved from the BCCAPI server by calling {@link Account#getStatement}.
 */
public class AccountStatement implements Serializable
{
	private static final long serialVersionUID = -4240314258838442911L;

	/**
	 * Represents a transaction that occurred on an account.
	 */
	public static class Record
	{

		/**
		 * Specifies the type of a {@link Record}.
		 */
		public enum Type
		{

			/**
			 * The record represents a transaction that spends coins from the account.
			 */
			Sent,
			/**
			 * The record represents a transaction that receives coins to the account.
			 */
			Received,
			/**
			 * The record represents a transaction that sends coins from the account to the account itself.
			 */
			SentToSelf;

			private static final int SENT = 1;
			private static final int RECEIVED = 2;
			private static final int SENTTOSELF = 3;

			/**
			 * Deserialize a {@link Type} from a reader.
			 * 
			 * @param stream
			 *            The {@link DataInputStream} to deserialize from.
			 * @return A {@link Type}.
			 * @throws IOException
			 */
			public static Type fromStream (WireFormat.Reader reader) throws IOException
			{
				int type = reader.readByte ();
				switch ( type )
				{
					case SENT:
						return Type.Sent;
					case RECEIVED:
						return Type.Received;
					case SENTTOSELF:
						return Type.SentToSelf;
					default:
						return Type.Sent;
				}
			}

			/**
			 * Serialize this {@link Type} instance.
			 * 
			 * @param out
			 *            writer to serialize to.
			 * @throws IOException
			 */
			public void toStream (WireFormat.Writer writer) throws IOException
			{
				if ( this == Type.Sent )
				{
					writer.writeByte (SENT);
				}
				else if ( this == Type.Received )
				{
					writer.writeByte (RECEIVED);
				}
				else if ( this == Type.SentToSelf )
				{
					writer.writeByte (SENTTOSELF);
				}
			}
		}

		private final int _index;
		private final int _confirmations;
		private final long _date;
		private final String _addresses;
		private final long _amount;
		private final Type _type;

		public Record (int index, int confirmations, long date, Type type, String addresses, long amount)
		{
			_index = index;
			_confirmations = confirmations;
			_date = date;
			_addresses = addresses;
			_amount = amount;
			_type = type;
		}

		/**
		 * Get the unique index of this record for the given account.
		 * 
		 * @return The unique index of this record for the given account.
		 */
		public int getIndex ()
		{
			return _index;
		}

		/**
		 * Get the number of confirmations on the transaction that this record represents.
		 * 
		 * @return The number of confirmations on the transaction that this record represents.
		 */
		public int getConfirmations ()
		{
			return _confirmations;
		}

		/**
		 * Get the date at which the transaction occurred.
		 * 
		 * @return The date at which the transaction occurred.
		 */
		public long getDate ()
		{
			return _date;
		}

		/**
		 * Get the addresses of the transaction that this record represents.
		 * <p>
		 * If this record represents a transaction that spends coins the addresses will be a comma separated list of the recipient's receiving addresses.
		 * <p>
		 * If this record represents a transaction that receives coins the addresses will be a comma separated list of your receiving addresses.
		 * <p>
		 * If this record represents a transaction that was sent to yourself the addresses will be a comma separated list of your receiving addresses.
		 * 
		 * @return Addresses of this transaction.
		 */
		public String getAddresses ()
		{
			return _addresses;
		}

		/**
		 * Get the amount of bitcoins in satoshis going to or from the account for this record. A positive value indicates that coins were sent to the account.
		 * A negative value indicates that coins were spent. Note that the amount may be negative for records of the type {@link Type#SentToSelf} as the amount
		 * also includes any transaction fee paid.
		 * 
		 * @return The amount going to or from the account
		 */
		public long getAmount ()
		{
			return _amount;
		}

		/**
		 * Get the type of the record.
		 * <p>
		 * The type {@link Type#Sent} is used for records that represent a transaction that spends coins from your wallet.
		 * <p>
		 * The type {@link Type#Received} is used for records that represent a transaction that sends coins from your wallet.
		 * <p>
		 * The type {@link Type#SentToSelf} is use for records that represent a transaction that sends coins from your wallet to itself.
		 * <p>
		 * 
		 * @return The type of the record.
		 */
		public Type getType ()
		{
			return _type;
		}

		/**
		 * Deserialize a {@link Record} from a {@link DataInputStream}.
		 * 
		 * @param reader
		 * @return A {@link Record}.
		 * @throws IOException
		 */
		public static Record fromStream (WireFormat.Reader reader) throws IOException
		{
			int recordIndex = (int) reader.readVarInt ();
			int confirmations = (int) reader.readVarInt ();
			long date = reader.readUint64 ();
			Type type = Type.fromStream (reader);
			byte[] bytes = reader.readBytes ((int) reader.readVarInt ());
			String addresses = new String (bytes, "US-ASCII");
			long credit = reader.readUint64 ();
			return new Record (recordIndex, confirmations, date, type, addresses, credit);
		}

		/**
		 * Serialize this {@link Record} instance.
		 * 
		 * @param writer
		 * @throws IOException
		 */
		public void toWire (WireFormat.Writer writer) throws IOException
		{
			writer.writeVarInt (_index);
			writer.writeVarInt (_confirmations);
			writer.writeUint64 (_date);
			_type.toStream (writer);
			byte[] bytes = _addresses.getBytes ();
			writer.writeVarInt (bytes.length);
			writer.writeBytes (bytes);
			writer.writeUint64 (_amount);
		}

	}

	private final AccountInfo _info;
	private final int _totalRecords;
	private final List<Record> _records;

	public AccountStatement (AccountInfo info, int totalRecords, List<Record> records)
	{
		_info = info;
		_totalRecords = totalRecords;
		_records = records;
	}

	/**
	 * Get the {@link AccountInfo} for this statement.
	 * 
	 * @return The {@link AccountInfo} for this statement.
	 */
	public AccountInfo getInfo ()
	{
		return _info;
	}

	/**
	 * Get the total number of records registered for this account.
	 * 
	 * @return The total number of records registered for this account.
	 */
	public int getTotalRecordCount ()
	{
		return _totalRecords;
	}

	/**
	 * Get the list of records for this statement.
	 * 
	 * @return The list of records for this statement.
	 */
	public List<Record> getRecords ()
	{
		return _records;
	}

	/**
	 * Deserialize an {@link AccountStatement} from a {@link DataInputStream}.
	 * 
	 * @param reader
	 * @return A {@link AccountStatement}.
	 * @throws IOException
	 */
	public static AccountStatement fromStream (WireFormat.Reader reader) throws IOException
	{
		AccountInfo info = AccountInfo.fromWire (reader);
		int totalRecords = (int) reader.readVarInt ();
		int numRecords = (int) reader.readVarInt ();
		List<Record> records = new ArrayList<Record> (numRecords);
		for ( int i = 0; i < numRecords; i++ )
		{
			records.add (Record.fromStream (reader));
		}
		return new AccountStatement (info, totalRecords, records);
	}

	/**
	 * Serialize this {@link AccountStatement} instance.
	 * 
	 * @param writer
	 * @throws IOException
	 */
	public void toWire (WireFormat.Writer writer) throws IOException
	{
		_info.toWire (writer);
		writer.writeVarInt (_totalRecords);
		writer.writeVarInt (_records.size ());
		for ( Record r : _records )
		{
			r.toWire (writer);
		}
	}

}
