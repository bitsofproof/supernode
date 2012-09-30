package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.model.JpaBlock;


public interface BlockListener {
	public void received (JpaBlock block);
}
