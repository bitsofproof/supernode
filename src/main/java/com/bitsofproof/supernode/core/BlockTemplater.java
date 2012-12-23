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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class BlockTemplater implements ChainListener, TransactionListener
{
	private final List<TemplateListener> templateListener = new ArrayList<TemplateListener> ();
	private final List<Transaction> validTransactions = Collections.synchronizedList (new ArrayList<Transaction> ());

	private final Block template = new Block ();

	private String coinbaseAddress;

	public void setCoinbaseAddress (String coinbaseAddress)
	{
		this.coinbaseAddress = coinbaseAddress;
	}

	public BlockTemplater (ChainLoader loader, TxHandler txhandler)
	{
		loader.addChainListener (this);
		txhandler.addTransactionListener (this);
		initTemplate ();
	}

	public void addTemplateListener (TemplateListener listener)
	{
		templateListener.add (listener);
	}

	private synchronized void initTemplate ()
	{
		// TODO:missing implementation
		template.setTransactions (new ArrayList<Transaction> ());
	}

	@Override
	public void blockAdded (Blk blk)
	{
		initTemplate ();
		// TODO:missing implementation
	}

	@Override
	public void onTransaction (Tx tx)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		tx.toWire (writer);
		validTransactions.add (Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ())));
	}
}
