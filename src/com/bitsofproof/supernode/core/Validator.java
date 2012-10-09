package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.ChainStore;


public interface Validator {
	void validate (ChainStore store);
}
