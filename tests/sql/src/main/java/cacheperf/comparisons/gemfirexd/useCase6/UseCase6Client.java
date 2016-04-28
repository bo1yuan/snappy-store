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
package cacheperf.comparisons.gemfirexd.useCase6;

import cacheperf.CachePerfPrms;
import cacheperf.comparisons.gemfirexd.*;
import cacheperf.comparisons.gemfirexd.useCase6.UseCase6Stats.Stmt;
import com.gemstone.gemfire.Statistics;
import com.gemstone.gemfire.StatisticsFactory;
import hydra.*;
import hydra.blackboard.SharedCounters;
import hydra.gemfirexd.*;

import java.io.*;
import java.rmi.RemoteException;
import java.sql.*;
import java.util.*;

import objects.query.QueryPrms;
import org.apache.hadoop.classification.InterfaceAudience;
import sql.SQLBB;
import sql.SQLHelper;
import sql.SQLPrms;
import sql.datagen.*;
import sql.generic.SqlUtilityHelper;
import sql.sqlutil.ResultSetHelper;
import util.TestException;
import util.TestHelper;

public class UseCase6Client extends QueryPerfClient {

  protected static final String CONFLICT_STATE = "X0Z02";
  protected static final String DEADLOCK_STATE = "ORA-00060"; // oracle table lock
  protected static final String DUPLICATE_STR = "The statement was aborted because it would have caused a duplicate key value";

  protected static final int numBuckets = UseCase6Prms.getNumBuckets();
  protected static final boolean timeStmts = UseCase6Prms.timeStmts();

  // trim intervals
  protected static final int TRANSACTIONS = 1580017;

  protected static final String TRANSACTIONS_NAME = "updatetransactions";

  // hydra thread locals
  protected static HydraThreadLocal localuseCase6stats = new HydraThreadLocal();

  private static final String selstm = "SELECT * FROM OLTP_PNP_Subscriptions WHERE msisdn = ? AND walletname = ? ORDER BY expirydate desc fetch first 1 rows only";
  private static final String updstm = "UPDATE OLTP_PNP_Subscriptions SET expirydate = ? WHERE id = ?";
  public static int numServers = 0;

  private PreparedStatement selstmPS = null;
  private PreparedStatement updstmPS = null;

  public UseCase6Stats useCase6stats; // statistics

  protected boolean logQueries;
  protected boolean logQueryResults;

  protected ResultSet rs = null;
  protected int result = 0;
  protected static final ArrayList<String>[] paramValues = new ArrayList[2];

//------------------------------------------------------------------------------
// statistics task
//------------------------------------------------------------------------------

  /**
   * TASK to register the useCase6 performance statistics object.
   */
  public static void openStatisticsTask() {
    UseCase6Client c = new UseCase6Client();
    c.openStatistics();
  }

  private void openStatistics() {
    this.useCase6stats = getUseCase6Stats();
    if (this.useCase6stats == null) {
      this.useCase6stats = UseCase6Stats.getInstance();
      RemoteTestModule.openClockSkewStatistics();
    }
    setUseCase6Stats(this.useCase6stats);
  }

  /**
   * TASK to unregister the performance statistics object.
   */
  public static void closeStatisticsTask() {
    UseCase6Client c = new UseCase6Client();
    c.initHydraThreadLocals();
    c.closeStatistics();
    c.updateHydraThreadLocals();
  }

  protected void closeStatistics() {
    MasterController.sleepForMs(2000);
    if (this.useCase6stats != null) {
      RemoteTestModule.closeClockSkewStatistics();
      this.useCase6stats.close();
    }
  }

//------------------------------------------------------------------------------
// ddl
//------------------------------------------------------------------------------

  public static void executeDDLTask()
      throws FileNotFoundException, IOException, SQLException {
    UseCase6Client c = new UseCase6Client();
    c.initialize();
    Vector<String> clientNames = TestConfig.getInstance().getClientNames();
//    TestTask t = TestConfig.getInstance().getTasks();
    for (String clientName : clientNames) {
      Log.getLogWriter().info("Client Name is ::" + clientName);
      if ((clientName.startsWith("newstore"))) {
        numServers++;
        UseCase6BB.getBB().getSharedCounters().increment(UseCase6BB.numServersInBB);
      }
    }
    Log.getLogWriter().info("swati--Number of servers to be started in the task::" + numServers);
    int numServersInBB = (int) UseCase6BB.getBB().getSharedCounters().read(UseCase6BB.numServersInBB);
    Log.getLogWriter().info("swati--Number of servers in BB::" + numServersInBB);
    if (c.sttgid == 0) {
      c.executeDDL();
    }
  }

