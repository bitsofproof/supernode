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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.AlertMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class AlertHandler implements BitcoinMessageListener<AlertMessage>
{
	private static final Logger log = LoggerFactory.getLogger (AlertHandler.class);

	private Chain chain;

	public AlertHandler (BitcoinNetwork network)
	{
		network.addListener ("alert", this);
	}

	@Override
	public void process (AlertMessage am, BitcoinPeer peer) throws Exception
	{
		if ( am.isValidWithKey (chain.getAlertKey ()) )
		{
			log.warn ("ALERT: " + am.getPayload ());
		}
		else
		{
			log.trace ("rejected unauthorized alert. Disconnecting.");
			peer.disconnect ();
		}
	}

	public Chain getChain ()
	{
		return chain;
	}

	public void setChain (Chain chain)
	{
		this.chain = chain;
	}
}
