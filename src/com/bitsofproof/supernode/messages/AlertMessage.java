package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class AlertMessage extends BitcoinPeer.Message {
	private String payload;
	private String signature;
	
	public AlertMessage(BitcoinPeer bitcoinPeer) {
		bitcoinPeer.super("alert");
	}

	@Override
	public void toWire(Writer writer) {
	}
	@Override
	public void fromWire(Reader reader) {
		payload = reader.readString();
		signature = reader.readString();
	}
	public void validate () throws ValidationException {
		// TODO: validate signature
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}
	
}
