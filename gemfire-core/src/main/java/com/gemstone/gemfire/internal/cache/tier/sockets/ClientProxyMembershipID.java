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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.InternalGemFireException;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.DurableClientAttributes;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.Assert;
import com.gemstone.gemfire.internal.DataSerializableFixedID;
import com.gemstone.gemfire.internal.HeapDataOutputStream;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.VersionedDataInputStream;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.shared.Version;

/**
 * This class represents a ConnectionProxy of the CacheClient
 * 
 * @author ashahid
 * 
 *  
 */
public final class ClientProxyMembershipID
  implements DataSerializableFixedID, Serializable, Externalizable {

  private static final int BYTES_32KB = 32768;

  // TODO:Asif : If possible remove the static data from here
  // Uniquely identifies the distributed system of client. These static fields
  // have significance
  // only in the Cache Client VM
  public volatile static byte[] client_side_identity = null;
  
  public volatile static DistributedSystem system = null;

  /**
   * the membershpi id of the distributed system in this client (if running in a
   * client)
   */
  public static DistributedMember systemMemberId;

  private static int synch_counter = 0;

  // private byte[] proxyID ;
  protected byte[] identity;

  /** cached membership identifier */
  private transient DistributedMember memberId;

  /** cached tostring of the memberID */
  private transient String memberIdString;

  protected int uniqueId;
  
  // private final String proxyIDStr;
  // private final String clientIdStr ;

  @Override
  public int hashCode()
  {
    int result = 17;
    final int mult = 37;
    if (isDurable()) {
      result = mult * result + getDurableId().hashCode();
    } else  {
      if (this.identity != null && this.identity.length > 0) {
        for (int i = 0; i < this.identity.length; i++) {
          result = mult * result + this.identity[i];
        }
      }
    }
    // we can't use unique_id in hashCode
    // because of HandShake's hashCode using our HashCode but
    // its equals using our isSameDSMember which ignores unique_id
    //result = mult * result + this.unique_id;
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || !(obj instanceof ClientProxyMembershipID)) {
      return false;
    }
    ClientProxyMembershipID that = (ClientProxyMembershipID) obj;
    if (this.uniqueId != that.uniqueId) {
      return false;
    }
    boolean isDurable = this.isDurable();
    if (isDurable && !that.isDurable()) {
      return false;
    }
    if (isDurable) {
      return this.getDurableId().equals(that.getDurableId());
    }
    return Arrays.equals(this.identity, that.identity);
  }
  
  /**
   * Return true if "that" can be used in place of "this"
   * when canonicalizing.
   */
  private boolean isCanonicalEquals(ClientProxyMembershipID that) {
    if (this == that) {
      return true;
    }
    if (this.uniqueId != that.uniqueId) {
      return false;
    }
    return Arrays.equals(this.identity, that.identity);
  }

  boolean isSameDSMember(ClientProxyMembershipID that)
  {
    if (that != null) {
      // Test whether:
      // - the durable ids are equal (if durable) or
      // - the identities are equal (if non-durable)
      return isDurable()
        ? this.getDurableId().equals(that.getDurableId())
        : Arrays.equals(this.identity, that.identity);
    }
    else {
      return false;
    }
  }

  /** method to obtain ClientProxyMembership for client side */
  public static synchronized ClientProxyMembershipID getNewProxyMembership(
      DistributedSystem sys) {
    initializeAndGetDSIdentity(sys);
    return new ClientProxyMembershipID(++synch_counter);
  }

  public static ClientProxyMembershipID getClientId(DistributedMember member) {
    return new ClientProxyMembershipID(member);
  }

  public static byte[] initializeAndGetDSIdentity(DistributedSystem sys)
  {
    if (sys == null) {
      // DistributedSystem is required now before handshaking -Kirk
      throw new IllegalStateException(LocalizedStrings.ClientProxyMembershipID_ATTEMPTING_TO_HANDSHAKE_WITH_CACHESERVER_BEFORE_CREATING_DISTRIBUTEDSYSTEM_AND_CACHE.toLocalizedString());
    }
    if (system != sys) {
      // DS already exists... make sure it's for current DS connection
      systemMemberId = sys.getDistributedMember();
      try {
        HeapDataOutputStream hdos = new HeapDataOutputStream(256, Version.CURRENT);
        DataSerializer.writeObject(systemMemberId, hdos);
        client_side_identity = hdos.toByteArray();
      }
      catch (IOException ioe) {
        throw new InternalGemFireException(LocalizedStrings.ClientProxyMembershipID_UNABLE_TO_SERIALIZE_IDENTITY.toLocalizedString(), ioe);
      }

      system = sys;
    }
    return client_side_identity;

  }

  private ClientProxyMembershipID(int id) {
    this.uniqueId = id;
    this.identity = client_side_identity;
    this.memberId = systemMemberId;
  }

  public ClientProxyMembershipID() {
  }

  public ClientProxyMembershipID(DistributedMember member) {
    this.uniqueId = 1;
    this.memberId = member;
    updateID(member);
  }
  
  
  private transient String _toString;