  private void executeDDL()
      throws FileNotFoundException, IOException, SQLException {
    String fn = getDDLFile(UseCase6Prms.getDDLFile());
    List<String> stmts = getDDLStatements(fn);
    for (String stmt : stmts) {
      if (this.queryAPI == QueryPrms.GFXD) {
        if (stmt.contains("partition")) {
          stmt += " buckets " + this.numBuckets;
        }
      }
      Log.getLogWriter().info("Executing DDL: " + stmt);
      try {
        execute(stmt, this.connection);
      } catch (SQLException e) {
        if (stmt.contains("DROP") &&
            e.getMessage().indexOf("does not exist") == -1 && // GFXD/MYSQL
            e.getMessage().indexOf("Unknown table ") == -1) { // ORACLE
          throw e;
        }
      }
      commitDDL();
    }
  }

  private ResultSet execute(String stmt, Connection conn)
      throws SQLException {
    if (this.logQueries) {
      Log.getLogWriter().info("Executing: " + stmt + " on: " + conn);
    }
    ResultSet rs = null;
    Statement s = conn.createStatement();
    boolean result = s.execute(stmt);
    if (result == true) {
      rs = s.getResultSet();
    }
    if (this.logQueries) {
      Log.getLogWriter().info("Executed: " + stmt + " on: " + conn);
    }
    s.close();
    return rs;
  }

  private String getDDLFile(String fname) {
    String fn = "$JTESTS/cacheperf/comparisons/gemfirexd/useCase6/ddl/" + fname;
    String newfn = EnvHelper.expandEnvVars(fn);
    Log.getLogWriter().info("DDL file: " + newfn);
    return newfn;
  }

  private List<String> getDDLStatements(String fn)
      throws FileNotFoundException, IOException {
    Log.getLogWriter().info("Reading statements from " + fn);
    String text = FileUtil.getText(fn).trim();
    StringTokenizer tokenizer = new StringTokenizer(text, ";", false);
    List<String> stmts = new ArrayList();
    while (tokenizer.hasMoreTokens()) {
      String stmt = tokenizer.nextToken().trim();
      stmts.add(stmt);
    }
    Log.getLogWriter().info("Read statements: " + stmts);
    return stmts;
  }

  private void commitDDL() {
    if (this.queryAPI != QueryPrms.GFXD) {
      try {
        this.connection.commit();
      } catch (SQLException e) {
        throw new QueryPerfException("Commit failed: " + e);
      }
    } // GFXD does not need to commit DDL
  }

//------------------------------------------------------------------------------
// support
//------------------------------------------------------------------------------

  public static void dumpBucketsHook() {
    String clientName = RemoteTestModule.getMyClientName();
    // @todo rework this to determine if this is a datahost
    if (clientName.contains("server")
        || clientName.contains("sender") || clientName.contains("receiver")
        || clientName.contains("dbsync") || clientName.contains("prdata")) {
      Log.getLogWriter().info("Dumping local buckets");
      ResultSetHelper.dumpLocalBucket();
      Log.getLogWriter().info("Dumped local buckets");
    }
  }

  public static void dumpBucketsTask() {
    UseCase6Client c = new UseCase6Client();
    c.initialize();
    if (c.jid == 0) {
      Log.getLogWriter().info("Dumping local buckets");
      ResultSetHelper.dumpLocalBucket();
      Log.getLogWriter().info("Dumped local buckets");
    }
  }

//------------------------------------------------------------------------------
// hydra thread locals
//------------------------------------------------------------------------------

  protected void initHydraThreadLocals() {
    super.initHydraThreadLocals();
    this.useCase6stats = getUseCase6Stats();
  }

  protected void updateHydraThreadLocals() {
    super.updateHydraThreadLocals();
    setUseCase6Stats(this.useCase6stats);
  }

