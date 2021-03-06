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
/*
 * Created on Apr 21, 2005 *
 * 
 */
package com.gemstone.gemfire.cache.query.internal.index;


import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.query.internal.index.IndexManager;
import com.gemstone.gemfire.cache.query.internal.index.IndexUtils;
//import com.gemstone.gemfire.internal.cache.LocalRegion;

import java.util.Properties;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.gemstone.gemfire.distributed.DistributedSystem;

/**
 * @author asifs
 *
 * 
 */
public class ProgRegionCreationIndexUpdateTypeTest extends TestCase{
  
  private Cache cache = null;
  public ProgRegionCreationIndexUpdateTypeTest(String testName) {
    super(testName);
  }
  
  protected void setUp() throws Exception {
    
  }
  
  protected void tearDown() throws Exception {
    if( !cache.isClosed())
      cache.close();
   
  }
  
  public static Test suite(){
    TestSuite suite = new TestSuite(ProgRegionCreationIndexUpdateTypeTest.class);
    return suite;
  }
  
  public void testProgrammaticIndexUpdateType() throws Exception  {
  	Properties props = new Properties();
    //props.setProperty("mcast-address","224.0.0.250");
    //props.setProperty("mcast-port", "10334");
    props.setProperty("log-level", "config");
    DistributedSystem  ds = DistributedSystem.connect(props);
    cache = CacheFactory.create(ds);
    //Create a Region with index maintenance type as explicit synchronous
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setIndexMaintenanceSynchronous(true);
    RegionAttributes regionAttributes = attributesFactory.create();
    Region region = cache.createRegion("region1", regionAttributes);
    IndexManager im = IndexUtils.getIndexManager(region,true);
    
    if(!im.isIndexMaintenanceTypeSynchronous())
    	Assert.fail("IndexMaintenanceTest::testProgrammaticIndexUpdateType: Index Update Type found to be asynchronous when it was marked explicitly synchronous");
    
    //Create a Region with index mainteneace type as explicit asynchronous    
    attributesFactory = new AttributesFactory();
    attributesFactory.setIndexMaintenanceSynchronous(false);
    regionAttributes = attributesFactory.create();
    region = cache.createRegion("region2", regionAttributes);
    im = IndexUtils.getIndexManager(region,true);
    if(im.isIndexMaintenanceTypeSynchronous())
    	Assert.fail("IndexMaintenanceTest::testProgrammaticIndexUpdateType: Index Update Type found to be synchronous when it was marked explicitly asynchronous");
    
    //create a default region & check index maintenecae type .It should be 
    // synchronous    
    attributesFactory = new AttributesFactory();
    regionAttributes = attributesFactory.create();
    region = cache.createRegion("region3", regionAttributes);
    im = IndexUtils.getIndexManager(region,true);
    if(!im.isIndexMaintenanceTypeSynchronous())
    	Assert.fail("IndexMaintenanceTest::testProgrammaticIndexUpdateType: Index Update Type found to be asynchronous when it default RegionAttributes should have created synchronous update type");

  }
}
