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

import java.util.List;

public interface KeyGenerator
{

	public int getAddressFlag ();

	public int getP2SHAddressFlag ();

	public KeyGenerator createSubKeyGenerator (int sequence) throws ValidationException;

	public void addListener (KeyGeneratorListener listener);

	public ExtendedKey getExtendedKey (int sequence) throws ValidationException;

	public Key getKey (int sequence) throws ValidationException;

	public Key generateNextKey () throws ValidationException;

	public void importKey (Key k);

	public Key getKeyForAddress (String address);

	public List<String> getAddresses () throws ValidationException;

	public ExtendedKey getMaster ();

	public int getNextKeySequence ();

}