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
package com.gemstone.gemfire.internal.cache.control;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.SystemFailure;
import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.query.internal.QueryMonitor;
import com.gemstone.gemfire.distributed.internal.ProcessorKeeper21;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.DiskCapacityMonitor;
import com.gemstone.gemfire.internal.GemFireStatSampler;
import com.gemstone.gemfire.internal.LocalStatListener;
import com.gemstone.gemfire.internal.LogWriterImpl;
import com.gemstone.gemfire.internal.SetUtils;
import com.gemstone.gemfire.internal.StatisticsImpl;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.control.InternalResourceManager.ResourceType;
import com.gemstone.gemfire.internal.cache.control.MemoryThresholds.MemoryState;
import com.gemstone.gemfire.internal.cache.control.ResourceAdvisor.ResourceManagerProfile;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.shared.SystemProperties;
import com.gemstone.gemfire.internal.util.LogService;

/**
 * Allows for the setting of eviction and critical thresholds. These thresholds
 * are compared against current heap usage and, with the help of {#link
 * InternalResourceManager}, dispatches events when the thresholds are crossed.
 * Gathering memory usage information from the JVM is done using a listener on
 * the MemoryMXBean, by polling the JVM and as a listener on GemFire Statistics
 * output in order to accommodate differences in the various JVMs.
 * 
 * @author Kirk Lund
 * @author Mitch Thomas
 * @author Swapnil Bawaskar
 * @author David Hoots
 * @since 7.5
 */
