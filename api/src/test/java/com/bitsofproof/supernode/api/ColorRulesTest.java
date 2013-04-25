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
package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.bitsofproof.supernode.api.ColorRules.ColoredCoin;

public class ColorRulesTest
{
	private static final String COLOR_RULES_TEST = "colorRules.json";

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

	/**
	 * format of the tests = [ test, ... ]
	 * 
	 * test = { inputs : [{color: "color", value: n}] outputs : [{color: "color", value: n}], valid: true|false, comment: "" }
	 * 
	 * 
	 * @throws JSONException
	 * @throws IOException
	 * 
	 */
	@Test
	public void rulesTest () throws IOException, JSONException
	{
		JSONArray testData = readObjectArray (COLOR_RULES_TEST);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONObject test = testData.getJSONObject (i);
			List<ColoredCoin> inputs = fromJSON (test.getJSONArray ("inputs"));
			List<ColoredCoin> outputs = fromJSON (test.getJSONArray ("outputs"));
			List<ColoredCoin> workingCopy = new ArrayList<ColoredCoin> ();
			for ( ColoredCoin c : outputs )
			{
				ColoredCoin copy = new ColoredCoin ();
				copy.color = null;
				copy.value = c.value;
				workingCopy.add (copy);
			}

			System.out.println ("Checking color scenario: " + test.getString ("comment") + ", expecting: " + test.getBoolean ("valid"));
			ColorRules.colorOutput (inputs, workingCopy);
			Iterator<ColoredCoin> a = outputs.iterator ();
			Iterator<ColoredCoin> b = workingCopy.iterator ();
			boolean valid = true;
			while ( a.hasNext () )
			{
				ColoredCoin one = a.next ();
				ColoredCoin two = b.next ();
				if ( one.color == null || two.color == null )
				{
					if ( !(one.color == null && two.color == null) )
					{
						valid = false;
					}
				}
				else
				{
					valid = valid && one.color.equals (two.color);
				}
			}
			assertTrue (valid == test.getBoolean ("valid"));
		}
	}

	private List<ColoredCoin> fromJSON (JSONArray a) throws JSONException
	{
		List<ColoredCoin> result = new ArrayList<ColoredCoin> ();
		for ( int i = 0; i < a.length (); ++i )
		{
			JSONObject coin = a.getJSONObject (i);
			ColoredCoin c = new ColoredCoin ();
			c.color = coin.optString ("color", null);
			c.value = coin.getLong ("value");
			result.add (c);
		}
		return result;
	}
}