  /**
   * Gets the per-thread UseCase6Stats wrapper instance.
   */
  protected UseCase6Stats getUseCase6Stats() {
    useCase6stats = (UseCase6Stats)localuseCase6stats.get();
    return useCase6stats;
  }

  /**
   * Sets the per-thread UseCase6Stats wrapper instance.
   */
  protected void setUseCase6Stats(UseCase6Stats useCase6stats) {
    localuseCase6stats.set(useCase6stats);
  }

//------------------------------------------------------------------------------
// OVERRIDDEN METHODS
//------------------------------------------------------------------------------

  protected void initLocalParameters() {
    super.initLocalParameters();

    this.logQueries = QueryPrms.logQueries();
    this.logQueryResults = QueryPrms.logQueryResults();
  }

  protected String nameFor(int name) {
    switch (name) {
      case TRANSACTIONS:
        return TRANSACTIONS_NAME;
    }
    return super.nameFor(name);
  }

  protected boolean getLogQueries() {
    return this.logQueries;
  }

  protected void setLogQueries(boolean b) {
    this.logQueries = b;
  }

  public static void generateAndLoadDataTaskForSmart() throws SQLException {
//    Set<Integer> threadIDsInBB = (Set<Integer>) UseCase6BB.getBB().getSharedMap().get(UseCase6BB.startFabricServerTask_threadID);
//    int size = (int) UseCase6BB.getBB().getSharedMap().get(UseCase6BB.numThreadsInStartStoreTask);
    String stringSize = (String) UseCase6BB.getBB().getSharedMap().get(UseCase6BB.numThreadsInStartStoreTask);
    int size = Integer.parseInt(stringSize);
    String serverStatus = (String) UseCase6BB.getBB().getSharedMap().get(UseCase6BB.startFabricServerTask_Status);
    Log.getLogWriter().info("");
    if (serverStatus.equalsIgnoreCase("Generate_Data")) {
      UseCase6Client c = new UseCase6Client();
      c.initialize();
      if (c.ttgid == 0) {
        c.generateAndLoadData();
        Log.getLogWriter().info("swati--generateAndLoadDataTaskForSmart task executed...");
        if (QueryPerfClient.getTotalThreds() >= size) {
          UseCase6BB.getBB().getSharedMap().put(UseCase6BB.startFabricServerTask_Status, "Trigger_Rebalance");
          Log.getLogWriter().info("swati--startFabricServerTask_Status value in generateAndLoadDataTaskForSmart is :: " + UseCase6BB.getBB().getSharedMap().get(UseCase6BB.startFabricServerTask_Status));
        } else {
          UseCase6BB.getBB().getSharedMap().put(UseCase6BB.startFabricServerTask_Status, "startServer");
          Log.getLogWriter().info("swati--startFabricServerTask_Status value in generateAndLoadDataTaskForSmart is :: " + UseCase6BB.getBB().getSharedMap().get(UseCase6BB.startFabricServerTask_Status));
        }
      }
    }
  }

  public static void generateAndLoadDataTask() throws SQLException {
    UseCase6Client c = new UseCase6Client();
    c.initialize();
    if (c.ttgid == 0) {
      c.generateAndLoadData();
    }
  }

  protected void generateAndLoadDataAfterRebalanceDone() throws SQLException {
    UseCase6Client c = new UseCase6Client();
    c.initialize();
    if (c.ttgid == 0) {
      c.generateAndLoadData();
    }
  }

  private String getMapperFileAbsolutePath() {
    String mPath = System.getProperty("JTESTS") + "/"
        + TestConfig.tab().stringAt(UseCase6Prms.mapperFile, null);
    return mPath;
  }

  public void generateAndLoadData() throws SQLException {
    // this will init DataGenerator, create CSVs for populate and create rows for inserts
    String[] tableNames = UseCase6Prms.getTableNames();
    ;
    int[] rowCounts = UseCase6Prms.getInitialRowCountToPopulateTable();
    String mapper = getMapperFileAbsolutePath();
    DataGeneratorHelper.initDataGenerator(mapper, tableNames, rowCounts, this.connection);

    int prevCount = SqlUtilityHelper.getRowsInTable(tableNames[0], this.connection);
    populateTables(this.connection);
    int newCount = SqlUtilityHelper.getRowsInTable(tableNames[0], this.connection);

    DataGeneratorHelper.clearDataGenerator();
    if( (newCount - prevCount) != rowCounts[0]) {
      throw new TestException("Row count mismatch. Expected (" + newCount + " - " + prevCount + ") = " + rowCounts[0]);
    }

    //executequery("SELECT * FROM OLTP_PNP_Subscriptions", this.connection);
  }

