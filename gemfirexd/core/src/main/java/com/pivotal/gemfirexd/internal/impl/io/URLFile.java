/*

   Derby - Class com.pivotal.gemfirexd.internal.impl.io.URLFile

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package com.pivotal.gemfirexd.internal.impl.io;


import com.pivotal.gemfirexd.internal.iapi.services.sanity.SanityManager;
import com.pivotal.gemfirexd.internal.io.StorageFile;
import com.pivotal.gemfirexd.internal.io.StorageRandomAccessFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.net.URL;

/**
 * This class provides a class path based implementation of the StorageFile interface. It is used by the
 * database engine to access persistent data and transaction logs under the classpath subsubprotocol.
 */
class URLFile extends InputStreamFile
{

    private final URLStorageFactory storageFactory;

    URLFile( URLStorageFactory storageFactory, String path)
    {
        super( storageFactory, path);
        this.storageFactory = storageFactory;
    }

    URLFile( URLStorageFactory storageFactory, String parent, String name)
    {
        super( storageFactory, parent, name);
        this.storageFactory = storageFactory;
    }

    URLFile( URLFile dir, String name)
    {
        super( dir,name);
        this.storageFactory = dir.storageFactory;
    }

    private URLFile( URLStorageFactory storageFactory, String child, int pathLen)
    {
        super( storageFactory, child, pathLen);
        this.storageFactory = storageFactory;
    }

    /**
     * Tests whether the named file exists.
     *
     * @return <b>true</b> if the named file exists, <b>false</b> if not.
     */
    public boolean exists()
    {
        try
        {
            InputStream is = getInputStream();
            if( is == null)
                return false;
            is.close();
            return true;
        }
        catch( IOException ioe){ return false;}
    } // end of exists

    /**
     * Get the parent of this file.
     *
     * @param pathLen the length of the parent's path name.
     */
    StorageFile getParentDir( int pathLen)
    {
        return new URLFile( storageFactory, path, pathLen);
    }
    
    /**
     * Creates an input stream from a file name.
     *
     * @return an input stream suitable for reading from the file.
     *
     * @exception FileNotFoundException if the file is not found.
     */
    public InputStream getInputStream( ) throws FileNotFoundException
    {
        try
        {
            URL url = new URL( path);
            return url.openStream();
        }
        catch( IOException ioe){ throw new java.io.FileNotFoundException(path);}
    } // end of getInputStream
}
