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
package com.bitsofproof.supernode.servlet;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import com.bccapi.api.APIException;
import com.bccapi.api.AccountInfo;
import com.bccapi.api.AccountStatement;
import com.bccapi.api.SendCoinForm;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.main.Supernode;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.plugins.bccapi.BCCAPI;

@Controller
@RequestMapping ("/bccapi")
public class NativeBCCAPI
{
	private static final String API_VERSION = "1";

	private final BCCAPI api;

	public NativeBCCAPI ()
	{
		api = Supernode.getApplicationContext ().getBean (BCCAPI.class);
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

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/getAccountInfo", produces = "application/octet-stream")
	public @ResponseBody
	byte[] getAccountInfo (WebRequest request) throws IOException, APIException
	{
		AccountInfo ai = api.getAccountInfo (request.getParameter ("sessionId"));
		if ( ai == null )
		{
			return null;
		}
		WireFormat.Writer writer = new WireFormat.Writer ();
		ai.toWire (writer);
		return writer.toByteArray ();
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/getAccountStatement", produces = "application/octet-stream")
	public @ResponseBody
	byte[] getAccountStatement (@RequestParam (value = "index", required = true) int startIndex, @RequestParam (value = "count", required = true) int count,
			WebRequest request) throws IOException, APIException
	{
		AccountStatement as = api.getAccountStatement (request.getParameter ("sessionId"), startIndex, count);
		if ( as == null )
		{
			return null;
		}
		WireFormat.Writer writer = new WireFormat.Writer ();
		as.toWire (writer);
		return writer.toByteArray ();
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/getRecentTransactionSummary", produces = "application/octet-stream")
	public @ResponseBody
	byte[] getRecentTransactionSummary (@RequestParam (value = "count", required = true) int count, WebRequest request) throws IOException, APIException
	{
		AccountStatement as = api.getRecentTransactionSummary (request.getParameter ("sessionId"), count);
		if ( as == null )
		{
			return null;
		}
		WireFormat.Writer writer = new WireFormat.Writer ();
		as.toWire (writer);
		return writer.toByteArray ();
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/addKeyToWallet")
	public void addKeyToWallet (@RequestParam (value = "key", required = true) String publicKey, WebRequest request) throws IOException, APIException
	{
		api.addKeyToWallet (request.getParameter ("sessionId"), ByteUtils.fromHex (publicKey));
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/getSendCoinForm", produces = "application/octet-stream")
	public @ResponseBody
	byte[] getSendCoinForm (WebRequest request, @RequestBody byte[] body) throws APIException, IOException
	{
		String sessionId = request.getParameter ("sessionId");
		WireFormat.Reader reader = new WireFormat.Reader (body);
		String address = reader.readString ();
		long amount = reader.readUint64 ();
		long fee = reader.readUint64 ();
		SendCoinForm f = api.getSendCoinForm (sessionId, address, amount, fee);
		if ( f == null )
		{
			return null;
		}
		WireFormat.Writer writer = new WireFormat.Writer ();
		f.toWire (writer);
		return writer.toByteArray ();
	}

	@RequestMapping (method = { RequestMethod.GET }, value = "/" + API_VERSION + "/submitTransaction", produces = "application/octet-stream")
	public void submitTransaction (WebRequest request, @RequestBody byte[] body) throws IOException, APIException
	{
		String sessionId = request.getParameter ("sessionId");
		WireFormat.Reader reader = new WireFormat.Reader (body);
		Tx tx = new Tx ();
		tx.fromWire (reader);
		api.submitTransaction (sessionId, tx);
	}
}
