package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.jpa.JpaBlock;

public interface BlockListener {
	public void received (JpaBlock block);
}
