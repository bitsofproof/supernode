/*
 * Copyright 2013 bits of proof zrt.
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

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.PingMessage;
import com.bitsofproof.supernode.messages.PongMessage;

public class PingPongHandler implements BitcoinMessageListener<PingMessage>
{
	private static final Logger log = LoggerFactory.getLogger (PingPongHandler.class);

	public PingPongHandler (BitcoinNetwork network)
	{
		network.addListener ("ping", this);
	}

	@Override
	public void process (PingMessage pi, BitcoinPeer peer)
	{
		if ( peer.getVersion () > 60000 )
		{
			log.trace ("received ping from " + peer.getAddress ());
			PongMessage po = (PongMessage) peer.createMessage ("pong");
			po.setNonce (pi.getNonce ());
			peer.send (po);
			log.trace ("sent pong to " + peer.getAddress ());
		}
	}
}
