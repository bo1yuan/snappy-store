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
package com.gemstone.gemfire.management.internal.beans.stats;

import com.gemstone.gemfire.StatisticDescriptor;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.internal.statistics.StatisticId;
import com.gemstone.gemfire.internal.statistics.StatisticNotFoundException;
import com.gemstone.gemfire.internal.statistics.StatisticsNotification;

/**
 * 
 * @author rishim
 *
 */
public class GCStatsMonitor extends MBeanStatsMonitor {

  private volatile long collections = 0;

  private volatile long collectionTime = 0;

  
  public GCStatsMonitor(String name) {
    super(name);
  }

  @Override
  public Number getStatistic(String statName) {
    if (statName.equals(StatsKey.VM_GC_STATS_COLLECTIONS)) {
      return getCollections();
    }
    if (statName.equals(StatsKey.VM_GC_STATS_COLLECTION_TIME)) {
      return getCollectionTime();
    }
    return 0;
  }

   
  @Override
  public void handleNotification(StatisticsNotification notification) {
    decreasePrevValues(statsMap);
    for (StatisticId statId : notification) {
      StatisticDescriptor descriptor = statId.getStatisticDescriptor();
      String name = descriptor.getName();
      Number value;
      try {
        value = notification.getValue(statId);
      } catch (StatisticNotFoundException e) {
        value = 0;
      }
      log(name,value);
      increaseStats(name, value);
      statsMap.put(name, value);
    }
  }

  private void decreasePrevValues(DefaultHashMap statsMap) {
    collections -= statsMap.get(StatsKey.VM_GC_STATS_COLLECTIONS).intValue();
    collectionTime -= statsMap.get(StatsKey.VM_GC_STATS_COLLECTION_TIME).intValue();
  }

  private void increaseStats(String name, Number value) {
    if (name.equals(StatsKey.VM_GC_STATS_COLLECTIONS)) {
      collections += value.longValue();
      return;
    }
    if (name.equals(StatsKey.VM_GC_STATS_COLLECTION_TIME)) {
      collectionTime += value.longValue();
      return;
    }
  }

  public long getCollections() {
    return collections;
  }

  public long getCollectionTime() {
    return collectionTime;
  }
}
