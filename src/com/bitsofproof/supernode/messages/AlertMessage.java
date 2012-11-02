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

import java.io.UnsupportedEncodingException;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class AlertMessage extends BitcoinPeer.Message
{
	private byte[] payload;
	private byte[] signature;
	private final byte[] alertKey;

	public AlertMessage (BitcoinPeer bitcoinPeer, byte[] alertKey)
	{
		bitcoinPeer.super ("alert");
		this.alertKey = alertKey;
	}

	@Override
	public void toWire (Writer writer)
	{
	}

	@Override
	public void fromWire (Reader reader)
	{
		payload = reader.readVarBytes ();
		signature = reader.readVarBytes ();
	}

	@Override
	public void validate () throws ValidationException
	{
		if ( !ECKeyPair.verify (Hash.hash (payload, 0, payload.length), signature, alertKey) )
		{
			throw new ValidationException ("Unauthorized alert");
		}
	}

	public String getPayload ()
	{
		try
		{
			return new String (payload, "US-ASCII");
		}
		catch ( UnsupportedEncodingException e )
		{
			return null;
		}
	}

	public void setPayload (String payload)
	{
		try
		{
			this.payload = payload.getBytes ("US-ASCII");
		}
		catch ( UnsupportedEncodingException e )
		{
		}
	}

	public byte[] getSignature ()
	{
		return signature;
	}

	public void setSignature (byte[] signature)
	{
		this.signature = signature;
	}

}