  public void populateTables(Connection gConn) {
    //populate gfxd tables from CSVs
    int totalThreads = SqlUtilityHelper.totalTaskThreads();
    int ttgid = SqlUtilityHelper.ttgid();
    String[] tableNames = UseCase6Prms.getTableNames();
    Log.getLogWriter().info("Populating tables " + Arrays.toString(tableNames) + " with totalThreads = " + totalThreads + " ttgid = " + ttgid);
    for (int i = 0; i < tableNames.length; i++) {
      if ((ttgid % totalThreads == 0)) {       // thus make is single threaded and should not import if data is already in table
        populateTables(tableNames[i], gConn);
      }
    }
  }

  private void populateTables(final String fullTableName, final Connection gConn) {
    String[] names = fullTableName.trim().split("\\.");
    String schemaName = names[0];
    String tableName = names[1];
    String csvFilePath = DataGeneratorBB.getCSVFileName(fullTableName);

    importTablesToGfxd(gConn, schemaName, tableName, csvFilePath);
    commitDDL();
  }

  private void importTablesToGfxd(Connection conn, String schema,
      String table, String csvPath) {

    Log.getLogWriter().info("grep -r 'Gfxd - Data Population Started for ' ." +
            "" + schema + "." + table + " using " + csvPath);
    String delimitor = ",";
    String procedure = "sql.datagen.ImportOraDG";
    String importTable = "CALL SYSCS_UTIL.IMPORT_DATA_EX(?, ?, null,null, ?, ?, NULL, NULL, 0, 0, 10, 0, ?, null)";
    try {
      CallableStatement cs = conn.prepareCall(importTable);
      cs.setString(1, schema.toUpperCase());
      cs.setString(2, table.toUpperCase());
      cs.setString(3, csvPath);
      cs.setString(4, delimitor);
      cs.setString(5, procedure);
      cs.execute();
    } catch (SQLException se) {
      SQLHelper.handleSQLException(se);
    }

    Log.getLogWriter().info("Gfxd - Data Population Completed for " + schema + "." + table);

  }

  public static void storeUniqueDataFromTableTask() throws SQLException {
    UseCase6Client c = new UseCase6Client();
    c.initialize();
    c.storeUniqueDataFromTable();
  }

  private void storeUniqueDataFromTable() throws SQLException {
    paramValues[0] = new ArrayList<>();
    paramValues[1] = new ArrayList<>();
    final PreparedStatement ps = this.connection.prepareStatement("select distinct msisdn, walletname from OLTP_PNP_Subscriptions");
    final ResultSet paramRS = ps.executeQuery();
    Log.getLogWriter().info("populating msisdn and walletnames");
    int counter = 0;
    while (paramRS.next()) {
      paramValues[0].add(paramRS.getString(1)); // msisdn
      paramValues[1].add(paramRS.getString(2)); // walletname
      counter++;
      if (counter % 50000 == 0)
        Log.getLogWriter().info("done loading " + counter + " items");
    }

    Log.getLogWriter().info("Total loaded msisdn and wallentname items = " + counter);
    paramRS.close();
    ps.close();
  }

  public static void selectAndUpdateTask() throws SQLException {
    UseCase6Client c = new UseCase6Client();
    //c.initialize();
    c.initialize(TRANSACTIONS);
    //c.useCase6SelectAndUpdateTask();
    c.useCase6SelectAndUpdate();
  }
  private void useCase6SelectAndUpdateTask() throws SQLException {
//    useCase6SelectAndUpdate();
    Random rand = new Random();
    do {
      executeTaskTerminator(); // does not commit
      executeWarmupTerminator(); // does not commit
      //useCase6SelectAndUpdate();
      ++this.batchCount;
      ++this.count;
      ++this.keyCount; // not really needed
    } while (!executeBatchTerminator()); // does not commit
  }

