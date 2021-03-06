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

package com.gemstone.gemfire.distributed.internal.membership;

import com.gemstone.gemfire.LogWriter;

/**
 * A QuorumChecker is created after a forced-disconnect in order
 * to probe the network to see if there is a quorum of members
 * that can be contacted.
 * 
 * @author bschuchardt
 *
 */
public interface QuorumChecker {
  
  /**
   * Check to see if a quorum of the old members are reachable
   * @param timeoutMS time to wait for responses, in milliseconds
   * @param debugLogger used for logging progress.  may be null
   */
  public boolean checkForQuorum(long timeoutMS, LogWriter debugLogger) throws InterruptedException;
  
  /**
   * suspends the quorum checker for an attempt to connect to the distributed system
   */
  public void suspend();
  
  /**
   * resumes the quorum checker after having invoked suspend();
   */
  public void resume();

  /**
   * Get the membership info from the old system that needs to be passed
   * to the one that is reconnecting.
   */
  public Object getMembershipInfo();
}
