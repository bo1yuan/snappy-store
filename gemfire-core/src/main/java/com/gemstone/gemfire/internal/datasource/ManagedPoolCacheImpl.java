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
package com.gemstone.gemfire.internal.datasource;

/**
 * @author rreja
 */
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;
import com.gemstone.gemfire.internal.jta.TransactionUtils;

/**
 * This class implements a connection pool for Managed connection. Extends the
 * AbstractPoolCache to inherit the pool bahavior.
 * 
 * @author rreja
 */
public class ManagedPoolCacheImpl extends AbstractPoolCache  {

  private static final long serialVersionUID = 1064642271736399718L;
  private ManagedConnectionFactory connFactory;
  private Subject sub;
  private ConnectionRequestInfo connReqInfo;

  /**
   * Constructor initializes the ConnectionPoolCacheImpl properties.
   */
  public ManagedPoolCacheImpl(ManagedConnectionFactory connFac,
      Subject subject, ConnectionRequestInfo connReq,
      javax.resource.spi.ConnectionEventListener eventListner,
      ConfiguredDataSourceProperties configs) throws PoolException {
    super(eventListner, configs);
    connFactory = connFac;
    sub = subject;
    connReqInfo = connReq;
    initializePool();
  }

  /**
   * Creates a new connection for the managed connection pool.
   * 
   * @return the managed connection from the EIS as ManagedConnection object.
   * @throws PoolException
   */
  @Override
  public Object getNewPoolConnection() throws PoolException {
    ManagedConnection manConn = null;
    try {
      manConn = connFactory.createManagedConnection(sub, connReqInfo);
    }
    catch (ResourceException rex) {
      rex.printStackTrace();
      throw new PoolException(LocalizedStrings.ManagedPoolCacheImpl_MANAGEDPOOLCACHEIMPLGETNEWCONNECTION_EXCEPTION_IN_CREATING_NEW_MANAGED_POOLEDCONNECTION.toLocalizedString(), rex);
    }
    manConn
        .addConnectionEventListener((javax.resource.spi.ConnectionEventListener) connEventListner);
    return manConn;
  }

  /**
   * Destroys the underline physical connection to EIS.
   * 
   * @param connectionObject connection Object.
   */
  @Override
  void destroyPooledConnection(Object connectionObject) {
    try {
      ((ManagedConnection) connectionObject)
          .removeConnectionEventListener((ConnectionEventListener) connEventListner);
      ((ManagedConnection) connectionObject).destroy();
      connectionObject = null;
    }
    catch (ResourceException rex) {
      LogWriterI18n writer = TransactionUtils.getLogWriterI18n();
      if (writer.finerEnabled())
          writer
              .finer(
                  "ManagedPoolcacheImpl::destroyPooledConnection:Exception in closing the connection.Ignoring it. The exeption is "
                      + rex.toString(), rex);
    }
  }
}
