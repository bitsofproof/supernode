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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;

import com.bitsofproof.supernode.api.ScriptFormat;

public class ScriptTest
{
	private final String SCRIPT_VALID = "script_valid.json";
	private final String SCRIPT_INVALID = "script_invalid.json";

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
	public void bitcoindValidScriptTest () throws IOException, JSONException
	{
		JSONArray testData = readObjectArray (SCRIPT_VALID);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONArray test = testData.getJSONArray (i);
			ScriptEvaluation script = new ScriptEvaluation ();
			System.out.println ("valid script " + test.toString ());
			assertTrue (script.evaluateScripts (false, ScriptFormat.fromReadable (test.get (0).toString ()),
					ScriptFormat.fromReadable (test.get (1).toString ())));
		}
	}

	@Test
	public void bitcoindInvalidScriptTest () throws IOException, JSONException
	{
		JSONArray testData = readObjectArray (SCRIPT_INVALID);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONArray test = testData.getJSONArray (i);
			ScriptEvaluation script = new ScriptEvaluation ();
			System.out.println ("invalid script " + test.toString ());
			assertFalse (script.evaluateScripts (false, ScriptFormat.fromReadable (test.get (0).toString ()),
					ScriptFormat.fromReadable (test.get (1).toString ())));
		}
	}
}
