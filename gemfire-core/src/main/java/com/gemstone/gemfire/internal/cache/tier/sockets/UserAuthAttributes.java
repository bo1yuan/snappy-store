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
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.util.concurrent.atomic.AtomicInteger;

import com.gemstone.gemfire.internal.security.AuthorizeRequest;
import com.gemstone.gemfire.internal.security.AuthorizeRequestPP;

public class UserAuthAttributes
{
  private AtomicInteger numberOfDurableCQ;

  /**
   * Authorize client requests using this object. This is set when each
   * operation on this connection is authorized in pre-operation phase.
   */
  private AuthorizeRequest authzRequest;

  /**
   * Authorize client requests using this object. This is set when each
   * operation on this connection is authorized in post-operation phase.
   */
  private AuthorizeRequestPP postAuthzRequest;
  
  public UserAuthAttributes(AuthorizeRequest authzRequest, AuthorizeRequestPP postAuthzRequest) {
    this.authzRequest = authzRequest;
    this.postAuthzRequest = postAuthzRequest;
    this.numberOfDurableCQ = new AtomicInteger();    
  }
  
  public AuthorizeRequest getAuthzRequest() {
    return this.authzRequest;
  }

  public AuthorizeRequestPP getPostAuthzRequest() {
    return this.postAuthzRequest;
  }
  /*
  public void setDurable(boolean isDurable) {
    if(isDurable)
    {
      this.numberOfDurableCQ.incrementAndGet();
    }
  }
  */
  public void setDurable() {
    this.numberOfDurableCQ.incrementAndGet();
  }
  
  public void unsetDurable() {
    this.numberOfDurableCQ.decrementAndGet();
  }
  
  
  public boolean isDurable() {
    return this.numberOfDurableCQ.intValue() != 0;
  }
  /*protected void setAuthorizeRequest(AuthorizeRequest authzRequest) {
    this.authzRequest = authzRequest;
  }

  protected void setPostAuthorizeRequest(AuthorizeRequestPP postAuthzRequest) {
    this.postAuthzRequest = postAuthzRequest;
  }*/

}
