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
package com.gemstone.gemfire.internal.util;

/**
 * Represents a data transform between two types.
 * @author rholmes
 *
 * @param <T1> The data type to be transformed from.
 * @param <T2> The data type to be transformed to.
 */
public interface Transformer<T1,T2> {
  /**
   * Transforms one data type into another.
   * @param t the data to be transferred from.
   * @return the transformed data.
   */
  public T2 transform(T1 t);
}
