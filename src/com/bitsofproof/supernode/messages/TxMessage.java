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
package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;
import com.bitsofproof.supernode.model.Tx;

public class TxMessage extends BitcoinPeer.Message
{
	Tx tx = new Tx ();

	public TxMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("tx");
	}

	public Tx getTx ()
	{
		return tx;
	}

	public void setTx (Tx tx)
	{
		this.tx = tx;
	}

	@Override
	public void validate () throws ValidationException
	{
		tx.basicValidation ();
	}

	@Override
	public void toWire (Writer writer)
	{
		tx.toWire (writer);
	}

	@Override
	public void fromWire (Reader reader)
	{
		tx.fromWire (reader);
	}

}
