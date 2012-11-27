package com.bitsofproof.supernode.model;

import com.bitsofproof.supernode.core.WireFormat;

public interface HasToWire
{
	public void toWire (WireFormat.Writer writer);
}
