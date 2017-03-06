/*
 * Copyright (c) 2017 SnappyData, Inc. All rights reserved.
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
package io.snappydata.jdbc;

/**
 * The data source factory for SnappyData client driver data sources.
 * <p>
 * Does nothing apart from namespace change since the base class will
 * take care of any Reference objects in the io.snappydata namespace.
 *
 * @see javax.naming.spi.ObjectFactory
 * @see io.snappydata.jdbc.ClientDataSource
 * @see io.snappydata.jdbc.ClientConnectionPoolDataSource
 * @see io.snappydata.jdbc.ClientXADataSource
 */
public class ClientDataSourceFactory
    extends com.pivotal.gemfirexd.internal.client.ClientDataSourceFactory {
}