//  private transient int transientPort; // variable for debugging member ID issues

  @Override
  public String toString()
  {
    if (this.identity != null && ((InternalDistributedMember)getDistributedMember()).getPort() == 0) {
      return this.toStringNoCache();
    }
    if (this._toString == null) {
      this._toString = this.toStringNoCache();
    }
    return this._toString;
  }
  
  /**
   * returns a string representation of this identifier, ignoring the toString
   * cache
   */
  public String toStringNoCache() {
    StringBuffer sb = new StringBuffer("identity(").append(getDSMembership())
    .append(",connection=").append(uniqueId);
    if (identity != null) {
    DurableClientAttributes dca = getDurableAttributes();
    if (dca.getId().length() > 0) {
      sb.append(",durableAttributes=")
      .append(getDurableAttributes()).append(')').toString();
    }
    }
    return sb.toString();
  }

  /**
   * For Externalizable
   * 
   * @see Externalizable
   */
  public void writeExternal(ObjectOutput out) throws IOException
  {
//    if (this.transientPort == 0) {
//      InternalDistributedSystem.getLoggerI18n().warning(
//          LocalizedStrings.DEBUG,
//          "externalizing a client ID with zero port: " + this.toString(),
//          new Exception("Stack trace"));
//    }
    Assert.assertTrue(this.identity.length <= BYTES_32KB);
    out.writeShort(this.identity.length);
    out.write(this.identity);
    out.writeInt(this.uniqueId);

  }

  /** returns the externalized size of this object */
  public int getSerializedSize()
  {
    return 4 + identity.length + 4;
  }

  /**
   * For Externalizable
   * 
   * @see Externalizable
   */
  public void readExternal(ObjectInput in) throws IOException,
      ClassNotFoundException
  {
    int identityLength = in.readShort();
    if (identityLength > BYTES_32KB) {
      throw new IOException(LocalizedStrings.ClientProxyMembershipID_HANDSHAKE_IDENTITY_LENGTH_IS_TOO_BIG.toLocalizedString());
    }
    this.identity = new byte[identityLength];
    read(in, this.identity);
    this.uniqueId = in.readInt();
    if (this.uniqueId == -1) {
      throw new IOException(LocalizedStrings.ClientProxyMembershipID_UNEXPECTED_EOF_REACHED_UNIQUE_ID_COULD_NOT_BE_READ.toLocalizedString());
    }
//    {toString(); this.transientPort = ((InternalDistributedMember)this.memberId).getPort();}
  }

  private void read(ObjectInput dis, byte[] toFill) throws IOException
  {

    int idBytes = 0;
    int toFillLength = toFill.length;
    while (idBytes < toFillLength) {
      // idBytes += dis.read(toFill, idBytes, (toFillLength - idBytes));
      int dataRead = dis.read(toFill, idBytes, (toFillLength - idBytes));
      if (dataRead == -1) {
        throw new IOException(LocalizedStrings.ClientProxyMembershipID_UNEXPECTED_EOF_REACHED_DISTRIBUTED_MEMBERSHIPID_COULD_NOT_BE_READ.toLocalizedString());
      }
      idBytes += dataRead;
    }
  }

  public int getDSFID() {
    return CLIENT_PROXY_MEMBERSHIPID;
  }

  public void toData(DataOutput out) throws IOException
  {
//    if (this.transientPort == 0) {
//      InternalDistributedSystem.getLoggerI18n().warning(
//          LocalizedStrings.DEBUG,
//          "serializing a client ID with zero port: " + this.toString(),
//          new Exception("Stack trace"));
//    }
    DataSerializer.writeByteArray(this.identity, out);
    out.writeInt(this.uniqueId);
  }

  public void fromData(DataInput in) throws IOException, ClassNotFoundException
  {
    this.identity = DataSerializer.readByteArray(in);
    this.uniqueId = in.readInt();
//    {toString(); this.transientPort = ((InternalDistributedMember)this.memberId).getPort();}
  }
  
  public Version getClientVersion() {
    return ((InternalDistributedMember)getDistributedMember()).getVersionObject();
  }

  public String getDSMembership()
  {
    if (identity == null) {
      // some unit tests create IDs that have no real identity, so return null
      return "null";
    }
    // don't cache if we haven't connected to the server yet
    if (((InternalDistributedMember)getDistributedMember()).getPort() == 0) {
      return getDistributedMember().toString();
    }
    if (memberIdString == null) {
      memberIdString = getDistributedMember().toString();
    }
    return memberIdString;
  }

  /**
   * this method uses CacheClientNotifier to try to obtain an ID that 
   * is equal to this one.  This is used during deserialization to
   * reduce storage overhead.
   */
  public ClientProxyMembershipID canonicalReference() {
    CacheClientNotifier ccn = CacheClientNotifier.getInstance();
    if (ccn != null) {
      CacheClientProxy cp = ccn.getClientProxy(this, true);
      if (cp != null) {
        if (this.isCanonicalEquals(cp.getProxyID())) {
          return cp.getProxyID();
        }
      }
    }
    return this;
  }

  /**
   * deserializes the membership id, if necessary, and returns it. All access to
   * membershipId should be through this method
   */
  
  public DistributedMember getDistributedMember()  {
    if (memberId == null) {      
      ByteArrayInputStream bais = new ByteArrayInputStream(identity);
      DataInputStream dis = new VersionedDataInputStream(bais, Version.CURRENT);
      try {
        memberId = (DistributedMember)DataSerializer.readObject(dis);
      }
      catch (Exception e) {
        DistributedSystem ds = InternalDistributedSystem.getAnyInstance();
        if(ds != null){
        LogWriterI18n logger = ds.getLogWriter().convertToLogWriterI18n();
        if (logger.errorEnabled())
          logger.error(
            LocalizedStrings.ClientProxyMembershipID_UNABLE_TO_DESERIALIZE_MEMBERSHIP_ID, e);
        } 
      }
    }
    return memberId;
  }
  
  /** Returns the byte-array for membership identity */
  byte[] getMembershipByteArray()
  {
    return this.identity;
  }
  
  /**
   * Returns whether this <code>ClientProxyMembershipID</code> is durable.
   * @return whether this <code>ClientProxyMembershipID</code> is durable
   * 
   * @since 5.5
   */
  public boolean isDurable() {
    String durableClientId = getDistributedMember().getDurableClientAttributes().getId(); 
    return durableClientId != null && !(durableClientId.length() == 0);
  }
  
  /**
   * Returns this <code>ClientProxyMembershipID</code>'s durable attributes.
   * @return this <code>ClientProxyMembershipID</code>'s durable attributes
   * 
   * @since 5.5
   */
  protected DurableClientAttributes getDurableAttributes() {
    return getDistributedMember().getDurableClientAttributes();
  }
  
  /**
   * Returns this <code>ClientProxyMembershipID</code>'s durable id.
   * @return this <code>ClientProxyMembershipID</code>'s durable id
   * 
   * @since 5.5
   */
  public String getDurableId() {
    DurableClientAttributes dca = getDurableAttributes();
    return dca == null ? "" : dca.getId();
  }
  
  /**
   * Returns this <code>ClientProxyMembershipID</code>'s durable timeout.
   * @return this <code>ClientProxyMembershipID</code>'s durable timeout
   * 
   * @since 5.5
   */
  protected int getDurableTimeout() {
    DurableClientAttributes dca = getDurableAttributes();
    return dca == null ? 0 : dca.getTimeout();
  }

  /**
   * Used to update the timeout when a durable client comes back to a server
   */
  public void updateDurableTimeout(int newValue) {
    DurableClientAttributes dca = getDurableAttributes();
    if (dca != null) {
      dca.updateTimeout(newValue);
    }
  }
  
  /**
   * call this when the distributed system ID has been modified
   */
  public void updateID(DistributedMember idm) {
//    this.transientPort = ((InternalDistributedMember)this.memberId).getPort();
//    if (this.transientPort == 0) {
//      InternalDistributedSystem.getLoggerI18n().warning(
//          LocalizedStrings.DEBUG,
//          "updating client ID when member port is zero: " + this.memberId,
//          new Exception("stack trace")
//          );
//    }
    HeapDataOutputStream hdos = new HeapDataOutputStream(256, Version.CURRENT);
    try {
      DataSerializer.writeObject(idm, hdos);
    } catch (IOException e) {
      throw new InternalGemFireException("Unable to serialize member: " + this.memberId, e);
    }
    this.identity = hdos.toByteArray();
    if (this.memberId != null && this.memberId == systemMemberId) {
      systemMemberId = idm;
      client_side_identity = this.identity;
    }
    this.memberId = idm;
    this._toString = null;  // make sure we don't retain the old ID representation in toString
  }
    
  
  /**
   * Return the name of the <code>HARegion</code> queueing this proxy's
   * messages. This is name is generated based on whether or not this proxy id
   * is durable. If this proxy id is durable, then the durable client id is
   * used. If this proxy id is not durable, then the<code>DistributedMember</code>
   * string is used.
   * 
   * @return the name of the <code>HARegion</code> queueing this proxy's
   * messages.
   * 
   * @since 5.5
   */
  protected String getHARegionName() {
    return getBaseRegionName() + "_queue";
  }
  
  /**
   * Return the name of the region used for communicating interest changes
   * between servers.
   * 
   * @return the name of the region used for communicating interest changes
   * between servers
   * 
   * @since 5.6
   */
  protected String getInterestRegionName() {
    return getBaseRegionName() + "_interest";
  }
  
  private String getBaseRegionName() {
    String id = isDurable()? getDurableId() : getDSMembership();
    if (id.indexOf('/') >= 0) {
      id = id.replace('/', ':');
    }
    StringBuilder buffer = new StringBuilder()
      .append("_gfe_")
      .append(isDurable() ? "" : "non_")
      .append("durable_client_")
      .append("with_id_"+id)
      .append("_")
      .append(this.uniqueId);
    return buffer.toString();
  }

  /**
   * Resets the unique id counter. This is done for durable clients that
   * stops/starts its cache. When it restarts its cache, it needs to maintain
   * the same unique id
   * 
   * @since 5.5
   */
  public static synchronized void resetUniqueIdCounter() {
    synch_counter = 0;
  }

  public Identity getIdentity() {
    return new Identity();
  }
  
  /**
   * Used to represent a unique identity of this ClientProxyMembershipID.
   * It does this by ignoring the durable id and only respecting the
   * unique_id and identity.
   * <p>
   * This class is used to clean up resources associated with a particular
   * client and thus does not want to limit itself to the durable id.
   * @since 5.7
   */
  public class Identity {
    public int getUniqueId() {
      return uniqueId;
    }
    public byte[] getMemberIdBytes() {
      return identity;
    }
    @Override
    public int hashCode() {
      int result = 17;
      final int mult = 37;
      byte[] idBytes = getMemberIdBytes();
      if (idBytes != null && idBytes.length > 0) {
        for (int i = 0; i < idBytes.length; i++) {
          result = mult * result + idBytes[i];
        }
      }
      result = mult * result + uniqueId;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if ((obj == null) || !(obj instanceof ClientProxyMembershipID.Identity)) {
        return false;
      }
      ClientProxyMembershipID.Identity that = (ClientProxyMembershipID.Identity) obj;
      return (getUniqueId() == that.getUniqueId() &&
              Arrays.equals(getMemberIdBytes(), that.getMemberIdBytes()));
    }
    public ClientProxyMembershipID getClientProxyID() {
      return ClientProxyMembershipID.this;
    }
    
  }

  @Override
  public Version[] getSerializationVersions() {
    return null;
  }

  public static ClientProxyMembershipID readCanonicalized(DataInput in)
      throws IOException, ClassNotFoundException {
    
    ClientProxyMembershipID result = DataSerializer.readObject(in);
    // We can't canonicalize if we have no identity.
    // I only saw this happen in unit tests that serialize "new ClientProxyMembershipID()".
    if (result == null || result.identity == null) {
      return result;
    }
    return result.canonicalReference();
  }
}
