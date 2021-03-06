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
package com.gemstone.gemfire.cache;

/**
 * A listener that can be implemented to handle region reliability membership 
 * events.  These are membership events that are specific to loss or gain of
 * required roles as defined by the region's {@link MembershipAttributes}.
 * <p>
 * Instead of implementing this interface it is recommended that you extend
 * the {@link com.gemstone.gemfire.cache.util.RegionRoleListenerAdapter} 
 * class.
 * 
 * @author Kirk Lund
 * 
 * @see AttributesFactory#setCacheListener
 * @see RegionAttributes#getCacheListener
 * @see AttributesMutator#setCacheListener
 * @since 5.0
 */
public interface RegionRoleListener<K,V> extends CacheListener<K,V> {

  /**
   * Invoked when a required role has returned to the distributed system
   * after being absent.
   *
   * @param event describes the member that fills the required role.
   */
  public void afterRoleGain(RoleEvent<K,V> event);
  
  /**
   * Invoked when a required role is no longer available in the distributed
   * system.
   *
   * @param event describes the member that last filled the required role.
   */
  public void afterRoleLoss(RoleEvent<K,V> event);
  
}

