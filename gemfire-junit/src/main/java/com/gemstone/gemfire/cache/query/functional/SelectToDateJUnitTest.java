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
/**
 *
 */
package com.gemstone.gemfire.cache.query.functional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.PartitionAttributesFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.query.CacheUtils;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.cache.query.data.Portfolio;

public class SelectToDateJUnitTest extends TestCase {

  private static String regionName = "test";
  private static int numElem = 120;
  private static String format = "MMddyyyyHHmmss";
  private static String mayDate = "05202012100559";
  private static int numMonthsBeforeMay = 4; 
  private static int numMonthsAfterMay = 7;
  private static int numElementsExpectedPerMonth = numElem * 2 / 12;

  /**
   * @param name test name
   */
  public SelectToDateJUnitTest(String name) {
    super(name);
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }

  protected void setUp() throws Exception {
    System.setProperty("gemfire.Query.VERBOSE", "true");
    CacheUtils.startCache();
  }

  protected void tearDown() throws Exception {
    CacheUtils.closeCache();
  }

  public static Test suite() {
    TestSuite suite = new TestSuite(SelectToDateJUnitTest.class);
    return suite;
  }

  private static String[] toDateQueries = new String[] {
      "select * from /test p where p.createDate = to_date('" + mayDate + "', '"
          + format + "')",
      "select * from /test p where p.createDate < to_date('" + mayDate + "', '"
          + format + "')",
      "select * from /test p where p.createDate > to_date('" + mayDate + "', '"
          + format + "')",
      "select * from /test p where p.createDate <= to_date('" + mayDate
          + "', '" + format + "')",
      "select * from /test p where p.createDate >= to_date('" + mayDate
          + "', '" + format + "')" };
  
  //the test will be validating against the May date, so expected values revolve around month of May
  private static int[] toDateExpectedResults = new int[] {
      numElementsExpectedPerMonth,
      numMonthsBeforeMay * numElementsExpectedPerMonth,
      numMonthsAfterMay * numElementsExpectedPerMonth,
      (numMonthsBeforeMay + 1) * numElementsExpectedPerMonth,
      (numMonthsAfterMay + 1) * numElementsExpectedPerMonth };

  private static String[] projectionQueries = new String[] {
      "select p.createDate from /test p where p.createDate = to_date('"
          + mayDate + "', '" + format + "')",
      "select p.createDate from /test p where p.createDate < to_date('"
          + mayDate + "', '" + format + "')",
      "select p.createDate from /test p where p.createDate > to_date('"
          + mayDate + "', '" + format + "')",
      "select p.createDate from /test p where p.createDate <= to_date('"
          + mayDate + "', '" + format + "')",
      "select p.createDate from /test p where p.createDate >= to_date('"
          + mayDate + "', '" + format + "')", };

