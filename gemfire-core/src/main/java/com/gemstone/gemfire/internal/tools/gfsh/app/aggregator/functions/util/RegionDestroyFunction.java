/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.gemstone.gemfire.internal.tools.gfsh.app.aggregator.functions.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.internal.tools.gfsh.aggregator.AggregateFunction;
import com.gemstone.gemfire.internal.tools.gfsh.aggregator.AggregateResults;
import com.gemstone.gemfire.internal.tools.gfsh.app.command.task.RegionDestroyTask;

public class RegionDestroyFunction implements AggregateFunction, DataSerializable
{	
	private static final long serialVersionUID = 1L;

	private RegionDestroyTask regionDestroyTask;
	
	public RegionDestroyFunction()
	{
	}
	
	public RegionDestroyFunction(String regionPath)
	{
		this.regionDestroyTask = new RegionDestroyTask(regionPath);
	}
	
	public RegionDestroyFunction(RegionDestroyTask regionDestroyTask)
	{
		this.regionDestroyTask = regionDestroyTask;
	}
	
	public RegionDestroyTask getRegionDestroyTask() 
	{
		return regionDestroyTask;
	}

	public void setRegionCreateAllTask(RegionDestroyTask regionDestroyTask) 
	{
		this.regionDestroyTask = regionDestroyTask;
	}
	
	public AggregateResults run(FunctionContext context) 
	{
		AggregateResults results = new AggregateResults();
		results.setDataObject(regionDestroyTask.runTask(null));
		return results;
	}

	/**
	 * Returns a java.util.List of LocalRegionInfo objects;
	 */
	public Object aggregate(List list)
	{
		ArrayList resultList = new ArrayList();
		for (int i = 0; i < list.size(); i++) {
			AggregateResults results = (AggregateResults)list.get(i);
			if (results.getDataObject() != null) {
				resultList.add(results.getDataObject());
			}
		}
		return resultList;
	}
	
	public Object aggregateDistributedSystems(Object[] results)
	{
		ArrayList list = new ArrayList();
		for (int i = 0; i < results.length; i++) {
			list.add(results[i]);
		}
		return list;
	}
	
	public void fromData(DataInput input) throws IOException, ClassNotFoundException 
	{
		regionDestroyTask = (RegionDestroyTask)DataSerializer.readObject(input);
	}

	public void toData(DataOutput output) throws IOException 
	{
		DataSerializer.writeObject(regionDestroyTask, output);
	}
}
