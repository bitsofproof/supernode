package com.bccapi.api;

import java.io.IOException;
import java.io.Serializable;

import com.bitsofproof.supernode.core.WireFormat;

/**
 * AccountInfo holds information about an account such as the current balance and number of keys monitored. Instances are retrieved from the BCCAPI server by
 * calling {@link Account#getInfo}.
 */
public class AccountInfo implements Serializable
{
	private static final long serialVersionUID = 4995282304210998901L;
	private final int _keys;
	private final long _availableBalance;
	private final long _estimatedBalance;

	public AccountInfo (int keys, long availableBalance, long estimatedBalance)
	{
		_keys = keys;
		_availableBalance = availableBalance;
		_estimatedBalance = estimatedBalance;
	}

	/**
	 * Get the number of keys being monitored by the BCCAPI server.
	 * 
	 * @return The number of keys being monitored by the BCCAPI server.
	 */
	public int getKeys ()
	{
		return _keys;
	}

	/**
	 * Get the balance available for spending. This includes all unspent transactions sent to one of your addresses which are confirmed by at least one block,
	 * plus any change sent back to your account from one of your own addresses. This value is what you would expect to see in a UI.
	 * 
	 * @return The balance available for spending.
	 */
	public long getAvailableBalance ()
	{
		return _availableBalance;
	}

	/**
	 * Get the estimated balance of this account. This is the balance available for spending plus unconfirmed the amount in transit your account.
	 * <p>
	 * If you subtract the available balance from the estimated balance you get the number of unconfirmed bitcoins in transit to you.
	 * <p>
	 * 
	 * @return The estimated balance of this account.
	 */
	public long getEstimatedBalance ()
	{
		return _estimatedBalance;
	}

	@Override
	public String toString ()
	{
		return "Keys: " + getKeys () + " Balance: " + getAvailableBalance () + " (" + getEstimatedBalance () + ")";
	}

	public static AccountInfo fromWire (WireFormat.Reader reader)
	{
		int keys = (int) reader.readUint32 ();
		long available = reader.readUint64 ();
		long estimated = reader.readUint64 ();
		return new AccountInfo (keys, available, estimated);
	}

	public void toWire (WireFormat.Writer writer) throws IOException
	{
		writer.writeUint32 (_keys);
		writer.writeUint64 (_availableBalance);
		writer.writeUint64 (_estimatedBalance);
	}

}
