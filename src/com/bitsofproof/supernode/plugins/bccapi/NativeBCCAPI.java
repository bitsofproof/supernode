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
package com.bitsofproof.supernode.plugins.bccapi;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.bccapi.api.APIException;
import com.bccapi.api.AccountInfo;
import com.bccapi.api.AccountStatement;
import com.bccapi.api.Network;
import com.bccapi.api.SendCoinForm;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.model.Tx;

@Controller
public class NativeBCCAPI
{
	private static final String API_VERSION = "1";

	private BCCAPI api;

	public void setApi (BCCAPI api)
	{
		this.api = api;
	}

	public Network getNetwork ()
	{
		return api.getNetwork ();
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/getLoginChallenge", produces = "application/octet-stream")
	public @ResponseBody
	byte[] getLoginChallenge (@RequestParam (value = "key", required = true) String accountPublicKey) throws IOException, APIException
	{
		return api.getLoginChallenge (ByteUtils.fromHex (accountPublicKey));
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/login", produces = "application/octet-stream")
	public @ResponseBody
	String login (@RequestParam (value = "key", required = true) String accountPublicKey,
			@RequestParam (value = "response", required = true) String challengeResponse) throws IOException, APIException
	{
		return api.login (ByteUtils.fromHex (accountPublicKey), ByteUtils.fromHex (challengeResponse));
	}

	public AccountInfo getAccountInfo (String sessionID) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public AccountStatement getAccountStatement (String sessionID, int startIndex, int count) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public AccountStatement getRecentTransactionSummary (String sessionID, int count) throws IOException, APIException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/addKeyToWallet", produces = "application/octet-stream")
	public void addKeyToWallet (@RequestParam (value = "sessionId", required = true) String sessionID,
			@RequestParam (value = "key", required = true) String publicKey) throws IOException, APIException
	{
		api.addKeyToWallet (sessionID, ByteUtils.fromHex (publicKey));
	}

	public SendCoinForm getSendCoinForm (String sessionID, String receivingAddressString, long amount, long fee) throws APIException, IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void submitTransaction (String sessionID, Tx tx) throws IOException, APIException
	{
		// TODO Auto-generated method stub

	}

}