  private void useCase6SelectAndUpdate() throws SQLException {
    PreparedStatement update = null;
    PreparedStatement select = null;
    final Connection connection = QueryUtil.gfxdClientSetup(this);
    //Random rand = new Random();
    //int val = rand.nextInt(paramValues[0].size() - 1);
    Log.getLogWriter().info("useCase6SelectAndUpdateTask v2 - done loading " + paramValues[0].size() + " items");
    for (int iter = 0; iter < paramValues[0].size(); ) {
      long start = this.useCase6stats.startTransaction();
      select = connection.prepareStatement(selstm);
      update = connection.prepareStatement(updstm);
      select.setString(1, paramValues[0].get(iter));
      select.setString(2, paramValues[1].get(iter));
      ResultSet sel = select.executeQuery();
      sel.next();
      final String id = sel.getString(1);
      //sel.close();
      update.clearParameters();
      final long tpsBegin = System.currentTimeMillis();
      final Timestamp ts = new Timestamp(tpsBegin);
      update.setTimestamp(1, ts);
      update.setString(2, id);
      try {
        update.executeUpdate();
      } catch (SQLException txe) {
        if ("X0Z02".equals(txe.getSQLState())) {
          this.useCase6stats.endTransaction(start, 0);
          continue;
        }
        txe.printStackTrace();
      }
      //Log.getLogWriter().info("Updated table with id " + id);
      connection.commit();
      this.useCase6stats.endTransaction(start, 0);
      iter++;
      if (select != null) {
        select.close();
        select = null;
      }
      if (update != null) {
        update.close();
        update = null;
      }
    }

  }

  public static void workLoadGeneratorTask() throws SQLException {
    String stringSize = (String) UseCase6BB.getBB().getSharedMap().get(UseCase6BB.numThreadsInStartStoreTask);
    int size = Integer.parseInt(stringSize);
    Log.getLogWriter().info("numThreadsinStartStoreTask ::" + size);
    int totalServerThreadsExecuted = QueryPerfClient.getTotalThreds();
    Log.getLogWriter().info("Total newStore Started retrieved from QueryPerfClient.getTotalThreds() are ::" + totalServerThreadsExecuted);
    if (QueryPerfClient.getTotalThreds() >= size) {
      return;
    }
    UseCase6Client c = new UseCase6Client();
    //c.initialize();
    c.initialize(TRANSACTIONS);
    //c.useCase6SelectAndUpdateTask();
    c.useCase6workLoadGeneratorTask(c);
  }

  private void useCase6workLoadGeneratorTask(UseCase6Client c) throws SQLException {
    PreparedStatement update = null;
    PreparedStatement select = null;
    final Connection connection = getConnection();
    select = connection.prepareStatement(selstm);
    update = connection.prepareStatement(updstm);
    //Random rand = new Random();
    //int val = rand.nextInt(paramValues[0].size() - 1);
    //Log.getLogWriter().info("useCase6SelectAndUpdateTask - done loading " + paramValues[0] + " items");
    for (int iter = 0; iter < paramValues[0].size(); ) {
//      long start = this.useCase6stats.startTransaction();
      select.setString(1, paramValues[0].get(iter));
      select.setString(2, paramValues[1].get(iter));
      ResultSet sel = select.executeQuery();
      sel.next();
      final String id = sel.getString(1);
      sel.close();
      update.clearParameters();
      final long tpsBegin = System.currentTimeMillis();
      final Timestamp ts = new Timestamp(tpsBegin);
      update.setTimestamp(1, ts);
      update.setString(2, id);
      try {
        update.executeUpdate();
      } catch (SQLException txe) {
        if ("X0Z02".equals(txe.getSQLState())) {
//          this.useCase6stats.endTransaction(start, 0);
          continue;
        }
        txe.printStackTrace();
      }
      //Log.getLogWriter().info("Updated table with id " + id);
//        this.connection.commit();
      connection.commit();
//      this.useCase6stats.endTransaction(start, 0);
      iter++;
    }
    if (select != null) {
      select.close();
      select = null;
    }
    if (update != null) {
      update.close();
      update = null;
    }

  }

}
