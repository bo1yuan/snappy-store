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
package com.gemstone.gemfire.cache.query.internal;

import com.gemstone.gemfire.cache.query.AmbiguousNameException;
import com.gemstone.gemfire.cache.query.NameResolutionException;
import com.gemstone.gemfire.cache.query.TypeMismatchException;

/**
 * Interface implemented by CompiledComparision and CompiledUndefibed to
 * indicate that index can be created on such CompiledValues.It indicates that
 * they are filter evaluatable at the atomic level.
 * 
 * @author asif
 * 
 */
public interface Indexable {
  /**
   * Returns the IndexInfo object, if any, associated with the CompiledValue
   * 
   * @param context
   *                ExecutionContext object
   * @return IndexInfo object , if any, associated with the CompiledValue
   * @throws TypeMismatchException
   * @throws AmbiguousNameException
   * @throws NameResolutionException
   */
  IndexInfo[] getIndexInfo(ExecutionContext context)
      throws TypeMismatchException, AmbiguousNameException,
      NameResolutionException;

  /**
   * 
   * @return boolean indicating whether the CompiledValue is RangeEvaluatable or
   *         not. Presently CompiledUndefined is assumed to be not range
   *         evaluatable while a CompiledComparison is assumed to be range
   *         evaluatable ( though we do not club compiled comparison having null
   *         as RHS or LHS field along with other CompiledComparison objects)
   */
  boolean isRangeEvaluatable();
}
