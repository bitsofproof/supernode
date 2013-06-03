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

import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.model.Tx;

public class TransactionValidationException extends ValidationException
{
	private static final long serialVersionUID = 1L;
	private final Tx tx;
	private final int in;

	public TransactionValidationException (String message, Tx tx, int in)
	{
		super (message);
		this.tx = tx;
		this.in = in;
	}

	public TransactionValidationException (Exception e, Tx tx)
	{
		super (e);
		this.tx = tx;
		this.in = -1;
	}

	public TransactionValidationException (Exception e, Tx tx, int in)
	{
		super (e);
		this.tx = tx;
		this.in = in;
	}

	public TransactionValidationException (String message, Tx tx)
	{
		super (message);
		this.tx = tx;
		this.in = -1;
	}

	public Tx getTx ()
	{
		return tx;
	}

	public int getIn ()
	{
		return in;
	}
}
