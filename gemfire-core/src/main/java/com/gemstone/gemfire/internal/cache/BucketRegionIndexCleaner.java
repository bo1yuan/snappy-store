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
package com.gemstone.gemfire.internal.cache;

import java.util.List;

import com.gemstone.gemfire.cache.query.internal.IndexUpdater;

public class BucketRegionIndexCleaner {
    private final boolean lockForGII ;
    private final boolean holdIndexLock;
    private final LocalRegion region;
    
    public BucketRegionIndexCleaner(boolean lockForGII, boolean holdIndexLock,
        BucketRegion region) {
      this.lockForGII = lockForGII;
      this.holdIndexLock = holdIndexLock;      
      this.region = region;
     
    }
    
  public void clearEntries(List<RegionEntry> entries) {
    IndexUpdater indexUpdater = region.getIndexUpdater();
    boolean indexGiiLockTaken = indexUpdater.clearIndexes(region, lockForGII,
        holdIndexLock, entries.iterator(), false);
    if (indexGiiLockTaken) {
      indexUpdater.unlockForGII(indexGiiLockTaken);
    }
  }    
   
}
