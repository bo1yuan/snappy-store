/*

   Derby - Class com.pivotal.gemfirexd.internal.iapi.sql.dictionary.GenericDescriptorList

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package com.pivotal.gemfirexd.internal.iapi.sql.dictionary;





import com.pivotal.gemfirexd.internal.catalog.UUID;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.services.sanity.SanityManager;
import com.pivotal.gemfirexd.internal.iapi.sql.dictionary.DataDictionary;
import com.pivotal.gemfirexd.internal.iapi.sql.dictionary.SchemaDescriptor;
import com.pivotal.gemfirexd.internal.iapi.sql.dictionary.UniqueTupleDescriptor;

import java.util.ArrayList;
import java.util.Iterator;

public class GenericDescriptorList extends ArrayList
{
	private boolean scanned;

	/**
	 * Mark whether or not the underlying system table has
	 * been scanned.  (If a table does not have any
	 * constraints then the size of its CDL will always
	 * be 0.  We used these get/set methods to determine
	 * when we need to scan the table.
	 *
	 * @param scanned	Whether or not the underlying system table has been scanned.
	 */
	public void setScanned(boolean scanned)
	{
		this.scanned = scanned;
	}

	/**
	 * Return whether or not the underlying system table has been scanned.
	 *
	 * @return		Where or not the underlying system table has been scanned.
	 */
	public boolean getScanned()
	{
		return scanned;
	}

	/**
	 * Get the UniqueTupleDescriptor that matches the 
	 * input uuid.
	 *
	 * @param uuid		The UUID for the object
	 *
	 * @return The matching UniqueTupleDescriptor.
	 */
	public UniqueTupleDescriptor getUniqueTupleDescriptor(UUID uuid)
	{
		for (Iterator iterator = iterator(); iterator.hasNext(); )
		{
			UniqueTupleDescriptor ud = (UniqueTupleDescriptor) iterator.next();
			if (ud.getUUID().equals(uuid))
			{
				return ud;
			}
		}
		return null;
	}

	public java.util.Enumeration elements() {
		return java.util.Collections.enumeration(this);
	}
}
