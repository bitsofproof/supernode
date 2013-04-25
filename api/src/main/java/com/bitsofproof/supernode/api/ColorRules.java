/*
 * Copyright 2013 bits of proof zrt.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ColorRules
{
	public static class ColoredCoin
	{
		public String color;
		public long value;
	}

	public static void colorOutput (List<ColoredCoin> input, List<ColoredCoin> output)
	{
		if ( validColorband (input) )
		{
			List<String> colorOrder = new ArrayList<String> ();
			Map<String, Long> colorSums = new HashMap<String, Long> ();
			for ( ColoredCoin c : input )
			{
				if ( c.color != null )
				{
					if ( colorOrder.isEmpty () || !c.color.equals (colorOrder.get (colorOrder.size () - 1)) )
					{
						colorOrder.add (c.color);
					}
					Long s = colorSums.get (c.color);
					colorSums.put (c.color, s == null ? c.value : s.longValue () + c.value);
				}
			}
			Iterator<ColoredCoin> o = output.iterator ();
			for ( String color : colorOrder )
			{
				long offer = colorSums.get (color);
				while ( o.hasNext () )
				{
					ColoredCoin out = o.next ();
					if ( out.value > offer )
					{
						// broke coloring from this point
						return;
					}
					offer -= out.value;
					out.color = color;
					if ( offer == 0 )
					{
						break;
					}
				}
			}
		}
	}

	private static boolean validColorband (List<ColoredCoin> coins)
	{
		boolean first = true;
		boolean startsWithColors = false;
		String lastColor = null;
		Set<String> seen = new HashSet<String> ();

		for ( ColoredCoin c : coins )
		{
			if ( first )
			{
				if ( c.color != null )
				{
					// must start with colors or no colors at all
					startsWithColors = true;
				}
				first = false;
			}

			if ( c.color != null )
			{
				if ( !startsWithColors )
				{
					// color should have been first
					return false;
				}
				if ( lastColor != null && !lastColor.equals (c.color) && seen.contains (c.color) )
				{
					// same colors not consecutive
					return false;
				}
				lastColor = c.color;
				seen.add (lastColor);
			}
		}
		return true;
	}
}
