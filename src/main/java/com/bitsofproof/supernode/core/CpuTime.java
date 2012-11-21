/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpuTime<T>
{
	private static final ThreadMXBean mxb = ManagementFactory.getThreadMXBean ();
	private static final Logger log = LoggerFactory.getLogger (BitcoinPeer.class);

	public T execute (String what, Callable<T> r) throws Exception
	{
		long user = mxb.getCurrentThreadUserTime ();
		long cpu = mxb.getCurrentThreadCpuTime ();
		T t = r.call ();
		user -= mxb.getCurrentThreadUserTime ();
		cpu -= mxb.getCurrentThreadCpuTime ();
		log.trace ("CPU [" + what + "] user : " + -user / 1000 + " system: " + -(cpu - user) / 1000);
		return t;
	}
}