public final class HeapMemoryMonitor implements NotificationListener,
    ResourceMonitor {

  // Allow for an unknown heap pool for VMs we may support in the future.
  private static final String HEAP_POOL = System
      .getProperty("gemfire.ResourceManager.HEAP_POOL");
  private static final String HEAP_EDEN_POOL = System
      .getProperty("gemfire.ResourceManager.HEAP_EDEN_POOL");
  private static final String HEAP_SURVIVOR_POOL = System
      .getProperty("gemfire.ResourceManager.HEAP_SURVIVOR_POOL");

  // Property for setting the JVM polling interval (below)
  public static final String POLLER_INTERVAL_PROP = "gemfire.heapPollerInterval";
  
   // Internal for polling the JVM for changes in heap memory usage.
  private static final int POLLER_INTERVAL =
       Integer.getInteger(POLLER_INTERVAL_PROP, 500);

  // This holds a new event as it transitions from updateStateAndSendEvent(...) to fillInProfile()
  private ThreadLocal<MemoryEvent> upcomingEvent = new ThreadLocal<MemoryEvent>();

  private ScheduledExecutorService pollerExecutor;

  private MemoryEventSender mes;

  // Listener for heap memory usage as reported by the Cache stats.
  private final LocalStatListener statListener = new LocalHeapStatListener();

  private volatile MemoryState currentState = MemoryState.DISABLED;

  //Set when startMonitoring() and stopMonitoring() are called
  private Boolean started = false;
  
  // Set to true when setEvictionThreshold(...) is called.
  private boolean hasEvictionThreshold = false;
  
   // Only change state when these counters exceed {@link HeapMemoryMonitor#memoryStateChangeTolerance}
  private int criticalToleranceCounter;
  private int evictionToleranceCounter;
  
  private final InternalResourceManager resourceManager;
  private final ResourceAdvisor resourceAdvisor;
  private final GemFireCacheImpl cache;
  private final ResourceManagerStats stats;
  
  private static boolean testDisableMemoryUpdates = false;
  private static long testBytesUsedForThresholdSet = -1;

  /**
   * Number of eviction or critical state changes that have to occur before the
   * event is delivered. This was introduced because we saw sudden memory usage
   * spikes in jrockit VM.
   * 
   * public for test code
   */
  public static final int memoryStateChangeTolerance;
  static {
    String vendor = System.getProperty("java.vendor");
    if (vendor.contains("Sun") || vendor.contains("Oracle")) {
      memoryStateChangeTolerance = Integer.getInteger("gemfire.memoryEventTolerance",1);
    } else {
      memoryStateChangeTolerance = Integer.getInteger("gemfire.memoryEventTolerance",5);
    }
  }
  
  // JVM MXBean used to report changes in heap memory usage
  private static final MemoryPoolMXBean tenuredMemoryPoolMXBean;
  private static final MemoryPoolMXBean edenMemoryPoolMXBean;
  private static final MemoryPoolMXBean survivorPoolMXBean;
  // Calculated value for the amount of JVM tenured heap memory available.
  private static final long tenuredPoolMaxMemory;
  private static final long edenAndSurvivorPoolMaxMemory;

  static {
    MemoryPoolMXBean matchingTenuredMemoryPoolMXBean = null;
    MemoryPoolMXBean matchingEdenMemoryPoolMXBean = null;
    MemoryPoolMXBean matchingSurvivorPoolMXBean = null;
    for (MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
      if (isTenured(memoryPoolMXBean)) {
        if (memoryPoolMXBean.isUsageThresholdSupported()) {
          matchingTenuredMemoryPoolMXBean = memoryPoolMXBean;
        }
      }
      else if (isEden(memoryPoolMXBean)) {
        matchingEdenMemoryPoolMXBean = memoryPoolMXBean;
      }
      else if (isSurvivor(memoryPoolMXBean)) {
        matchingSurvivorPoolMXBean = memoryPoolMXBean;
      }
    }

    tenuredMemoryPoolMXBean = matchingTenuredMemoryPoolMXBean;
    edenMemoryPoolMXBean = matchingEdenMemoryPoolMXBean;
    survivorPoolMXBean = matchingSurvivorPoolMXBean;

    if (tenuredMemoryPoolMXBean == null) {
      LogWriterI18n logger = LogService.logger();
      logger.error(LocalizedStrings.HeapMemoryMonitor_NO_POOL_FOUND_POOLS_0, getAllMemoryPoolNames());
    }

    /*
     * Calculates the max memory for the tenured pool. Works around JDK bug:
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7078465 by getting max
     * memory from runtime and subtracting all other heap pools from it.
     */
    if (tenuredMemoryPoolMXBean != null
        && tenuredMemoryPoolMXBean.getUsage().getMax() != -1) {
      tenuredPoolMaxMemory = tenuredMemoryPoolMXBean.getUsage().getMax();
    }
    else {
      long calculatedMaxMemory = Runtime.getRuntime().maxMemory();
      List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
      for (MemoryPoolMXBean p : pools) {
        if (p.getType() == MemoryType.HEAP && p.getUsage().getMax() != -1) {
          calculatedMaxMemory -= p.getUsage().getMax();
        }
      }
      tenuredPoolMaxMemory = calculatedMaxMemory;
    }

    long edenAndSurvivorPoolMax = 0;
    if (edenMemoryPoolMXBean != null) {
      edenAndSurvivorPoolMax += Math.max(0, edenMemoryPoolMXBean.getUsage()
          .getMax());
    }
    if (survivorPoolMXBean != null) {
      edenAndSurvivorPoolMax += Math.max(0, survivorPoolMXBean.getUsage()
          .getMax());
    }
    edenAndSurvivorPoolMaxMemory = edenAndSurvivorPoolMax;
  }

  private volatile MemoryThresholds thresholds = new MemoryThresholds(
      tenuredPoolMaxMemory + edenAndSurvivorPoolMaxMemory);
  private volatile MemoryEvent mostRecentEvent = new MemoryEvent(ResourceType.HEAP_MEMORY, MemoryState.DISABLED,
      MemoryState.DISABLED, null, 0L, true, this.thresholds);

  /**
   * Determines if the name of the memory pool MXBean provided matches a list of
   * known tenured pool names.
   * 
   * Package private for testing.
   * 
   * @param memoryPoolMXBean
   *          The memory pool MXBean to check.
   * @return True if the pool name matches a known tenured pool name, false
   *         otherwise.
   */
  static boolean isTenured(MemoryPoolMXBean memoryPoolMXBean) {
    if (memoryPoolMXBean.getType() != MemoryType.HEAP) {
      return false;
    }
    
    String name = memoryPoolMXBean.getName();
    
    return name.equals("CMS Old Gen")     // Sun Concurrent Mark Sweep GC
        || name.equals("PS Old Gen")      // Sun Parallel GC
        || name.equals("G1 Old Gen")      // Sun G1 GC
        || name.equals("Old Space")       // BEA JRockit 1.5, 1.6 GC
        || name.equals("Tenured Gen")     // Hitachi 1.5 GC
        || name.equals("Java heap")       // IBM 1.5, 1.6 GC
        
        // Allow an unknown pool name to monitor
        || (HEAP_POOL != null && name.equals(HEAP_POOL));
  }

  /**
   * Determines if the name of the memory pool MXBean provided matches a list of
   * known young generation pool names.
   * 
   * @param memoryPoolMXBean
   *          The memory pool MXBean to check.
   * @return True if the pool name matches a known young generation pool name,
   *         false otherwise.
   */
  static boolean isEden(MemoryPoolMXBean memoryPoolMXBean) {
    if (memoryPoolMXBean.getType() != MemoryType.HEAP) {
      return false;
    }

    String name = memoryPoolMXBean.getName();

    return name.equals("Par Eden Space")  // Oracle ParNew with Concurrent Mark Sweep GC
        || name.equals("PS Eden Space")   // Oracle Parallel GC
        || name.equals("G1 Eden")         // Oracle G1 GC
        //|| name.equals("Nursery")       // BEA JRockit 1.5, 1.6 GC
        || name.equals("Eden Space")      // Hitachi 1.5 GC
        // Allow an unknown pool name to monitor
        || (HEAP_EDEN_POOL != null && name.equals(HEAP_EDEN_POOL));
  }

  /**
   * Determines if the name of the memory pool MXBean provided matches a list of
   * known survivor space pool names.
   * 
   * @param memoryPoolMXBean
   *          The memory pool MXBean to check.
   * @return True if the pool name matches a known survivor space pool name,
   *         false otherwise.
   */
  static boolean isSurvivor(MemoryPoolMXBean memoryPoolMXBean) {
    if (memoryPoolMXBean.getType() != MemoryType.HEAP) {
      return false;
    }

    String name = memoryPoolMXBean.getName();

    return name.equals("Par Survivor Space")  // Oracle Concurrent Mark Sweep GC
        || name.equals("PS Survivor Space")   // Oracle Parallel GC
        || name.equals("G1 Survivor")         // Oracle G1 GC
        || name.equals("Survivor Space")      // Hitachi 1.5 GC
        // Allow an unknown pool name to monitor
        || (HEAP_SURVIVOR_POOL != null && name.equals(HEAP_SURVIVOR_POOL));
  }

  HeapMemoryMonitor(final InternalResourceManager resourceManager, final GemFireCacheImpl cache, final ResourceManagerStats stats) {
    this.resourceManager = resourceManager;
    this.resourceAdvisor = (ResourceAdvisor) cache.getDistributionAdvisor();
    this.cache = cache;
    this.stats = stats;
  }

  /**
   * Returns the tenured pool MXBean or throws an IllegaleStateException if one
   * couldn't be found.
   */
  public static MemoryPoolMXBean getTenuredMemoryPoolMXBean() {
    if (tenuredMemoryPoolMXBean != null) {
      return tenuredMemoryPoolMXBean;
    }

    throw new IllegalStateException(LocalizedStrings.HeapMemoryMonitor_NO_POOL_FOUND_POOLS_0
        .toLocalizedString(getAllMemoryPoolNames()));
  }

  /**
   * Returns the eden pool MXBean or null if one couldn't be found.
   */
  public static MemoryPoolMXBean getEdenMemoryPoolMXBean() {
    return edenMemoryPoolMXBean;
  }

  /**
   * Returns the survivor space MXBean or null if one couldn't be found.
   */
  public static MemoryPoolMXBean getSurvivorPoolMXBean() {
    return survivorPoolMXBean;
  }

  /**
   * Returns the names of all available memory pools as a single string.
   */
  private static String getAllMemoryPoolNames() {
    StringBuilder builder = new StringBuilder("[");

    for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getMemoryPoolMXBeans()) {
      builder.append("(Name=").append(memoryPoolBean.getName()).append(";Type=").append(memoryPoolBean.getType()).append(
          ";UsageThresholdSupported=").append(memoryPoolBean.isUsageThresholdSupported()).append("), ");
    }
    
    if (builder.length() > 1) {
      builder.setLength(builder.length() - 2);
    }
    builder.append("]");
    
    return builder.toString();
  }
 
  /**
   * Monitoring is done using a combination of data from the JVM and statistics
   * collected from the cache. A usage threshold is set on the MemoryMXBean of
   * the JVM to get notifications when the JVM crosses the eviction or critical
   * thresholds. A separate usage collection is done either by setting up a
   * listener on the cache stats or polling of the JVM, depending on whether
   * stats have been enabled. This separate collection is done to return the
   * state of the heap memory back to a normal state when memory has been freed.
   */
  private void startMonitoring() {
    synchronized (this) {
      if (this.started) {
        return;
      }

      final boolean statListenerStarted = startCacheStatListener();

      if (!statListenerStarted) {
        startMemoryPoolPoller();
      }

      startJVMThresholdListener();
      
      this.started = true;
    }
  }
  
  /**
   * Stops all three mechanisms from monitoring heap usage.
   */
 @Override
