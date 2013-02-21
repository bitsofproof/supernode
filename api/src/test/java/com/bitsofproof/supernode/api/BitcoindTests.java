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
package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;

/**
 * These are the unit tests from bitcoind that are supported with test data in json format
 */
public class BitcoindTests
{
	private static final String BASE58_ENCODE = "base58_encode_decode.json";

	private JSONArray readObjectArray (String resource) throws IOException, JSONException
	{
		InputStream input = this.getClass ().getResource ("/" + resource).openStream ();
		StringBuffer content = new StringBuffer ();
		byte[] buffer = new byte[1024];
		int len;
		while ( (len = input.read (buffer)) > 0 )
		{
			byte[] s = new byte[len];
			System.arraycopy (buffer, 0, s, 0, len);
			content.append (new String (buffer, "UTF-8"));
		}
		return new JSONArray (content.toString ());
	}

	@Test
	public void base58encode () throws IOException, JSONException, ValidationException
	{
		JSONArray testData = readObjectArray (BASE58_ENCODE);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONArray test = testData.getJSONArray (i);
			assertTrue (AddressConverter.toBase58 (ByteUtils.fromHex (test.getString (0))).equals (test.get (1)));
			assertTrue (ByteUtils.toHex (AddressConverter.fromBase58 (test.getString (1))).equals (test.get (0)));
		}
	}
}
