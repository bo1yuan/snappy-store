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
package com.gemstone.gemfire.internal.cache.wan.misc;

import com.gemstone.gemfire.internal.cache.wan.WANTestBase;
import com.gemstone.gemfire.internal.cache.wan.WANTestBase.MyGatewayEventFilter;
import com.gemstone.gemfire.internal.cache.wan.WANTestBase.PDXGatewayEventFilter;

import dunit.AsyncInvocation;

public class PDXNewWanDUnitTest extends WANTestBase{

  private static final long serialVersionUID = 1L;
  
  public PDXNewWanDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
  }

  /**
   * Test
   *   1> Site 1 : 1 locator, 1 member
   *   2> Site 2 : 1 locator, 1 member
   *   3> DR is defined on  member 1 on site1
   *   4> Serial GatewaySender is defined on member 1 on site1
   *   5> Same DR is defined on site2 member 1
   *   6> Put is done with value which is PDXSerializable
   *   7> Validate whether other sites member receive this put operation.    
   */
  public void testWANPDX_RR_SerialSender() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", null, isOffHeap()});

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm3.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", "ln", isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
        1 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_RR", 1 });
  }
  
  /**
   * Test
   *   1> Site 1 : 1 locator, 1 member
   *   2> Site 2 : 1 locator, 1 member
   *   3> Site 3 : 1 locator, 1 member
   *   3> DR is defined on  member 1 on site1
   *   4> Serial GatewaySender is defined on member 1 on site1
   *   5> Same DR is defined on site2 member 1
   *   6> Put is done with value which is PDXSerializable
   *   7> Validate whether other sites member receive this put operation.    
   */
  public void testWANPDX_RR_SerialSender3Sites() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });
    
    Integer tkPort = (Integer)vm2.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 3, lnPort });

    vm3.invoke(WANTestBase.class, "createReceiver", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });
    vm5.invoke(WANTestBase.class, "createReceiver", new Object[] { tkPort });


    //Create all of our gateway senders
    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ny", 2,
        false, 100, 10, false, false, null, true });
    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "tk", 3,
      false, 100, 10, false, false, null, true });
    vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 1,
      false, 100, 10, false, false, null, true });
    vm4.invoke(WANTestBase.class, "createSender", new Object[] { "tk", 3,
    false, 100, 10, false, false, null, true });
    vm5.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 1,
      false, 100, 10, false, false, null, true });
    vm5.invoke(WANTestBase.class, "createSender", new Object[] { "ny", 2,
    false, 100, 10, false, false, null, true });
    vm3.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
      testName + "_RR", "ny,tk", isOffHeap()});
    vm4.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
      testName + "_RR", "ln,tk", isOffHeap()});
    vm5.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
      testName + "_RR", "ln,ny", isOffHeap()});
    
    //Start all of the senders
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ny" });
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "tk" });
    vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    vm4.invoke(WANTestBase.class, "startSender", new Object[] { "tk" });
    vm5.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    vm5.invoke(WANTestBase.class, "startSender", new Object[] { "ny" });
    
    //Pause ln to ny. This means the PDX type will not be dispatched
    //to ny from ln
    vm3.invoke(WANTestBase.class, "pauseSender", new Object[] { "ny" });
    
    pause(5000);
    
    //Do some puts that define a PDX type in ln
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
      1 });
    
    //Make sure that tk received the update
    vm5.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
      testName + "_RR", 1 });
    
    //Make ny didn't receive the update because the sender is paused 
    vm4.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
      testName + "_RR", 0 });
    
    //Now, do a put from tk. This serialized object will be distributed
    //to ny from tk, using the type defined by ln.
    vm5.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
      2 });
    
    //Verify the ny can read the object
    vm4.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
      testName + "_RR", 2 });
    
    //Wait for vm3 to receive the update (prevents a broken pipe suspect string) 
    vm3.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
      testName + "_RR", 2 });
  }
  
  public void testWANPDX_RR_SerialSender_StartedLater() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", null, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", "ln", isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
        10 });

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
      40 });
    
    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_RR", 40 });
  }
  
  /**
   * Test
   *   1> Site 1 : 1 locator, 1 member
   *   2> Site 2 : 1 locator, 1 member
   *   3> PR is defined on  member 1 on site1
   *   4> Serial GatewaySender is defined on member 1 on site1
   *   5> Same PR is defined on site2 member 1
   *   6> Put is done with value which is PDXSerializable
   *   7> Validate whether other sites member receive this put operation.    
   */
  
  public void testWANPDX_PR_SerialSender() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        1 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 1 });
  }
  
  public void testWANPDX_PR_SerialSender_StartedLater() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver_PDX", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        20 });
    
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
      40 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 40 });
  }
  
  /**
   * Test
   *   1> Site 1 : 1 locator, 2 member
   *   2> Site 2 : 1 locator, 2 member
   *   3> PR is defined on  member 1, 2 on site1
   *   4> Serial GatewaySender is defined on member 1 on site1
   *   5> Same PR is defined on site2 member 1, 2
   *   6> Put is done with value which is PDXSerializable
   *   7> Validate whether other sites member receive this put operation.    
   */
  
  public void testWANPDX_PR_MultipleVM_SerialSender() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });
    
    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null,1, 5, isOffHeap()});

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", "ln", 1, 5, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 1, 5, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 10 });
  }
  
  public void testWANPDX_PR_MultipleVM_SerialSender_StartedLater() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver_PDX", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });
    
    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 1, 5, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", "ln", 1, 5, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 1, 5, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm4.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
      40 });
    
    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 40 });
  }
  
  /**
   * Test
   *   1> Site 1 : 1 locator, 1 member
   *   2> Site 2 : 1 locator, 1 member
   *   3> PR is defined on  member 1 on site1
   *   4> Parallel GatewaySender is defined on member 1 on site1
   *   5> Same PR is defined on site2 member 1
   *   6> Put is done with value which is PDXSerializable
   *   7> Validate whether other sites member receive this put operation.    
   */
  
  public void testWANPDX_PR_ParallelSender() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        true, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 1, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 1, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        1 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 1});
  }
  
  public void testWANPDX_PR_ParallelSender_StartedLater() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver_PDX", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        true, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
      40 });
    
    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 40 });
  }
  
  
  public void testWANPDX_PR_MultipleVM_ParallelSender() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        true, 100, 10, false, false, null, true });
    vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
      true, 100, 10, false, false, null, true });
    
    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 10 });
  }
  
  public void testWANPDX_PR_MultipleVM_ParallelSender_StartedLater() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver_PDX", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createCache_PDX", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        true, 100, 10, false, false, null, true });
   vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
      true, 100, 10, false, false, null, true });
    
    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    AsyncInvocation inv1 = vm3.invokeAsync(WANTestBase.class, "startSender", new Object[] { "ln" });
    AsyncInvocation inv2 = vm4.invokeAsync(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    try{
      inv1.join();
      inv2.join();
    }
    catch(InterruptedException ie) {
      fail("Caught interrupted exception");
    }
    
    vm4.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
      40 });
    
    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 40 });
  }
  
  
  public void testWANPDX_RR_SerialSenderWithFilter() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, new PDXGatewayEventFilter(), true });

    vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", null, isOffHeap()});

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm3.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", "ln", isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_RR",
        1 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_RR", 1 });
    
    vm3.invoke(PDXNewWanDUnitTest.class, "verifyFilterInvocation", new Object[] { 1});
  }
  
  
  public void testWANPDX_PR_MultipleVM_ParallelSenderWithFilter() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });
    vm4.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        true, 100, 10, false, false, new PDXGatewayEventFilter(), true });
    vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
      true, 100, 10, false, false, new PDXGatewayEventFilter(), true });
    
    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        10 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 10 });
    
    vm3.invoke(PDXNewWanDUnitTest.class, "verifyFilterInvocation", new Object[] { 5});
    vm4.invoke(PDXNewWanDUnitTest.class, "verifyFilterInvocation", new Object[] { 5});
  }
  
  
  /**
   * When remote site bounces then we should send pdx event again.
   */
  
  public void Bug_testWANPDX_PR_SerialSender_RemoteSite_Bounce() {
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

    vm3.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

    vm3.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", null, 0, 2, isOffHeap()});

    vm3.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm3.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
        testName + "_PR", "ln", 0, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
        1 });

    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
        testName + "_PR", 1 });
    
    vm2.invoke(WANTestBase.class, "killSender", new Object[] {});
    
    vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });
    vm4.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });
    
    vm2.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", null, 1, 2, isOffHeap()});
    vm4.invoke(WANTestBase.class, "createPartitionedRegion", new Object[] {
      testName + "_PR", null, 1, 2, isOffHeap()});
    
    vm3.invoke(WANTestBase.class, "doPutsPDXSerializable", new Object[] { testName + "_PR",
      1 });
    
    vm2.invoke(WANTestBase.class, "validateRegionSize_PDX", new Object[] {
      testName + "_PR", 1 });
  }
  

  
  
  public static void verifyFilterInvocation(int invocation) {
    assertEquals(((PDXGatewayEventFilter)eventFilter).beforeEnqueueInvoked, invocation);
    assertEquals(((PDXGatewayEventFilter)eventFilter).beforeTransmitInvoked, invocation);
    assertEquals(((PDXGatewayEventFilter)eventFilter).afterAckInvoked, invocation);
  }

  
  
}