public void stopMonitoring() {
    synchronized (this) {
      if (!this.started) {
        return;
      }
      
      // Stop the poller
      this.resourceManager.stopExecutor(this.pollerExecutor);

      // Stop the JVM threshold listener
      NotificationEmitter emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
      try {
        emitter.removeNotificationListener(this, null, null);
        this.cache.getLoggerI18n().fine("Removed Memory MXBean notification listener" + this);
      } catch (ListenerNotFoundException e) {
        this.cache.getLoggerI18n().fine("This instance '" + toString() + "' was not registered as a Memory MXBean listener");
      }

      // Stop the stats listener
      final GemFireStatSampler sampler = this.cache.getDistributedSystem().getStatSampler();
      if (sampler != null) {
        sampler.removeLocalStatListener(this.statListener);
      }

      // Stop the memory event sender
      if (this.mes != null) {
        this.mes.stopSender();
      }
    }
  }
  
  /**
   * Start a listener on the cache stats to monitor memory usage.
   * 
   * @return True of the listener was correctly started, false otherwise.
   */
  private boolean startCacheStatListener() {
    final GemFireStatSampler sampler = this.cache.getDistributedSystem().getStatSampler();
    if (sampler == null) {
      return false;
    }
    
    try {
      sampler.waitForInitialization();
      String tenuredPoolName = getTenuredMemoryPoolMXBean().getName();
      String edenPoolName = getEdenMemoryPoolMXBean() != null
          ? getEdenMemoryPoolMXBean().getName() : null;
      String survivorPoolName = getSurvivorPoolMXBean() != null
          ? getSurvivorPoolMXBean().getName() : null;
      List list = this.cache.getDistributedSystem().getStatsList();
      synchronized (list) {
        for (Object o : list) {
          if (o instanceof StatisticsImpl) {
            StatisticsImpl si = (StatisticsImpl) o;
            if ((si.getTextId().contains(tenuredPoolName)
                || (edenPoolName != null && si.getTextId().contains(edenPoolName))
                || (survivorPoolName != null && si.getTextId().contains(
                    survivorPoolName)))
                && si.getType().getName().contains("PoolStats")) {
              sampler.addLocalStatListener(this.statListener, si, "currentUsedMemory");
              if (this.cache.getLoggerI18n().fineEnabled()) {
                this.cache.getLoggerI18n().fine("Registered stat listener for " + si.getTextId());
              }

              return true;
            }
          }
        }
      }
    } catch (InterruptedException iex) {
      Thread.currentThread().interrupt();
      this.cache.getCancelCriterion().checkCancelInProgress(iex);
    }
    
    return false;
  }
  
  /**
   * Start a separate thread for polling the JVM for heap memory usage.
   */
  private void startMemoryPoolPoller() {
    if (tenuredMemoryPoolMXBean == null) {
      return;
    }
    
    final ThreadGroup threadGroup = LogWriterImpl.createThreadGroup("HeapPoller", this.cache.getLoggerI18n());
    final ThreadFactory threadFactory = new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(threadGroup, r, "GemfireHeapPoller");
        thread.setDaemon(true);
        return thread;
      }
    };
    
    this.pollerExecutor = Executors.newScheduledThreadPool(1, threadFactory);
    this.pollerExecutor.scheduleAtFixedRate(new HeapPoller(), POLLER_INTERVAL, POLLER_INTERVAL, TimeUnit.MILLISECONDS);
    
    if (this.cache.getLoggerI18n().fineEnabled()) {
      this.cache.getLoggerI18n().fine("Started GemfireHeapPoller to poll the heap every " + POLLER_INTERVAL + " milliseconds");
    }
  }
  
  void setCriticalThreshold(final float criticalThreshold) {
    synchronized (this) {
      // If the threshold isn't changing then don't do anything.
      if (criticalThreshold == this.thresholds.getCriticalThreshold()) {
        return;
      }

      // Do some basic sanity checking on the new threshold
      if (criticalThreshold > 100.0f || criticalThreshold < 0.0f) {
        throw new IllegalArgumentException(LocalizedStrings.MemoryThresholds_CRITICAL_PERCENTAGE_GT_ZERO_AND_LTE_100
            .toLocalizedString());
      }
      if (getTenuredMemoryPoolMXBean() == null) {
        throw new IllegalStateException(LocalizedStrings.HeapMemoryMonitor_NO_POOL_FOUND_POOLS_0
            .toLocalizedString(getAllMemoryPoolNames()));
      }
      if (criticalThreshold != 0 && this.thresholds.isEvictionThresholdEnabled()
          && criticalThreshold <= this.thresholds.getEvictionThreshold()) {
        throw new IllegalArgumentException(LocalizedStrings.MemoryThresholds_CRITICAL_PERCENTAGE_GTE_EVICTION_PERCENTAGE
            .toLocalizedString());
      }
       
      this.cache.setQueryMonitorRequiredForResourceManager(criticalThreshold != 0);

      this.thresholds = new MemoryThresholds(this.thresholds.getMaxMemoryBytes(), criticalThreshold, this.thresholds
          .getEvictionThreshold());

      if (testBytesUsedForThresholdSet != -1) {
        updateStateAndSendEvent(testBytesUsedForThresholdSet);
      } else {
        updateStateAndSendEvent(getBytesUsed());
      }
      
      // Start or stop monitoring based upon whether a threshold has been set
      if (this.thresholds.isEvictionThresholdEnabled() || this.thresholds.isCriticalThresholdEnabled()) {
        startMonitoring();
      } else if (!this.thresholds.isEvictionThresholdEnabled() && !this.thresholds.isCriticalThresholdEnabled()) {
        stopMonitoring();
      }

      this.stats.changeCriticalThreshold(this.thresholds.getCriticalThresholdBytes());
    }
  }

  float getCriticalThreshold() {
    return this.thresholds.getCriticalThreshold();
  }
  
  public boolean hasEvictionThreshold() {
    return this.hasEvictionThreshold;
  }
  
  void setEvictionThreshold(final float evictionThreshold) {
    this.hasEvictionThreshold = true;
    
    synchronized (this) {
      // If the threshold isn't changing then don't do anything.
      if (evictionThreshold == this.thresholds.getEvictionThreshold()) {
        return;
      }

      // Do some basic sanity checking on the new threshold
      if (evictionThreshold > 100.0f || evictionThreshold < 0.0f) {
        throw new IllegalArgumentException(LocalizedStrings.MemoryThresholds_EVICTION_PERCENTAGE_GT_ZERO_AND_LTE_100
            .toLocalizedString());
      }
      if (getTenuredMemoryPoolMXBean() == null) {
        throw new IllegalStateException(LocalizedStrings.HeapMemoryMonitor_NO_POOL_FOUND_POOLS_0
            .toLocalizedString(getAllMemoryPoolNames()));
      }
      if (evictionThreshold != 0 && this.thresholds.isCriticalThresholdEnabled()
          && evictionThreshold >= this.thresholds.getCriticalThreshold()) {
        throw new IllegalArgumentException(LocalizedStrings.MemoryMonitor_EVICTION_PERCENTAGE_LTE_CRITICAL_PERCENTAGE
            .toLocalizedString());
      }

      this.thresholds = new MemoryThresholds(this.thresholds.getMaxMemoryBytes(), this.thresholds.getCriticalThreshold(),
          evictionThreshold);

      if (testBytesUsedForThresholdSet != -1) {
        updateStateAndSendEvent(testBytesUsedForThresholdSet);
      } else {
        updateStateAndSendEvent(getBytesUsed());
      }

      // Start or stop monitoring based upon whether a threshold has been set
      if (this.thresholds.isEvictionThresholdEnabled() || this.thresholds.isCriticalThresholdEnabled()) {
        startMonitoring();
      } else if (!this.thresholds.isEvictionThresholdEnabled() && !this.thresholds.isCriticalThresholdEnabled()) {
        stopMonitoring();
      }

      this.stats.changeEvictionThreshold(this.thresholds.getEvictionThresholdBytes());
    }
  }
  
  public float getEvictionThreshold() {
    return this.thresholds.getEvictionThreshold();
  }
  
  /**
   * Compare the number of bytes used (fetched from the JVM) to the thresholds.
   * If necessary, change the state and send an event for the state change.
   */
  public final void updateStateAndSendEvent() {
    updateStateAndSendEvent(getBytesUsed());
  }

  private static final SystemProperties sysProps = SystemProperties
      .getServerInstance();
  public static boolean DELAY_MEMORY_EVENT = sysProps.getBoolean(
      "delay_memory_event", true);
  public static final int MAX_DELAY = sysProps.getInteger(
      "delay_memory_event", 5);
  private static int RECHECK_INTERVAL = 100;

  class MemoryEventSender extends Thread {
    private volatile boolean stopThread;
    private volatile boolean stopped;
    private volatile boolean interruptSamplerThread = false;

    @Override
    public void run() {
      while(!stopThread) {
        SystemFailure.checkFailure();
        if (Thread.currentThread().isInterrupted()) {
          this.interruptSamplerThread = true;
          break;
        }
        int maxLoop = MAX_DELAY * 1000 / RECHECK_INTERVAL;
        long startMillis = System.currentTimeMillis();
        boolean interrupted = checkMemoryAndSendEvent(
            getBytesUsed(), maxLoop, RECHECK_INTERVAL);
        if (interrupted) {
          this.stopThread = true;
          this.interruptSamplerThread = true;
          break;
        }
        long timeTaken = System.currentTimeMillis() - startMillis;
        // don't run this method very frequently in case everything is normal
        long toSleep = 1000 - timeTaken;
        if(toSleep > 0) {
          try {
            sleep(toSleep);
          } catch (InterruptedException e) {
            interruptSamplerThread = true;
          }
        }
      }
      stopped = true;
    }

    public void startSender() {
      this.start();
    }

    public void stopSender() {
      this.stopThread = true;
      try {
        int maxTry = 50;
        while(!stopped && maxTry-- > 0) {
          Thread.sleep(200);
        }
      } catch (InterruptedException e) {
        // ignore proper closing
      }
    }

    public boolean interruptSampler() {
      return this.interruptSamplerThread;
    }
  }

  // If the thread is interrupted return true else false
  public boolean checkMemoryAndSendEvent(long bytesUsed, int maxLoop, int recheckInterval) {
    synchronized (this) {
      int cnt = 0;
      while (cnt < maxLoop) {
        if (cnt > 0) {
          try {
            Thread.sleep(recheckInterval);
          } catch (InterruptedException ignored) {
            return true;
          }
          bytesUsed = getBytesUsed();
        }

        MemoryState oldState = this.mostRecentEvent.getState();
        MemoryState newState = this.thresholds.computeNextState(oldState, bytesUsed);
        if (oldState != newState) {
          setUsageThresholdOnMXBean(bytesUsed);

          if (!skipEventDueToToleranceLimits(oldState, newState)) {
            this.currentState = newState;

            if (!newState.isNormal() && testBytesUsedForThresholdSet == -1 && cnt < maxLoop) {
              cnt++;
              continue;
            }
            MemoryEvent event = new MemoryEvent(ResourceType.HEAP_MEMORY, oldState, newState, this.cache.getMyId(), bytesUsed, true,
                this.thresholds);

            this.upcomingEvent.set(event);
            processLocalEvent(event);
            updateStatsFromEvent(event);
          }
          break;

          // The state didn't change.  However, if the state isn't normal and the
          // number of bytes used changed, then go ahead and send the event
          // again with an updated number of bytes used.
        } else if (!oldState.isNormal() && bytesUsed != this.mostRecentEvent.getBytesUsed()) {
          cnt++;
          if (testBytesUsedForThresholdSet == -1 || cnt == maxLoop-1) {
            MemoryEvent event = new MemoryEvent(ResourceType.HEAP_MEMORY, oldState, newState, this.cache.getMyId(), bytesUsed, true,
                this.thresholds);
            this.upcomingEvent.set(event);
            processLocalEvent(event);
            break;
          }
          else {
            continue;
          }
        }
        break;
      }
    }
    return false;
  }

  /**
   * Compare the number of bytes used to the thresholds.  If necessary, change the state
   * and send an event for the state change.
   * 
   * Public for testing.
   * 
   * @param bytesUsed Number of bytes of heap memory currently used.
   */
  public void updateStateAndSendEvent(long bytesUsed) {
    this.stats.changeTenuredHeapUsed(bytesUsed);
    if (!DELAY_MEMORY_EVENT ||
        testBytesUsedForThresholdSet != -1 || testDisableMemoryUpdates) {
      checkMemoryAndSendEvent(bytesUsed, 1, -1);
    } else {
      if (mes == null) {
        mes = new MemoryEventSender();
        mes.start();
      }
      if (mes.interruptSampler()) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Update resource manager stats based upon the given event.
   * 
   * @param event
   *          Event from which to derive data for updating stats.
   */
  private void updateStatsFromEvent(MemoryEvent event) {
    if (event.isLocal()) {
      if (event.getState().isCritical() && !event.getPreviousState().isCritical()) {
        this.stats.incHeapCriticalEvents();
      } else if (!event.getState().isCritical() && event.getPreviousState().isCritical()) {
        this.stats.incHeapSafeEvents();
      }
      
      if (event.getState().isEviction() && !event.getPreviousState().isEviction()) {
        this.stats.incEvictionStartEvents();
      } else if (!event.getState().isEviction() && event.getPreviousState().isEviction()) {
        this.stats.incEvictionStopEvents();
      }
    }
  }
  
  /**
   * Populate heap memory data in the given profile.
   * 
   * @param profile
   *          Profile to populate.
   */
  @Override
  public void fillInProfile(final ResourceManagerProfile profile) {
    final MemoryEvent tempEvent = this.upcomingEvent.get();
    if (tempEvent != null) {
      this.mostRecentEvent = tempEvent;
      this.upcomingEvent.set(null);
    }
    final MemoryEvent eventToPopulate = this.mostRecentEvent;
    profile.setHeapData(eventToPopulate.getBytesUsed(), eventToPopulate.getState(), eventToPopulate.getThresholds());
  }
  
  public MemoryState getState() {
    return this.currentState;
  }
  
  public MemoryThresholds getThresholds() {
    MemoryThresholds saveThresholds = this.thresholds;
    
    return new MemoryThresholds(saveThresholds.getMaxMemoryBytes(), saveThresholds.getCriticalThreshold(),
        saveThresholds.getEvictionThreshold());
  }
  
  /**
   * Sets the usage threshold on the tenured pool to either the eviction
   * threshold or the critical threshold depending on the current number of
   * bytes used
   * 
   * @param bytesUsed
   *          Number of bytes of heap memory currently used.
   */
  private void setUsageThresholdOnMXBean(final long bytesUsed) {
    // this method has been made a no-op to fix bug 49064
  }
  
  /**
   * Register with the JVM to get threshold events.
   * 
   * Package private for testing.
   */
  void startJVMThresholdListener() {
    final MemoryPoolMXBean memoryPoolMXBean = getTenuredMemoryPoolMXBean();

    // Set collection threshold to a low value, so that we can get
    // notifications after every GC run. After each such collection
    // threshold notification we set the usage thresholds to an
    // appropriate value.
    if (!testDisableMemoryUpdates) {
      memoryPoolMXBean.setCollectionUsageThreshold(1);
      // also set for notifications from survivor space GC
      final MemoryPoolMXBean survivorPoolMXBean = getSurvivorPoolMXBean();
      if (survivorPoolMXBean != null
          && survivorPoolMXBean.isCollectionUsageThresholdSupported()) {
        survivorPoolMXBean.setCollectionUsageThreshold(1);
      }
    }
    
    final long usageThreshold = memoryPoolMXBean.getUsageThreshold();
    this.cache.getLoggerI18n().info(
        LocalizedStrings.HeapMemoryMonitor_OVERRIDDING_MEMORYPOOLMXBEAN_HEAP_0_NAME_1,
        new Object[] { Long.valueOf(usageThreshold), memoryPoolMXBean.getName() });

    MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
    NotificationEmitter emitter = (NotificationEmitter) mbean;
    emitter.addNotificationListener(this, null, null);
  }

  /**
   * To avoid memory spikes in jrockit, we only deliver events if we receive
   * more than {@link HeapMemoryMonitor#memoryStateChangeTolerance} of the same
   * state change.
   * 
   * @return True if an event should be skipped, false otherwise.
   */
  private boolean skipEventDueToToleranceLimits(MemoryState oldState, MemoryState newState) {
    if (testDisableMemoryUpdates) {
      return false;
    }
    
    if (newState.isEviction() && !oldState.isEviction()) {
      this.evictionToleranceCounter++;
      this.criticalToleranceCounter = 0;
      if (this.evictionToleranceCounter <= memoryStateChangeTolerance) {
        if (this.cache.getLoggerI18n().fineEnabled()) {
          this.cache.getLoggerI18n().fine("State "+newState+" ignored. toleranceCounter:"
              +this.evictionToleranceCounter+" MEMORY_EVENT_TOLERANCE:" + memoryStateChangeTolerance);
        }
        return true;
      }
    } else if (newState.isCritical()) {
      this.criticalToleranceCounter++;
      this.evictionToleranceCounter = 0;
      if (this.criticalToleranceCounter <= memoryStateChangeTolerance) {
        if (this.cache.getLoggerI18n().fineEnabled()) {
          this.cache.getLoggerI18n().fine("State "+newState+" ignored. toleranceCounter:"
              +this.criticalToleranceCounter+" MEMORY_EVENT_TOLERANCE:" + memoryStateChangeTolerance);
        }
        return true;
      }
    } else {
      this.criticalToleranceCounter = 0;
      this.evictionToleranceCounter = 0;
      if (this.cache.getLoggerI18n().fineEnabled()) {
        this.cache.getLoggerI18n().fine("TOLERANCE counters reset");
      }
    }
    return false;
  }

  public static long getBytesUsed(final MemoryPoolMXBean memoryPool,
      boolean getCurrentUsage) {
    if (getCurrentUsage) {
      return memoryPool.getUsage().getUsed();
    }
    else {
      final MemoryUsage usage = memoryPool.getCollectionUsage();
      if (usage != null) {
        return usage.getUsed();
      }
      else {
        return memoryPool.getUsage().getUsed();
      }
    }
  }

  /**
   * Returns the number of bytes of memory reported by the tenured and eden
   * pools as currently in use.
   */
  public final long getBytesUsed() {
    // get current usage for old gen
    long bytesUsed = getBytesUsed(getTenuredMemoryPoolMXBean(), true);
    MemoryPoolMXBean pool = getEdenMemoryPoolMXBean();
    if (pool != null) {
      // get last collection usage for eden space since there is likely
      // to be quite a bit of garbage in current usage
      bytesUsed += getBytesUsed(pool, false);
    }
    pool = getSurvivorPoolMXBean();
    if (pool != null) {
      // get current usage for survivor pool
      bytesUsed += getBytesUsed(pool, true);
    }
    return bytesUsed;
  }

  public static long getTenuredPoolMaxMemory() {
    return tenuredPoolMaxMemory;
  }

  public static long getEdenAndSurvivorPoolMaxMemory() {
    return edenAndSurvivorPoolMaxMemory;
  }

  public static long getTrackedMaxMemory() {
    long max = getTenuredMemoryPoolMXBean().getUsage().getMax();
    MemoryPoolMXBean mbean = getEdenMemoryPoolMXBean();
    if (mbean != null) {
      max += Math.max(0, mbean.getUsage().getMax());
    }
    mbean = getSurvivorPoolMXBean();
    if (mbean != null) {
      max += Math.max(0, mbean.getUsage().getMax());
    }
    return max;
  }

  /**
   * Deliver a memory event from one of the monitors to both local listeners and
   * remote resource managers. Also, if a critical event is received and a query
   * monitor has been enabled, then the query monitor will be notified.
   * 
   * Package private for testing.
   * 
   * @param event
   *          Event to process.
   */
  synchronized void processLocalEvent(MemoryEvent event) {
    assert event.isLocal();

    if (this.cache.getLoggerI18n().fineEnabled()) {
      this.cache.getLoggerI18n().fine("Handling new local event " + event);
    }

    if (event.getState().isCritical() && !event.getPreviousState().isCritical()) {
      this.cache.getLoggerI18n().error(LocalizedStrings.MemoryMonitor_MEMBER_ABOVE_CRITICAL_THRESHOLD,
          new Object[] { event.getMember(), "heap" });
      if (!this.cache.isQueryMonitorDisabledForLowMemory()) {
        QueryMonitor.setLowMemory(true, event.getBytesUsed());
        this.cache.getQueryMonitor().cancelAllQueriesDueToMemory();
      }

    } else if (!event.getState().isCritical() && event.getPreviousState().isCritical()) {
      this.cache.getLoggerI18n().error(LocalizedStrings.MemoryMonitor_MEMBER_BELOW_CRITICAL_THRESHOLD,
          new Object[] { event.getMember(), "heap" });
      if (!this.cache.isQueryMonitorDisabledForLowMemory()) {
        QueryMonitor.setLowMemory(false, event.getBytesUsed());
      }
    }

    if (event.getState().isEviction() && !event.getPreviousState().isEviction()) {
      this.cache.getLoggerI18n().info(LocalizedStrings.MemoryMonitor_MEMBER_ABOVE_HIGH_THRESHOLD,
          new Object[] { event.getMember(), "heap" });
    } else if (!event.getState().isEviction() && event.getPreviousState().isEviction()) {
      this.cache.getLoggerI18n().info(LocalizedStrings.MemoryMonitor_MEMBER_BELOW_HIGH_THRESHOLD,
          new Object[] { event.getMember(),  "heap" });
    }

    if (this.cache.getLoggerI18n().fineEnabled()) {
      this.cache.getLoggerI18n().fine("Informing remote members of event " + event);
    }
    
    this.resourceAdvisor.updateRemoteProfile();
    this.resourceManager.deliverLocalEvent(event);
  }
  
  @Override
  public void notifyListeners(final Set<ResourceListener> listeners, final ResourceEvent event) {
    for (ResourceListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (CancelException ignore) {
        // ignore
      } catch (Throwable t) {
        Error err;
        if (t instanceof Error && SystemFailure.isJVMFailureError(err = (Error) t)) {
          SystemFailure.initiateFailure(err);
          // If this ever returns, rethrow the error. We're poisoned now, so
          // don't let this thread continue.
          throw err;
        }
        // Whenever you catch Error or Throwable, you must also
        // check for fatal JVM error (see above). However, there is
        // _still_ a possibility that you are dealing with a cascading
        // error condition, so you also need to check to see if the JVM
        // is still usable:
        SystemFailure.checkFailure();
        this.cache.getLoggerI18n().error(LocalizedStrings.MemoryMonitor_EXCEPTION_OCCURED_WHEN_NOTIFYING_LISTENERS, t);
      }
    }
  }

  // Handles memory usage notification from MemoryMXBean.
  // See ((NotificationEmitter) MemoryMXBean).addNoticiationListener(...).
  @Override
  public void handleNotification(final Notification notification, final Object callback) {
    this.resourceManager.runWithNotifyExecutor(new Runnable() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void run() {
        // Not using the information given by the notification in favor
        // of constructing fresh information ourselves.
        if (!testDisableMemoryUpdates) {
          updateStateAndSendEvent();
        }
      }
    });
  }

  /**
   * Given a set of members, determine if any member in the set is above
   * critical threshold.
   * 
   * @param members
   *          The set of members to check.
   * @return True if the set contains a member above critical threshold, false
   *         otherwise
   */
  public boolean containsHeapCriticalMembers(final Set<InternalDistributedMember> members) {
    if (members.contains(this.cache.getMyId()) && this.mostRecentEvent.getState().isCritical()) {
      return true;
    }

    return SetUtils.intersectsWith(members, this.resourceAdvisor.adviseCriticalMembers());
  }

  /**
   * Determines if the given member is in a heap critical state.
   * 
   * @param member
   *          Member to check.
   * 
   * @return True if the member's heap memory is in a critical state, false
   *         otherwise.
   */
  public final boolean isMemberHeapCritical(final InternalDistributedMember member) {
    if (member.equals(this.cache.getMyId())) {
      return this.mostRecentEvent.getState().isCritical();
    }
    return this.resourceAdvisor.isHeapCritical(member);
  }
  
  class LocalHeapStatListener implements LocalStatListener {
    /* (non-Javadoc)
     * @see com.gemstone.gemfire.internal.LocalStatListener#statValueChanged(double)
     */
    @Override
    @SuppressWarnings("synthetic-access")
    public void statValueChanged(double value) {
      final long usedBytes = (long)value;
      try {
        HeapMemoryMonitor.this.resourceManager.runWithNotifyExecutor(new Runnable(){
          @Override
          public void run() {
            if (!testDisableMemoryUpdates) {
              updateStateAndSendEvent();
            }
          }
        });
        if (HeapMemoryMonitor.this.cache.getLoggerI18n().fineEnabled()) {          
          HeapMemoryMonitor.this.cache.getLoggerI18n().fine("StatSampler scheduled a " +
                        "handleNotification call with "+usedBytes+" bytes");
        }
      } catch (RejectedExecutionException e) {
        if (!HeapMemoryMonitor.this.resourceManager.isClosed()) {
          HeapMemoryMonitor.this.cache.getLoggerI18n().warning(LocalizedStrings.ResourceManager_REJECTED_EXECUTION_CAUSE_NOHEAP_EVENTS);
        }
      } catch (CacheClosedException e) {
        // nothing to do
      }
    }
  }
  
  @Override
  public String toString() {
    return "HeapMemoryMonitor [thresholds=" + this.thresholds
        + ", mostRecentEvent=" + this.mostRecentEvent
        + ", criticalToleranceCounter=" + this.criticalToleranceCounter
        + ", evictionToleranceCounter=" + this.evictionToleranceCounter + "]";
  }

  /**
   * Polls the heap if stat sampling is disabled.
   * 
   * @author sbawaska
   */
  class HeapPoller implements Runnable {

    final DiskCapacityMonitor diskMon;
    
    HeapPoller() {
      diskMon = DiskCapacityMonitor.getInstance();  
    }

    @SuppressWarnings("synthetic-access")
    @Override
    public void run() {
      if (testDisableMemoryUpdates) {
        return;
      }
      try {
        updateStateAndSendEvent();
        if (diskMon != null) {
          diskMon.checkAvailableSpace();
        }
      } catch (Exception e) {
        HeapMemoryMonitor.this.cache.getLoggerI18n().fine("Poller Thread caught exception:",e);
      }
      //TODO: do we need to handle errors too?
      try {
        ProcessorKeeper21.cleanupProcessor();
      }catch(Exception ex){
    	  HeapMemoryMonitor.this.cache.getLoggerI18n().fine("Poller Thread caught exception while "
    	      + "cleaning Processorkeeper:",ex);
      }
    }
  }
  
  /**
   * Overrides the value returned by the JVM as the number of bytes of available
   * memory.
   * 
   * @param testMaxMemoryBytes
   *          The value to use as the maximum number of bytes of memory
   *          available.
   */
  public void setTestMaxMemoryBytes(final long testMaxMemoryBytes) {
    synchronized (this) {
      MemoryThresholds newThresholds;

      if (testMaxMemoryBytes == 0) {
        newThresholds = new MemoryThresholds(getTenuredPoolMaxMemory()
            + getEdenAndSurvivorPoolMaxMemory());
      } else {
        newThresholds = new MemoryThresholds(testMaxMemoryBytes, this.thresholds.getCriticalThreshold(), this.thresholds
            .getEvictionThreshold());
      }

      this.thresholds = newThresholds;
      StringBuilder builder = new StringBuilder("In testing, the following values were set");
      builder.append(" maxMemoryBytes:" + newThresholds.getMaxMemoryBytes());
      builder.append(" criticalThresholdBytes:" + newThresholds.getCriticalThresholdBytes());
      builder.append(" evictionThresholdBytes:" + newThresholds.getEvictionThresholdBytes());
      this.cache.getLoggerI18n().fine(builder.toString());
    }
  }
  
  public static void setTestDisableMemoryUpdates(final boolean newTestDisableMemoryUpdates) {
    testDisableMemoryUpdates = newTestDisableMemoryUpdates;
  }
  
  /**
   * Since the setter methods for the eviction and critical thresholds
   * immediately update state based upon the new threshold value and the number
   * of bytes currently used by the JVM, there needs to be a way to override the
   * number of bytes of memory reported as in use for testing. That's what this
   * method and the value it sets are for.
   * 
   * @param newTestBytesUsedForThresholdSet
   *          Value to use as the amount of memory in use when calling the
   *          setEvictionThreshold or setCriticalThreshold methods are called.
   */
  public static void setTestBytesUsedForThresholdSet(final long newTestBytesUsedForThresholdSet) {
    testBytesUsedForThresholdSet = newTestBytesUsedForThresholdSet;
  }
}
