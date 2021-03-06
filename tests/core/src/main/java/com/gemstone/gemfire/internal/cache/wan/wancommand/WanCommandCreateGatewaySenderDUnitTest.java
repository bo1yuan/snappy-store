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
package com.gemstone.gemfire.internal.cache.wan.wancommand;

import hydra.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.gemstone.gemfire.cache.util.Gateway.OrderPolicy;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.cache.ForceReattemptException;
import com.gemstone.gemfire.internal.cache.wan.GatewaySenderException;
import com.gemstone.gemfire.management.cli.Result;
import com.gemstone.gemfire.management.internal.cli.i18n.CliStrings;
import com.gemstone.gemfire.management.internal.cli.result.CommandResult;
import com.gemstone.gemfire.management.internal.cli.result.TabularResultData;

import dunit.Host;
import dunit.VM;
import dunit.DistributedTestCase.ExpectedException;

public class WanCommandCreateGatewaySenderDUnitTest extends WANCommandTestBase {
  
  private static final long serialVersionUID = 1L;
  
  public WanCommandCreateGatewaySenderDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
  }
  
  private CommandResult executeCommandWithIgnoredExceptions(String command) {
    final ExpectedException exln = addExpectedException("Could not connect");
    try {
      CommandResult commandResult = executeCommand(command);
      return commandResult;
    } finally {
      exln.remove();
    }
  }


  /**
   * GatewaySender with all default attributes
   */
  public void testCreateGatewaySenderWithDefault() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
  }
  
  /**
   * GatewaySender with given attribute values
   */
  public void testCreateGatewaySender() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD";   
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    
    vm3.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, null });
    vm4.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, null });
    vm5.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, null });
  }

  /**
   * GatewaySender with given attribute values.
   * Error scenario where dispatcher threads is set to more than 1 and 
   * no order policy provided.
   */
  public void testCreateGatewaySender_Error() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation should fail", status.get(i).indexOf("ERROR:") != -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

  }

  
  /**
   * GatewaySender with given attribute values and event filters.
   */
  public void testCreateGatewaySenderWithGatewayEventFilters() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__GATEWAYEVENTFILTER + 
        "=com.gemstone.gemfire.cache30.MyGatewayEventFilter1,com.gemstone.gemfire.cache30.MyGatewayEventFilter2";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    
    List<String> eventFilters = new ArrayList<String>();
    eventFilters.add("com.gemstone.gemfire.cache30.MyGatewayEventFilter1");
    eventFilters.add("com.gemstone.gemfire.cache30.MyGatewayEventFilter2");
    vm3.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, eventFilters, null });
    vm4.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, eventFilters, null });
    vm5.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, eventFilters, null });
  }

  /**
   * GatewaySender with given attribute values and transport filters.
   */
  public void testCreateGatewaySenderWithGatewayTransportFilters() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__GATEWAYTRANSPORTFILTER + "=com.gemstone.gemfire.cache30.MyGatewayTransportFilter1";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    
    List<String> transportFilters = new ArrayList<String>();
    transportFilters.add("com.gemstone.gemfire.cache30.MyGatewayTransportFilter1");
    vm3.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, transportFilters });
    vm4.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, transportFilters });
    vm5.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, transportFilters });
  }

  /**
   * GatewaySender with given attribute values on given member.
   */
  public void testCreateGatewaySender_OnMember() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    
    final DistributedMember vm3Member = (DistributedMember) vm3.invoke(
        WANCommandTestBase.class, "getMember");

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MEMBER + "=" + vm3Member.getId()
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(1, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    
    vm3.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, false, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 2, OrderPolicy.THREAD, null, null });
  }

  /**
   * GatewaySender with given attribute values on given group
   */
  public void testCreateGatewaySender_Group() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup1" });
    vm4.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup1" });
    vm5.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup1" });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__GROUP + "=SenderGroup1"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(3, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
  }
  
  /**
   * GatewaySender with given attribute values on given group.
   * Only 2 of 3 members are part of the group.
   */
  public void testCreateGatewaySender_Group_Scenario2() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup1" });
    vm4.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup1" });
    vm5.invoke(WANCommandTestBase.class, "createCacheWithGroups",
        new Object[] { punePort, "SenderGroup2" });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__GROUP + "=SenderGroup1"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(2, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", true, false });
  }
  
  /**
   * Parallel GatewaySender with given attribute values
   */
  public void testCreateParallelGatewaySender() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" 
        + " --" + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100";
    CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
    if (cmdResult != null) {
      String strCmdResult = commandResultToString(cmdResult);
      Log.getLogWriter().info(
          "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
      assertEquals(Result.Status.OK, cmdResult.getStatus());

      TabularResultData resultData = (TabularResultData) cmdResult
          .getResultData();
      List<String> status = resultData.retrieveAllValues("Status");
      assertEquals(4, status.size());
      for (int i = 0; i < status.size(); i++) {
        assertTrue("GatewaySender creation failed with: " + status.get(i), status.get(i).indexOf("ERROR:") == -1);
      }
    } else {
      fail("testCreateGatewaySender failed as did not get CommandResult");
    }

    vm3.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm4.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    vm5.invoke(WANCommandTestBase.class, "verifySenderState", new Object[] {
        "ln", false, false });
    
    vm3.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, true, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 1, null, null, null });
    vm4.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, true, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 1, null, null, null });
    vm5.invoke(WANCommandTestBase.class, "verifySenderAttributes",
        new Object[] { "ln", 2, true, true, 1000, 2000, true, 1000, 5000,
            true, false, 1000, 100, 1, null, null, null });
  }
  
  /**
   * Parallel GatewaySender with given attribute values.
   * Provide dispatcherThreads as 2 which is not valid for Parallel sender.
   */
  public void testCreateParallelGatewaySender_Error() {

    VM puneLocator = Host.getLocator();
    int punePort = (Integer) puneLocator.invoke(WANCommandTestBase.class,
        "getLocatorPort");

    Properties props = new Properties();
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "localhost[" + punePort
        + "]");
    createDefaultSetup(props);

    Integer nyPort = (Integer) vm2.invoke(WANCommandTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, punePort });

    vm3.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm4.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });
    vm5.invoke(WANCommandTestBase.class, "createCache",
        new Object[] { punePort });

    String command = CliStrings.CREATE_GATEWAYSENDER + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ID + "=ln" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__REMOTEDISTRIBUTEDSYSTEMID + "=2"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__PARALLEL + "=true" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__MANUALSTART + "=true" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__SOCKETBUFFERSIZE + "=1000" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__SOCKETREADTIMEOUT + "=2000" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ENABLEBATCHCONFLATION + "=true"
        + " --" + CliStrings.CREATE_GATEWAYSENDER__BATCHSIZE + "=1000" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__BATCHTIMEINTERVAL + "=5000" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ENABLEPERSISTENCE + "=true" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__DISKSYNCHRONOUS + "=false" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__MAXQUEUEMEMORY + "=1000" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ALERTTHRESHOLD + "=100" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__DISPATCHERTHREADS + "=2" + " --"
        + CliStrings.CREATE_GATEWAYSENDER__ORDERPOLICY + "=THREAD";
    ExpectedException exp = addExpectedException(GatewaySenderException.class
        .getName());
    try {
      CommandResult cmdResult = executeCommandWithIgnoredExceptions(command);
      if (cmdResult != null) {
        String strCmdResult = commandResultToString(cmdResult);
        Log.getLogWriter().info(
            "testCreateGatewaySender stringResult : " + strCmdResult + ">>>>");
        assertEquals(Result.Status.OK, cmdResult.getStatus());

        TabularResultData resultData = (TabularResultData) cmdResult
            .getResultData();
        List<String> status = resultData.retrieveAllValues("Status");
        assertEquals(4, status.size());
        for (int i = 0; i < status.size(); i++) {
          assertTrue("GatewaySender creation should have failed", status.get(i)
              .indexOf("ERROR:") != -1);
        }
      } else {
        fail("testCreateGatewaySender failed as did not get CommandResult");
      }
    } finally {
      exp.remove();
    }

  }
}
