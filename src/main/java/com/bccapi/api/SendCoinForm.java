package com.bccapi.api;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxOut;

/**
 * Contains all the information necessary to sign and verify a transaction that sends bitcoins to a bitcoin address. Before signing a transaction the client
 * should validate the amounts being signed and the receivers. This can be done using the {@link SendCoinFormValidator}.
 */
public class SendCoinForm implements Serializable
{
	private static final long serialVersionUID = -2620065938616820193L;
	private final Tx _tx;
	private final List<TxOut> _txFunding;
	private final List<Integer> _keyIndexes;

	public SendCoinForm (Tx tx, List<Integer> keyIndexes, List<TxOut> txFunding)
	{
		_tx = tx;
		_txFunding = txFunding;
		_keyIndexes = keyIndexes;
	}

	/**
	 * Get the transaction of this form.
	 * 
	 * @return The transaction of this form.
	 */
	public Tx getTransaction ()
	{
		return _tx;
	}

	/**
	 * Get the list of private key indexes that should sign the inputs of the transactions.
	 * 
	 * @return The list of private key indexes that should sign the inputs of the transactions.
	 */
	public List<Integer> getKeyIndexes ()
	{
		return _keyIndexes;
	}

	/**
	 * Get the list of transaction outputs that fund the inputs of the transaction. This allows the client to verify how many BTC is being sent.
	 * 
	 * @return The list of transaction outputs that fund the inputs of the transaction.
	 */
	public List<TxOut> getFunding ()
	{
		return _txFunding;
	}

	/**
	 * Deserialize a {@link SendCoinForm} from a {@link DataInputStream}.
	 * 
	 * @param reader
	 * @return A {@link SendCoinForm}.
	 * @throws IOException
	 */
	public static SendCoinForm fromStream (WireFormat.Reader reader) throws IOException
	{
		Tx tx = new Tx ();
		tx.fromWire (reader);
		int indexes = (int) reader.readVarInt ();
		List<Integer> keyIndexes = new ArrayList<Integer> (indexes);
		for ( int i = 0; i < indexes; i++ )
		{
			keyIndexes.add ((int) reader.readVarInt ());
		}
		int fundLength = (int) reader.readVarInt ();
		List<TxOut> txFunding = new ArrayList<TxOut> (fundLength);
		for ( int i = 0; i < fundLength; i++ )
		{
			TxOut txout = new TxOut ();
			txout.fromWire (reader);
			txFunding.add (txout);
		}
		return new SendCoinForm (tx, keyIndexes, txFunding);
	}

	/**
	 * Serialize a {@link SendCoinForm} instance.
	 * 
	 * @param writer
	 * @throws IOException
	 */
	public void toWire (WireFormat.Writer writer) throws IOException
	{
		_tx.toWire (writer);
		writer.writeVarInt (_keyIndexes.size ());
		for ( int index : _keyIndexes )
		{
			writer.writeVarInt (index);
		}
		writer.writeVarInt (_txFunding.size ());
		for ( TxOut out : _txFunding )
		{
			out.toWire (writer);
		}
	}

}
