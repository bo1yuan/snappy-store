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
package com.gemstone.gemfire.management.internal.cli;

import com.gemstone.gemfire.management.cli.Result;
import com.gemstone.gemfire.management.internal.cli.shell.GfshExecutionStrategy;

/**
 * Interceptor interface which {@link GfshExecutionStrategy} can use to
 * intercept before & after actual command execution.
 * 
 * @author Abhishek Chaudhari
 * 
 * @since 7.0
 */
public interface CliAroundInterceptor {
  
  public Result preExecution(GfshParseResult parseResult);
  
  public Result postExecution(GfshParseResult parseResult, Result commandResult);

}
