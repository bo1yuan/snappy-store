/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
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
package com.pivotal.gemfirexd.internal.engine.distributed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import com.gemstone.gemfire.cache.Declarable;
import com.gemstone.gemfire.cache.execute.Function;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.internal.cache.BucketRegion;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.pivotal.gemfirexd.internal.engine.Misc;

public class RegionSizeCalculatorFunction implements Function , Declarable {

  public static String ID = "RegionSizeCalculatorFunction";

  @Override
  public void init(Properties props) {

  }

  @Override
  public boolean hasResult() {
    return true;
  }

  @Override
  public void execute(FunctionContext context) {
    HashMap<String, Long> regionSizeInfo = new HashMap<>();

    Set<PartitionedRegion> partitionedRegions = Misc.getGemFireCache().getPartitionedRegions();
    for (PartitionedRegion pr : partitionedRegions) {
      long valueSizeOfRegion = getSizeForAllPrimaryBucketsOfRegion(pr);
      String qualifiedTableName =  Misc.getFullTableNameFromRegionPath(pr.getFullPath());
      regionSizeInfo.put(qualifiedTableName, valueSizeOfRegion);
    }
    context.getResultSender().lastResult(regionSizeInfo);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean optimizeForWrite() {
    return true;
  }

  @Override
  public boolean isHA() {
    return true;
  }


  private long getSizeForAllPrimaryBucketsOfRegion(PartitionedRegion region) {
    long sizeOfRegion = 0;
    Set<BucketRegion> bucketRegions = region.getDataStore().getAllLocalBucketRegions();
    for (BucketRegion br: bucketRegions) {
      sizeOfRegion += br.getTotalBytes();
    }
    return sizeOfRegion;
  }
}
