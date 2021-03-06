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

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.PartitionAttributesFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.cache.client.internal.ClientMetadataService;
import com.gemstone.gemfire.cache.client.internal.ClientPartitionAdvisor;
import com.gemstone.gemfire.cache.server.CacheServer;
import com.gemstone.gemfire.cache30.CacheTestCase;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.Locator;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.cache.execute.data.CustId;
import com.gemstone.gemfire.internal.cache.execute.data.Customer;
import com.gemstone.gemfire.internal.cache.execute.data.Order;
import com.gemstone.gemfire.internal.cache.execute.data.OrderId;
import com.gemstone.gemfire.internal.cache.execute.data.Shipment;
import com.gemstone.gemfire.internal.cache.execute.data.ShipmentId;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheServerTestUtil;

import dunit.Host;
import dunit.SerializableRunnable;
import dunit.VM;

public class Bug42010StatsDUnitTest extends CacheTestCase{

  
  private static final String Region_Name = "42010";

  private VM member0 = null;

  private VM member1 = null;

  private VM member2 = null;

  private VM member3 = null;

  private static Region region = null;

  private static Region customerRegion = null;

  private static Region orderRegion = null;

  private static Region shipmentRegion = null;

  private static Region regionWithResolver = null;

  private static Cache cache = null;

  private static final int locatorPort = 12345;

  private static Locator locator = null;
  
  private static long metaDataRefreshCount;
  
  private static long nonSingleHopsCount;
  
  private static long metaDataRefreshCount_Customer;
  
  private static long nonSingleHopsCount_Customer;
  
  private static long metaDataRefreshCount_Order;
  
  private static long nonSingleHopsCount_Order;
  
  private static long metaDataRefreshCount_Shipment;
  
  private static long nonSingleHopsCount_Shipment;
  
  public Bug42010StatsDUnitTest(String name) {
    super(name);

    // TODO Auto-generated constructor stub
  }

  public void setUp() throws Exception {
    super.setUp();
    Host host = Host.getHost(0);
    member0 = host.getVM(0);
    member1 = host.getVM(1);
    member2 = host.getVM(2);
    member3 = host.getVM(3);
  }

  public void tearDown2() throws Exception {
    try {

      // close the clients first
      member0.invoke(Bug42010StatsDUnitTest.class, "closeCache");
      member1.invoke(Bug42010StatsDUnitTest.class, "closeCache");
      member2.invoke(Bug42010StatsDUnitTest.class, "closeCache");
      member3.invoke(Bug42010StatsDUnitTest.class, "closeCache");
      closeCache();

      super.tearDown2();

      member0 = null;
      member1 = null;
      member2 = null;
      member3 = null;
      cache = null;
      invokeInEveryVM(new SerializableRunnable() { public void run() { cache = null; } });

    }
    finally {
      unregisterAllDataSerializersFromAllVms();
    }
  }

  public static void closeCache() {
    if (cache != null && !cache.isClosed()) {
      cache.close();
      cache.getDistributedSystem().disconnect();
    }
  }

