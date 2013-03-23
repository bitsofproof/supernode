/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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

import com.google.protobuf.ByteString;

public class Color
{
	private TransactionOutput root;
	private String terms;
	private long unit;
	private int expiryHeight;
	private byte[] signature;

	public BCSAPIMessage.Color toProtobuf ()
	{
		BCSAPIMessage.Color.Builder builder = BCSAPIMessage.Color.newBuilder ();
		builder.setRoot (root.toProtobuf ());
		builder.setTerms (terms);
		builder.setUnit (unit);
		builder.setExpiryHeight (expiryHeight);
		builder.setSignature (ByteString.copyFrom (signature));
		return builder.build ();
	}

	public static Color fromProtobuf (BCSAPIMessage.Color po)
	{
		Color color = new Color ();
		color.setRoot (TransactionOutput.fromProtobuf (po.getRoot ()));
		color.setTerms (po.getTerms ());
		color.setUnit (po.getUnit ());
		color.setExpiryHeight (po.getExpiryHeight ());
		color.setSignature (po.getSignature ().toByteArray ());
		return color;
	}

	public int getExpiryHeight ()
	{
		return expiryHeight;
	}

	public void setExpiryHeight (int expiryHeight)
	{
		this.expiryHeight = expiryHeight;
	}

	public long getUnit ()
	{
		return unit;
	}

	public void setUnit (long unit)
	{
		this.unit = unit;
	}

	public byte[] getSignature ()
	{
		return signature;
	}

	public void setSignature (byte[] signature)
	{
		this.signature = signature;
	}

	public TransactionOutput getRoot ()
	{
		return root;
	}

	public void setRoot (TransactionOutput root)
	{
		this.root = root;
	}

	public String getTerms ()
	{
		return terms;
	}

	public void setTerms (String terms)
	{
		this.terms = terms;
	}
}
