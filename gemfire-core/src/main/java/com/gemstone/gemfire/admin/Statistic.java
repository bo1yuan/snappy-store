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

package com.gemstone.gemfire.admin;

/**
 * Interface to represent a single statistic of a <code>StatisticResource</code>
 *
 * @author    Kirk Lund
 * @since     3.5
 *
 * @deprecated as of 7.0 use the {@link com.gemstone.gemfire.management} package instead
 */
public interface Statistic extends java.io.Serializable {
    
  /**
   * Gets the identifying name of this statistic.
   *
   * @return the identifying name of this statistic 
   */
  public String getName();
    
  /**
   * Gets the value of this statistic as a <code>java.lang.Number</code>.
   *
   * @return the value of this statistic
   */
  public Number getValue();
  
  /**
   * Gets the unit of measurement (if any) this statistic represents.
   *
   * @return the unit of measurement (if any) this statistic represents
   */
  public String getUnits();
  
  /**
   * Returns true if this statistic represents a numeric value which always 
   * increases.
   *
   * @return true if this statistic represents a value which always increases
   */
  public boolean isCounter();
  
  /**
   * Gets the full description of this statistic.
   *
   * @return the full description of this statistic
   */
  public String getDescription();
}

