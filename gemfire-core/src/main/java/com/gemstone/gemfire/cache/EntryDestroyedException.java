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
 * Indicates that a method was invoked on an entry that has been destroyed.
 *
 * @author Eric Zoerner
 *
 * 
 * @see Region.Entry
 * @since 3.0
 */
public class EntryDestroyedException extends CacheRuntimeException
{
  private static final long serialVersionUID = 831865939772672542L;
  /** Constructs a new <code>EntryDestroyedException</code>. */ 
  public EntryDestroyedException()
  {
     super();
  }

  /**
   * Constructs a new <code>EntryDestroyedException</code> with the message.
   * @param s the detailed message for this exception
   */
  public EntryDestroyedException(String s)
  {
    super(s);
  }

  /** Constructs a new <code>EntryDestroyedException</code> with a detailed message
   * and a causal exception.
   * @param s the message
   * @param ex a causal Throwable
   */
  public EntryDestroyedException(String s, Throwable ex)
  {
    super(s, ex);
  }
  
  /** Construct a <code>EntryDestroyedException</code> with a cause.
   * @param ex the causal Throwable
   */  
  public EntryDestroyedException(Throwable ex) {
    super(ex);
  }
}