  private void executeQueryTest(Cache cache, String[] queries,
      int[] expectedResults) {
    System.out.println("********Execute Query Test********");
    QueryService queryService = cache.getQueryService();
    Query query = null;
    String queryString = null;
    int numQueries = queries.length;
    try {
      for (int i = 0; i < numQueries; i++) {
        queryString = queries[0];
        query = queryService.newQuery(queries[0]);
        SelectResults result = (SelectResults) query.execute();
        assertEquals(queries[0], expectedResults[0], result.size());
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail("Query " + queryString + ":" + query + " Execution Failed!");
    }
    System.out.println("********Completed Executing Query Test********");

    // Destroy current Region for other tests
    cache.getRegion(regionName).destroyRegion();
  }

  private void printoutResults(SelectResults results) {
    Iterator iterator = results.iterator();
    while (iterator.hasNext()) {
      Portfolio p = (Portfolio) iterator.next();
      System.out.println("->" + p + ";" + p.createDate);
    }
  }

  /**
   * Test on Local Region data
   */
  public void testQueriesOnLocalRegion() throws Exception {
    Cache cache = CacheUtils.getCache();
    createLocalRegion();
    assertNotNull(cache.getRegion(regionName));
    assertEquals(numElem * 2, cache.getRegion(regionName).size());
    executeQueryTest(cache, toDateQueries, toDateExpectedResults);
  }

  /**
   * Test on Replicated Region data
   */
  public void testQueriesOnReplicatedRegion() throws Exception {
    Cache cache = CacheUtils.getCache();
    createReplicatedRegion();
    assertNotNull(cache.getRegion(regionName));
    assertEquals(numElem * 2, cache.getRegion(regionName).size());
    executeQueryTest(cache, toDateQueries, toDateExpectedResults);
  }

  /**
   * Test on Partitioned Region data
   */
  public void testQueriesOnPartitionedRegion() throws Exception {
    Cache cache = CacheUtils.getCache();
    createPartitionedRegion();
    assertNotNull(cache.getRegion(regionName));
    assertEquals(numElem * 2, cache.getRegion(regionName).size());
    executeQueryTest(cache, toDateQueries, toDateExpectedResults);
  }

  /**
   * Test on Replicated Region data
   */
  public void testQueriesOnReplicatedRegionWithSameProjAttr() throws Exception {
    Cache cache = CacheUtils.getCache();
    createReplicatedRegion();
    assertNotNull(cache.getRegion(regionName));
    assertEquals(numElem * 2, cache.getRegion(regionName).size());
    executeQueryTest(cache, projectionQueries, toDateExpectedResults);
  }

  /**
   * Test on Partitioned Region data
   */
  public void testQueriesOnPartitionedRegionWithSameProjAttr() throws Exception {
    Cache cache = CacheUtils.getCache();
    createPartitionedRegion();
    assertNotNull(cache.getRegion(regionName));
    assertEquals(numElem * 2, cache.getRegion(regionName).size());
    executeQueryTest(cache, projectionQueries, toDateExpectedResults);
  }

  /******** Region Creation Helper Methods *********/
  /**
   * Each month will have exactly 20 entries with a matching date Code borrowed
   * from shobhit's test
   * 
   * @throws ParseException
   */
  private void createLocalRegion() throws ParseException {
    Cache cache = CacheUtils.getCache();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setDataPolicy(DataPolicy.NORMAL);
    RegionAttributes regionAttributes = attributesFactory.create();
    Region region = cache.createRegion(regionName, regionAttributes);

    for (int i = 1; i <= numElem; i++) {
      putData(i, region);
    }
  }

  private void createReplicatedRegion() throws ParseException {
    Cache cache = CacheUtils.getCache();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setDataPolicy(DataPolicy.REPLICATE);
    RegionAttributes regionAttributes = attributesFactory.create();
    Region region = cache.createRegion(regionName, regionAttributes);

    for (int i = 1; i <= numElem; i++) {
      putData(i, region);
    }
  }

  private void createPartitionedRegion() throws ParseException {
    Cache cache = CacheUtils.getCache();
    PartitionAttributesFactory prAttFactory = new PartitionAttributesFactory();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setPartitionAttributes(prAttFactory.create());
    RegionAttributes regionAttributes = attributesFactory.create();
    Region region = cache.createRegion(regionName, regionAttributes);

    for (int i = 1; i <= numElem; i++) {
      putData(i, region);
    }
  }

  //creates a portfolio object and puts it into the specified region
  private void putData(int id, Region region) throws ParseException {
    Portfolio obj = new Portfolio(id);
    obj.createDate = getCreateDate(id);
    region.put(id, obj);
    region.put(id + numElem, obj);
    System.out.println("Added object " + obj.createDate);
  }

  //creates a date object
  private Date getCreateDate(int i) throws ParseException {
    int month = (i % 12) + 1;
    String format = "MMddyyyyHHmmss";
    String dateString;
    if (month < 10) {
      dateString = "0" + month + "202012100559";
    } else {
      dateString = month + "202012100559";
    }

    SimpleDateFormat sdf = new SimpleDateFormat(format);
    return sdf.parse(dateString);
  }

}
