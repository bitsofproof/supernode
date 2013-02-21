package com.bitsofproof.supernode.model;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

public class LvlTransaction implements UserTransaction
{

	@Override
	public void begin () throws NotSupportedException, SystemException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void commit () throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException,
			SystemException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public int getStatus () throws SystemException
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void rollback () throws IllegalStateException, SecurityException, SystemException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void setRollbackOnly () throws IllegalStateException, SystemException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void setTransactionTimeout (int arg0) throws SystemException
	{
		// TODO Auto-generated method stub

	}

}
