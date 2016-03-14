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

package com.gemstone.gemfire.internal.cache.partitioned;

import java.nio.BufferUnderflowException;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.InternalGemFireError;
import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.DistributionMessage;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.ReplyMessage;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.SerializationVersions;
import com.gemstone.gemfire.internal.shared.Version;
import com.gemstone.gemfire.internal.cache.BucketRegion;
import com.gemstone.gemfire.internal.cache.EventID;
import com.gemstone.gemfire.internal.cache.FilterProfile;
import com.gemstone.gemfire.internal.cache.ForceReattemptException;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.gemstone.gemfire.internal.cache.RegionEventImpl;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheClientNotifier;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientTombstoneMessage;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientUpdateMessage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This message class sends tombstone GC information to other PR holders
 *
 * @author Bruce Schuchardt
 * @since 7.0
 */
public final class PRTombstoneMessage extends PartitionMessageWithDirectReply
  implements SerializationVersions {

  private static Version[] serializationVersions = new Version[]{ Version.GFXD_14 };

  private Set<Object> keys;
  private EventID eventID;

  /**
   * Empty constructor to satisfy {@link DataSerializer} requirements
   */
  public PRTombstoneMessage() {
  }
  
  public static void send(BucketRegion r, final Set<Object> keys, EventID eventID)  {
    Set<InternalDistributedMember> recipients = r.getPartitionedRegion().getRegionAdvisor().adviseAllPRNodes();
    recipients.removeAll(r.getDistributionAdvisor().adviseReplicates());
    if (recipients.size() == 0) {
      return;
    }
    PartitionResponse p = new Response(r.getSystem(), recipients);
    PRTombstoneMessage m = new PRTombstoneMessage(recipients, r.getPartitionedRegion().getPRId(), p,
        keys, eventID);
    r.getDistributionManager().putOutgoing(m);

    try {
      p.waitForCacheException();
    } catch (ForceReattemptException e) {
      // ignore - the member is going away or has destroyed the PR so
      // it won't be forwarding anything to clients for the PR
    }
  }

  private PRTombstoneMessage(Set<InternalDistributedMember> recipients, int regionId,
      PartitionResponse p, Set<Object> reapedKeys, EventID eventID) {
    super(recipients, regionId, p, null);
    this.keys = reapedKeys;
    this.eventID = eventID;
  }

  @Override
  protected final boolean operateOnPartitionedRegion(
          final DistributionManager dm, PartitionedRegion r, long startTime)
          throws ForceReattemptException {
    LogWriterI18n l = r.getCache().getLoggerI18n();
    if (DistributionManager.VERBOSE) {
      l.fine("PRTombstoneMessage operateOnRegion: " + r.getFullPath());
    }
    FilterProfile fp = r.getFilterProfile();
    if (fp != null && CacheClientNotifier.getInstance() != null && this.eventID != null) {
      RegionEventImpl regionEvent = new RegionEventImpl(r, Operation.REGION_DESTROY, 
          null, true, r.getGemFireCache().getMyId()); 
      regionEvent.setLocalFilterInfo(fp.getLocalFilterRouting(regionEvent)); 
      ClientUpdateMessage clientMessage = ClientTombstoneMessage.gc(r, this.keys,
          this.eventID);
      CacheClientNotifier.notifyClients(regionEvent, clientMessage); 
    }
    return true;
  }

  @Override
  protected void appendFields(StringBuilder buff) {
    super.appendFields(buff);
    buff.append("; keys=").append(this.keys.size());
    buff.append("; eventID=").append(this.eventID);
  }

  public int getDSFID() {
    return PR_TOMBSTONE_MESSAGE;
  }

  @Override
  public Version[] getSerializationVersions() {
    return serializationVersions;
  }

  @Override
  public void fromData(DataInput in) throws IOException,
          ClassNotFoundException {
    fromDataPre_GFXD_1_4_0_0(in);
    this.eventID = (EventID)DataSerializer.readObject(in);
  }
  
  public void fromDataPre_GFXD_1_4_0_0(DataInput in) throws IOException,
          ClassNotFoundException {
    super.fromData(in);
    int numKeys = in.readInt();
    this.keys = new HashSet<Object>(numKeys);
    for (int i = 0; i < numKeys; i++) {
      this.keys.add(DataSerializer.readObject(in));
    }
  }
  

  @Override
  public void toData(DataOutput out) throws IOException {
    toDataPre_GFXD_1_4_0_0(out);
    DataSerializer.writeObject(this.eventID, out);
  }
  
  public void toDataPre_GFXD_1_4_0_0(DataOutput out) throws IOException {
    super.toData(out);
    out.writeInt(this.keys.size());
    for (Object key : keys) {
      DataSerializer.writeObject(key, out);
    }
  }


  private static class Response extends PartitionResponse {
    //    Set<InternalDistributedMember> forceReattemptSenders = new HashSet<InternalDistributedMember>();

    public Response(InternalDistributedSystem ds, Set recipients) {
      super(ds, recipients, false, null);
    }

    @Override
    public void process(DistributionMessage msg) {
      ReplyMessage reply = (ReplyMessage) msg;
      if (reply.getException() != null) {
        Throwable cause = reply.getException().getCause();
        if (cause instanceof ForceReattemptException || cause instanceof CancelException) {
          // TODO do we need to resend to these recipients?  Might they have clients that won't otherwise get
          // the GC message?
          //          this.forceReattemptSenders.add(reply.getSender());
          reply.setException(null);
        }
      }
      super.process(reply);
    }
  }
}
