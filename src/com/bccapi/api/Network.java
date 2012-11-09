package com.bccapi.api;

import java.io.Serializable;

import com.bitsofproof.supernode.core.Chain;

/**
 * Settings for the network used. Can be either the test or production network.
 */
public class Network implements Serializable
{
	private static final long serialVersionUID = 1L;

	/**
	 * The first byte of a base58 encoded bitcoin standard address.
	 */
	private final int _standardAddressHeader;

	/**
	 * The first byte of a base58 encoded bitcoin multisig address.
	 */
	private final int _multisigAddressHeader;

	public Network (Chain chain)
	{
		_standardAddressHeader = chain.getAddressFlag ();
		_multisigAddressHeader = chain.getMultisigAddressFlag ();
	}

	/**
	 * Get the first byte of a base58 encoded bitcoin address as an integer.
	 * 
	 * @return The first byte of a base58 encoded bitcoin address as an integer.
	 */
	public int getStandardAddressHeader ()
	{
		return _standardAddressHeader;
	}

	/**
	 * Get the first byte of a base58 encoded bitcoin multisig address as an integer.
	 * 
	 * @return The first byte of a base58 encoded bitcoin multisig address as an integer.
	 */
	public int getMultisigAddressHeader ()
	{
		return _multisigAddressHeader;
	}

	@Override
	public int hashCode ()
	{
		return _standardAddressHeader;
	};

	@Override
	public boolean equals (Object obj)
	{
		if ( !(obj instanceof Network) )
		{
			return false;
		}
		Network other = (Network) obj;
		return other._standardAddressHeader == _standardAddressHeader;
	}

}
