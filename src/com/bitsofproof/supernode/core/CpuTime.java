package com.bitsofproof.supernode.core;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpuTime<T> {
	private static final ThreadMXBean mxb = ManagementFactory.getThreadMXBean();
	private static final Logger log = LoggerFactory.getLogger(BitcoinPeer.class);
	
	public T execute (String what, Callable<T> r) throws Exception
	{
		long user = mxb.getCurrentThreadUserTime();
		long cpu = mxb.getCurrentThreadCpuTime();
		T t = r.call();
		user -= mxb.getCurrentThreadUserTime();
		cpu -= mxb.getCurrentThreadCpuTime();
		log.trace("CPU ["+what+"] user : " + -user/1000 + " system: " + -(cpu-user)/1000);
		return t;
	}
}
