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
package com.bitsofproof.supernode.main;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Setup
{

	static public void setup () throws IOException
	{
		String password = null;
		File master = new File ("MASTERPASSWORD");
		if ( master.exists () )
		{
			FileReader reader = new FileReader (master);
			password = new BufferedReader (reader).readLine ();
			reader.close ();
		}
		if ( password == null )
		{
			Console console = System.console ();
			if ( console == null )
			{
				throw new IOException ("unable to obtain console");
			}

			password = new String (console.readPassword ("MASTERPASSWORD: "));
		}
		System.setProperty ("javax.net.ssl.keyStorePassword", password);
		System.setProperty ("javax.net.ssl.trustStorePassword", password);
	}

}