  public void test_clientStats_PR(){
    VM server1 = member0;
    VM server2 = member1;
    VM server3 = member2;
    VM client1 = member3;
    
    Integer port0 = (Integer)member0.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 113,"No_Colocation"});
    Integer port1 = (Integer)member1.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 113,"No_Colocation"});
    Integer port2 = (Integer)member2.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 113,"No_Colocation"});
     client1.invoke(
        Bug42010StatsDUnitTest.class, "createClient",
        new Object[] {port0, port1, port2,"No_Colocation"});
     
    createClient(port0, port1, port2, "No_Colocation");

    client1.invoke(
        Bug42010StatsDUnitTest.class, "createPR",
        new Object[] {"FirstClient", "No_Colocation"});
    createPR("SecondClient", "No_Colocation");
    
    client1.invoke(
        Bug42010StatsDUnitTest.class, "getPR",
        new Object[] {"FirstClient", "No_Colocation"});
    getPR("SecondClient", "No_Colocation");
    
    client1.invoke(
        Bug42010StatsDUnitTest.class, "updatePR",
        new Object[] {"FirstClient", "No_Colocation"});
  }
  
  public void test_clientStatsColocation_PR(){
    VM server1 = member0;
    VM server2 = member1;
    VM server3 = member2;
    VM client1 = member3;
    
    Integer port0 = (Integer)member0.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 4, "Colocation" });
    Integer port1 = (Integer)member1.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 4, "Colocation" });
    Integer port2 = (Integer)member2.invoke(
        Bug42010StatsDUnitTest.class, "createServerForStats",
        new Object[] { 0, 4, "Colocation"});
     client1.invoke(
        Bug42010StatsDUnitTest.class, "createClient",
        new Object[] {port0, port1, port2, "Colocation"});
    createClient(port0, port1, port2, "Colocation");

    client1.invoke(
        Bug42010StatsDUnitTest.class, "createPR",
        new Object[] {"FirstClient", "Colocation"});
    
    client1.invoke(
        Bug42010StatsDUnitTest.class, "getPR",
        new Object[] {"FirstClient", "Colocation"});
  }

  
  public static void createClient(int port0, int port1, int port2, String colocation) {
    Properties props = new Properties();
    props = new Properties();
    props.setProperty("mcast-port", "0");
    props.setProperty("locators", "");
    CacheTestCase test = new Bug42010StatsDUnitTest(
        "Bug42010StatsDUnitTest");
    DistributedSystem ds = test.getSystem(props);
    cache = CacheFactory.create(ds);
    assertNotNull(cache);
    CacheServerTestUtil.disableShufflingOfEndpoints();
    Pool p;
    try {
      p = PoolManager.createFactory().addServer("localhost", port0).addServer(
          "localhost", port1).addServer("localhost", port2)
          .setSubscriptionEnabled(false)
          .create(Region_Name);
    }
    finally {
      CacheServerTestUtil.enableShufflingOfEndpoints();
    }
    createRegionInClientCache(p.getName(), colocation);
  }

  public static int createServerForStats(int redundantCopies, int totalNoofBuckets, String colocation) {
    CacheTestCase test = new Bug42010StatsDUnitTest(
        "Bug42010StatsDUnitTest");
    cache = test.getCache();
    CacheServer server = cache.addCacheServer();
    int port = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    server.setPort(port);
    server.setHostnameForClients("localhost");
    try {
      server.start();
    }
    catch (IOException e) {
      fail("Failed to start server ", e);
    }

    if (colocation.equals("No_Colocation")) {
      if(totalNoofBuckets == 0){ //DR
        AttributesFactory attr = new AttributesFactory();
        attr.setScope(Scope.DISTRIBUTED_ACK);
        attr.setDataPolicy(DataPolicy.REPLICATE);
        region = cache.createRegion(Region_Name, attr.create());
        assertNotNull(region);
        getLogWriter().info(
            "Distributed Region " + Region_Name + " created Successfully :"
                + region.toString());
      }else{
      PartitionAttributesFactory paf = new PartitionAttributesFactory();
      paf.setRedundantCopies(redundantCopies).setTotalNumBuckets(
          totalNoofBuckets);
      AttributesFactory attr = new AttributesFactory();
      attr.setPartitionAttributes(paf.create());
      region = cache.createRegion(Region_Name, attr.create());
      assertNotNull(region);
      getLogWriter().info(
          "Partitioned Region " + Region_Name + " created Successfully :"
              + region.toString());
      }
    }
    else {
      PartitionAttributesFactory paf = new PartitionAttributesFactory();
      paf.setRedundantCopies(redundantCopies).setTotalNumBuckets(
          totalNoofBuckets).setPartitionResolver(
          new CustomerIDPartitionResolver("CustomerIDPartitionResolver"));
      AttributesFactory attr = new AttributesFactory();
      attr.setPartitionAttributes(paf.create());
      customerRegion = cache.createRegion("CUSTOMER", attr.create());
      assertNotNull(customerRegion);
      getLogWriter().info(
          "Partitioned Region CUSTOMER created Successfully :"
              + customerRegion.toString());

      paf = new PartitionAttributesFactory();
      paf.setRedundantCopies(redundantCopies).setTotalNumBuckets(
          totalNoofBuckets).setColocatedWith("CUSTOMER").setPartitionResolver(
          new CustomerIDPartitionResolver("CustomerIDPartitionResolver"));
      attr = new AttributesFactory();
      attr.setPartitionAttributes(paf.create());
      orderRegion = cache.createRegion("ORDER", attr.create());
      assertNotNull(orderRegion);
      getLogWriter().info(
          "Partitioned Region ORDER created Successfully :"
              + orderRegion.toString());

      paf = new PartitionAttributesFactory();
      paf.setRedundantCopies(redundantCopies).setTotalNumBuckets(
          totalNoofBuckets).setColocatedWith("ORDER").setPartitionResolver(
          new CustomerIDPartitionResolver("CustomerIDPartitionResolver"));
      attr = new AttributesFactory();
      attr.setPartitionAttributes(paf.create());
      shipmentRegion = cache.createRegion("SHIPMENT", attr.create());
      assertNotNull(shipmentRegion);
      getLogWriter().info(
          "Partitioned Region SHIPMENT created Successfully :"
              + shipmentRegion.toString());
    }
    return port;
  }
  
  private static void createRegionInClientCache(String poolName, String colocation) {
    if (colocation.equals("No_Colocation")) {
      AttributesFactory factory = new AttributesFactory();
      factory.setPoolName(poolName);
      factory.setDataPolicy(DataPolicy.EMPTY);
      RegionAttributes attrs = factory.create();
      region = cache.createRegion(Region_Name, attrs);
      assertNotNull(region);
      getLogWriter().info(
          "Region " + Region_Name + " created Successfully :" + region.toString());
    }
    else {
      AttributesFactory factory = new AttributesFactory();
      factory.setPoolName(poolName);
      RegionAttributes attrs = factory.create();
      customerRegion = cache.createRegion("CUSTOMER", attrs);
      assertNotNull(customerRegion);
      getLogWriter().info(
          "Partitioned Region CUSTOMER created Successfully :"
              + customerRegion.toString());

      factory = new AttributesFactory();
      factory.setPoolName(poolName);
      attrs = factory.create();
      orderRegion = cache.createRegion("ORDER", attrs);
      assertNotNull(orderRegion);
      getLogWriter().info(
          "Partitioned Region ORDER created Successfully :"
              + orderRegion.toString());

      factory = new AttributesFactory();
      factory.setPoolName(poolName);
      attrs = factory.create();
      shipmentRegion = cache.createRegion("SHIPMENT", attrs);
      assertNotNull(shipmentRegion);
      getLogWriter().info(
          "Partitioned Region SHIPMENT created Successfully :"
              + shipmentRegion.toString());
    }
  }
  
  public static void createPR(String FromClient,
      String colocation) {
    if (colocation.equals("No_Colocation")) {
      if (FromClient.equals("FirstClient")) {
        for (int i = 0; i < 113; i++) {
          region.create(new Integer(i), "create" + i);
        }
        ClientMetadataService cms = ((GemFireCacheImpl)cache)
            .getClientMetadataService();
        Map<String, ClientPartitionAdvisor> regionMetaData = cms
            .getClientPRMetadata_TEST_ONLY();
        assertEquals(0, regionMetaData.size());

        for (int i = 113; i < 226; i++) {
          region.create(new Integer(i), "create" + i);
        }
        cms = ((GemFireCacheImpl)cache).getClientMetadataService();
        regionMetaData = cms.getClientPRMetadata_TEST_ONLY();
        assertEquals(1, regionMetaData.size());
        assertTrue(regionMetaData.containsKey(region.getFullPath()));
        ClientPartitionAdvisor prMetaData = regionMetaData.get(region
            .getFullPath());
        metaDataRefreshCount = ((LocalRegion)region).getCachePerfStats().getMetaDataRefreshCount();
        nonSingleHopsCount = ((LocalRegion)region).getCachePerfStats().getNonSingleHopsCount();
        assertTrue(metaDataRefreshCount != 0); // hops are not predictable
        assertTrue(nonSingleHopsCount != 0);
      }
      else {
        for (int i = 0; i < 226; i++) {
          region.create(new Integer(i), "create" + i);
        }
        ClientMetadataService cms = ((GemFireCacheImpl)cache)
            .getClientMetadataService();
        Map<String, ClientPartitionAdvisor> regionMetaData = cms
            .getClientPRMetadata_TEST_ONLY();
        assertEquals(1, regionMetaData.size());
        assertTrue(regionMetaData.containsKey(region.getFullPath()));
        ClientPartitionAdvisor prMetaData = regionMetaData.get(region
            .getFullPath());
        metaDataRefreshCount = ((LocalRegion)region).getCachePerfStats().getMetaDataRefreshCount();
        nonSingleHopsCount = ((LocalRegion)region).getCachePerfStats().getNonSingleHopsCount();
        assertTrue(metaDataRefreshCount != 0); // hops are not predictable
        assertTrue(nonSingleHopsCount != 0);
      }
    }
    else {
      for (int i = 0; i <= 20; i++) {
        CustId custid = new CustId(i);
        Customer customer = new Customer("name" + i, "Address" + i);
        customerRegion.put(custid, customer);
        for (int j = 1; j <= 10; j++) {
          int oid = (i * 10) + j;
          OrderId orderId = new OrderId(oid, custid);
          Order order = new Order("OREDR" + oid);
          orderRegion.put(orderId, order);
          for (int k = 1; k <= 10; k++) {
            int sid = (oid * 10) + k;
            ShipmentId shipmentId = new ShipmentId(sid, orderId);
            Shipment shipment = new Shipment("Shipment" + sid);
            shipmentRegion.put(shipmentId, shipment);
          }
        }
      }
      ClientMetadataService cms = ((GemFireCacheImpl)cache)
          .getClientMetadataService();
      Map<String, ClientPartitionAdvisor> regionMetaData = cms
          .getClientPRMetadata_TEST_ONLY();
      assertEquals(3, regionMetaData.size());
      assertTrue(regionMetaData.containsKey(customerRegion.getFullPath()));
      ClientPartitionAdvisor prMetaData_Customer = regionMetaData
          .get(customerRegion.getFullPath());
      metaDataRefreshCount_Customer = ((LocalRegion)customerRegion).getCachePerfStats().getMetaDataRefreshCount();
      nonSingleHopsCount_Customer = ((LocalRegion)customerRegion).getCachePerfStats().getNonSingleHopsCount();
      assertTrue(metaDataRefreshCount_Customer != 0); // hops are not predictable
      assertTrue(nonSingleHopsCount_Customer != 0);

      ClientPartitionAdvisor prMetaData_Order = regionMetaData
          .get(orderRegion.getFullPath());
      metaDataRefreshCount_Order = ((LocalRegion)orderRegion).getCachePerfStats().getMetaDataRefreshCount();
      nonSingleHopsCount_Order = ((LocalRegion)orderRegion).getCachePerfStats().getNonSingleHopsCount();
      assertTrue(metaDataRefreshCount_Order == 0); 
      assertTrue(nonSingleHopsCount_Order != 0);

      ClientPartitionAdvisor prMetaData_Shipment = regionMetaData
          .get(shipmentRegion.getFullPath());
      metaDataRefreshCount_Shipment = ((LocalRegion)shipmentRegion).getCachePerfStats().getMetaDataRefreshCount();
      nonSingleHopsCount_Shipment = ((LocalRegion)shipmentRegion).getCachePerfStats().getNonSingleHopsCount();
      assertTrue(metaDataRefreshCount_Shipment == 0); 
      assertTrue(nonSingleHopsCount_Shipment != 0);
    }
  }
  
  public static void getPR(String FromClient,
      String colocation) {
    if (colocation.equals("No_Colocation")) {
      if (FromClient.equals("FirstClient")) {
        for (int i = 0; i < 226; i++) {
          region.get(new Integer(i));
        }
        ClientMetadataService cms = ((GemFireCacheImpl)cache)
            .getClientMetadataService();
        Map<String, ClientPartitionAdvisor> regionMetaData = cms
            .getClientPRMetadata_TEST_ONLY();
        assertEquals(1, regionMetaData.size());
        ClientPartitionAdvisor prMetaData = regionMetaData.get(region
            .getFullPath());
        assertEquals(metaDataRefreshCount , ((LocalRegion)region).getCachePerfStats().getMetaDataRefreshCount());
        assertEquals(nonSingleHopsCount , ((LocalRegion)region).getCachePerfStats().getNonSingleHopsCount());
      }
      else {
        for (int i = 0; i < 226; i++) {
          region.get(new Integer(i));
        }
        ClientMetadataService cms = ((GemFireCacheImpl)cache)
            .getClientMetadataService();
        Map<String, ClientPartitionAdvisor> regionMetaData = cms
            .getClientPRMetadata_TEST_ONLY();
        assertEquals(1, regionMetaData.size());
        ClientPartitionAdvisor prMetaData = regionMetaData.get(region
            .getFullPath());
        assertEquals(metaDataRefreshCount , ((LocalRegion)region).getCachePerfStats().getMetaDataRefreshCount());
        assertEquals(nonSingleHopsCount , ((LocalRegion)region).getCachePerfStats().getNonSingleHopsCount());
      }
    }
    else {
      for (int i = 0; i <= 20; i++) {
        CustId custid = new CustId(i);
        customerRegion.get(custid);
        for (int j = 1; j <= 10; j++) {
          int oid = (i * 10) + j;
          OrderId orderId = new OrderId(oid, custid);
          orderRegion.get(orderId);
          for (int k = 1; k <= 10; k++) {
            int sid = (oid * 10) + k;
            ShipmentId shipmentId = new ShipmentId(sid, orderId);
            shipmentRegion.get(shipmentId);
          }
        }
      }
      ClientMetadataService cms = ((GemFireCacheImpl)cache)
          .getClientMetadataService();
      Map<String, ClientPartitionAdvisor> regionMetaData = cms
          .getClientPRMetadata_TEST_ONLY();
      assertEquals(3, regionMetaData.size());
      assertTrue(regionMetaData.containsKey(customerRegion.getFullPath()));
      ClientPartitionAdvisor prMetaData_Customer = regionMetaData
          .get(customerRegion.getFullPath());
      assertEquals(metaDataRefreshCount_Customer , ((LocalRegion)customerRegion).getCachePerfStats().getMetaDataRefreshCount());
      assertEquals(nonSingleHopsCount_Customer , ((LocalRegion)customerRegion).getCachePerfStats().getNonSingleHopsCount());

      ClientPartitionAdvisor prMetaData_Order = regionMetaData
          .get(orderRegion.getFullPath());
      assertEquals(metaDataRefreshCount_Order , ((LocalRegion)orderRegion).getCachePerfStats().getMetaDataRefreshCount());
      assertEquals(nonSingleHopsCount_Order , ((LocalRegion)orderRegion).getCachePerfStats().getNonSingleHopsCount());

      ClientPartitionAdvisor prMetaData_Shipment = regionMetaData
          .get(shipmentRegion.getFullPath());
      assertEquals(metaDataRefreshCount_Shipment , ((LocalRegion)shipmentRegion).getCachePerfStats().getMetaDataRefreshCount());
      assertEquals(nonSingleHopsCount_Shipment , ((LocalRegion)shipmentRegion).getCachePerfStats().getNonSingleHopsCount());
    }
  }
  
  public static void updatePR(String FromClient,
      String colocation) {
    if (colocation.equals("No_Colocation")) {
      if (FromClient.equals("FirstClient")) {
        for (int i = 0; i < 226; i++) {
          region.put(new Integer(i), "Update" + i);
        }
        ClientMetadataService cms = ((GemFireCacheImpl)cache)
            .getClientMetadataService();
        Map<String, ClientPartitionAdvisor> regionMetaData = cms
            .getClientPRMetadata_TEST_ONLY();
        assertEquals(1, regionMetaData.size());
        ClientPartitionAdvisor prMetaData = regionMetaData.get(region
            .getFullPath());
        assertEquals(metaDataRefreshCount , ((LocalRegion)region).getCachePerfStats().getMetaDataRefreshCount());
        assertEquals(nonSingleHopsCount , ((LocalRegion)region).getCachePerfStats().getNonSingleHopsCount());
      }
    }
  }
}
