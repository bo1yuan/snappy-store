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

/**
 * Do not modify this class. It was generated.
 * Instead modify LeafRegionEntry.cpp and then run
 * bin/generateRegionEntryClasses.sh from the directory
 * that contains your build.xml.
 */
package com.pivotal.gemfirexd.internal.engine.store.entry;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import com.gemstone.gemfire.internal.cache.Token;
import com.gemstone.gemfire.internal.concurrent.AtomicUpdaterFactory;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.internal.cache.lru.EnableLRU;
import com.gemstone.gemfire.internal.cache.persistence.DiskRecoveryStore;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.cache.versions.VersionSource;
import com.gemstone.gemfire.internal.cache.versions.VersionStamp;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;
import com.gemstone.gemfire.internal.concurrent.CustomEntryConcurrentHashMap.HashEntry;
import java.io.DataOutput;
import java.io.IOException;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.internal.cache.RegionEntryContext;
import com.gemstone.gemfire.internal.cache.RegionEntryFactory;
import com.gemstone.gemfire.internal.shared.Version;
import com.gemstone.gemfire.internal.util.ArrayUtils;
import com.pivotal.gemfirexd.internal.engine.sql.catalog.ExtraTableInfo;
import com.pivotal.gemfirexd.internal.engine.store.CompactCompositeKey;
import com.pivotal.gemfirexd.internal.engine.store.GemFireContainer;
import com.pivotal.gemfirexd.internal.engine.store.RegionEntryUtils;
import com.pivotal.gemfirexd.internal.engine.store.RowFormatter;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.services.cache.ClassSize;
import com.pivotal.gemfirexd.internal.iapi.services.io.ArrayInputStream;
import com.pivotal.gemfirexd.internal.iapi.sql.execute.ExecRow;
import com.pivotal.gemfirexd.internal.iapi.types.BooleanDataValue;
import com.pivotal.gemfirexd.internal.iapi.types.DataTypeDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueFactory;
import com.pivotal.gemfirexd.internal.iapi.types.RowLocation;
import com.pivotal.gemfirexd.internal.shared.common.StoredFormatIds;
import com.gemstone.gemfire.internal.cache.DiskId;
import com.gemstone.gemfire.internal.cache.DiskStoreImpl;
import com.gemstone.gemfire.internal.cache.AbstractDiskRegionEntry;
public class VersionedBucketRowLocationThinDiskRegionEntryHeap extends RowLocationThinDiskRegionEntry
    implements VersionStamp
{
  public VersionedBucketRowLocationThinDiskRegionEntryHeap (RegionEntryContext context, Object key,
    Object value
      ) {
    super(context,
          (value instanceof RecoveredEntry ? null : value)
        );
    initialize(context, value);
    this.tableInfo = RegionEntryUtils.entryGetTableInfo(context, key, value);
    this.bucketId = RegionEntryUtils.getBucketId(context);
    this.key = RegionEntryUtils.entryGetRegionKey(key, value);
  }
  protected int hash;
  private HashEntry<Object, Object> next;
  @SuppressWarnings("unused")
  private volatile long lastModified;
  private static final AtomicLongFieldUpdater<VersionedBucketRowLocationThinDiskRegionEntryHeap> lastModifiedUpdater
    = AtomicUpdaterFactory.newLongFieldUpdater(VersionedBucketRowLocationThinDiskRegionEntryHeap.class, "lastModified");
  protected long getlastModifiedField() {
    return lastModifiedUpdater.get(this);
  }
  protected boolean compareAndSetLastModifiedField(long expectedValue, long newValue) {
    return lastModifiedUpdater.compareAndSet(this, expectedValue, newValue);
  }
  @Override
  public final int getEntryHash() {
    return this.hash;
  }
  @Override
  protected void setEntryHash(int v) {
    this.hash = v;
  }
  @Override
  public final HashEntry<Object, Object> getNextEntry() {
    return this.next;
  }
  @Override
  public final void setNextEntry(final HashEntry<Object, Object> n) {
    this.next = n;
  }
  protected void initialize(RegionEntryContext context, Object value) {
    diskInitialize(context, value);
  }
  @Override
  public int updateAsyncEntrySize(EnableLRU capacityController) {
    throw new IllegalStateException("should never be called");
  }
  private void diskInitialize(RegionEntryContext context, Object value) {
    DiskRecoveryStore drs = (DiskRecoveryStore)context;
    DiskStoreImpl ds = drs.getDiskStore();
    long maxOplogSize = ds.getMaxOplogSize();
    this.id = DiskId.createDiskId(maxOplogSize, true , ds.needsLinkedList());
    Helper.initialize(this, drs, value);
  }
  protected DiskId id;
  public DiskId getDiskId() {
    return this.id;
  }
  @Override
  public void setDiskId(RegionEntry old) {
    this.id = ((AbstractDiskRegionEntry)old).getDiskId();
  }
  private VersionSource memberID;
  private short entryVersionLowBytes;
  private short regionVersionHighBytes;
  private int regionVersionLowBytes;
  private byte entryVersionHighByte;
  private byte distributedSystemId;
  public int getEntryVersion() {
    return ((entryVersionHighByte << 16) & 0xFF0000) | (entryVersionLowBytes & 0xFFFF);
  }
  public long getRegionVersion() {
    return (((long)regionVersionHighBytes) << 32) | (regionVersionLowBytes & 0x00000000FFFFFFFFL);
  }
  public long getVersionTimeStamp() {
    return getLastModified();
  }
  public void setVersionTimeStamp(long time) {
    setLastModified(time);
  }
  public VersionSource getMemberID() {
    return this.memberID;
  }
  public int getDistributedSystemId() {
    return this.distributedSystemId;
  }
  public void setVersions(VersionTag tag) {
    this.memberID = tag.getMemberID();
    int eVersion = tag.getEntryVersion();
    this.entryVersionLowBytes = (short)(eVersion & 0xffff);
    this.entryVersionHighByte = (byte)((eVersion & 0xff0000) >> 16);
    this.regionVersionHighBytes = tag.getRegionVersionHighBytes();
    this.regionVersionLowBytes = tag.getRegionVersionLowBytes();
    if (!(tag.isGatewayTag()) && this.distributedSystemId == tag.getDistributedSystemId()) {
      if (getVersionTimeStamp() <= tag.getVersionTimeStamp()) {
        setVersionTimeStamp(tag.getVersionTimeStamp());
      } else {
        tag.setVersionTimeStamp(getVersionTimeStamp());
      }
    } else {
      setVersionTimeStamp(tag.getVersionTimeStamp());
    }
    this.distributedSystemId = (byte)(tag.getDistributedSystemId() & 0xff);
  }
  public void setMemberID(VersionSource memberID) {
    this.memberID = memberID;
  }
  @Override
  public VersionStamp getVersionStamp() {
    return this;
  }
  public VersionTag asVersionTag() {
    VersionTag tag = VersionTag.create(memberID);
    tag.setEntryVersion(getEntryVersion());
    tag.setRegionVersion(this.regionVersionHighBytes, this.regionVersionLowBytes);
    tag.setVersionTimeStamp(getVersionTimeStamp());
    tag.setDistributedSystemId(this.distributedSystemId);
    return tag;
  }
  public void processVersionTag(LocalRegion r, VersionTag tag,
      boolean isTombstoneFromGII, boolean hasDelta,
      VersionSource thisVM, InternalDistributedMember sender, boolean checkForConflicts) {
    basicProcessVersionTag(r, tag, isTombstoneFromGII, hasDelta, thisVM, sender, checkForConflicts);
  }
  @Override
  public void processVersionTag(EntryEvent cacheEvent) {
    super.processVersionTag(cacheEvent);
  }
  public short getRegionVersionHighBytes() {
    return this.regionVersionHighBytes;
  }
  public int getRegionVersionLowBytes() {
    return this.regionVersionLowBytes;
  }
  private Object key;
  @Override
  public final Object getRawKey() {
    return this.key;
  }
  @Override
  protected void _setRawKey(Object key) {
    this.key = key;
  }
  private volatile Object value;
  @Override
  public final boolean isRemoved() {
    final Object o = this.value;
    return (o == Token.REMOVED_PHASE1) || (o == Token.REMOVED_PHASE2) || (o == Token.TOMBSTONE);
  }
  @Override
  public final boolean isDestroyedOrRemoved() {
    final Object o = this.value;
    return o == Token.DESTROYED || o == Token.REMOVED_PHASE1 || o == Token.REMOVED_PHASE2 || o == Token.TOMBSTONE;
  }
  @Override
  public final boolean isDestroyedOrRemovedButNotTombstone() {
    final Object o = this.value;
    return o == Token.DESTROYED || o == Token.REMOVED_PHASE1 || o == Token.REMOVED_PHASE2;
  }
  @Override
  protected Object getValueField() {
    return this.value;
  }
  @Override
  protected void setValueField(Object v) {
    this.value = v;
  }
  private transient ExtraTableInfo tableInfo;
  @Override
  public final ExtraTableInfo getTableInfo(GemFireContainer baseContainer) {
    return this.tableInfo;
  }
  @Override
  public final Object getContainerInfo() {
    return this.tableInfo;
  }
  @Override
  public final Object setContainerInfo(final LocalRegion owner, final Object val) {
    final GemFireContainer container;
    ExtraTableInfo tabInfo;
    if (owner == null) {
      final RowFormatter rf;
      if ((tabInfo = this.tableInfo) != null
          && (rf = tabInfo.getRowFormatter()) != null) {
        container = rf.container;
      }
      else {
        return null;
      }
    }
    else {
      container = (GemFireContainer)owner.getUserAttribute();
    }
    if (container != null && container.isByteArrayStore()) {
      tabInfo = container.getExtraTableInfo(val);
      this.tableInfo = tabInfo;
      if (tabInfo != null && tabInfo.regionKeyPartOfValue()) {
        return tabInfo;
      }
    }
    return null;
  }
  @Override
  public int estimateMemoryUsage() {
    return ClassSize.refSize;
  }
  @Override
  public int getTypeFormatId() {
    return StoredFormatIds.ACCESS_MEM_HEAP_ROW_LOCATION_ID;
  }
  @Override
  public final Object cloneObject() {
    return this;
  }
  @Override
  public final RowLocation getClone() {
    return this;
  }
  @Override
  public final int compare(DataValueDescriptor other) {
    if (this == other) {
      return 0;
    }
    return this.hashCode() - other.hashCode();
  }
  @Override
  public DataValueDescriptor recycle() {
    return this;
  }
  @Override
  public DataValueDescriptor getNewNull() {
    return DataValueFactory.DUMMY;
  }
  @Override
  public boolean isNull() {
    return this == DataValueFactory.DUMMY;
  }
  @Override
  public Object getObject() throws StandardException {
    return this;
  }
  @Override
  public DataValueDescriptor coalesce(DataValueDescriptor[] list,
      DataValueDescriptor returnValue) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int compare(DataValueDescriptor other, boolean nullsOrderedLow)
      throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, DataValueDescriptor other,
      boolean orderedNulls, boolean unknownRV) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, DataValueDescriptor other,
      boolean orderedNulls, boolean nullsOrderedLow, boolean unknownRV)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue equals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int getLengthInBytes(DataTypeDescriptor dtd) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue greaterOrEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue greaterThan(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue in(DataValueDescriptor left,
      DataValueDescriptor[] inList, boolean orderedList)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue isNotNull() {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue isNullOp() {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue lessOrEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue lessThan(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void normalize(DataTypeDescriptor dtd, DataValueDescriptor source)
      throws StandardException {
  }
  @Override
  public BooleanDataValue notEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void readExternalFromArray(ArrayInputStream ais) throws IOException,
  ClassNotFoundException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void setValue(DataValueDescriptor theValue) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int writeBytes(byte[] outBytes, int offset, DataTypeDescriptor dtd) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int computeHashCode(int maxWidth, int hash) {
    throw new UnsupportedOperationException("unexpected invocation for " + toString());
  }
  @Override
  public final DataValueDescriptor getKeyColumn(int index) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public final void getKeyColumns(DataValueDescriptor[] keys) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, ExecRow row, boolean byteArrayStore,
      int colIdx, boolean orderedNulls, boolean unknownRV)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, CompactCompositeKey key, int colIdx,
      boolean orderedNulls, boolean unknownRV) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int equals(RowFormatter rf, byte[] bytes, boolean isKeyBytes,
      int logicalPosition, int keyBytesPos, final DataValueDescriptor[] outDVD)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public byte getTypeId() {
    throw new UnsupportedOperationException("Implement the method for DataType="+ this);
  }
  @Override
  public void writeNullDVD(DataOutput out) throws IOException{
    throw new UnsupportedOperationException("Implement the method for DataType="+ this);
  }
  @Override
  public Object getValueWithoutFaultInOrOffHeapEntry(LocalRegion owner) {
    final Object value = this.value;
    return value != null && !Token.isRemovedFromDisk(value)
        ? value : Helper.getValueHeapOrDiskWithoutFaultIn(this, owner);
  }
  @Override
  public Object getValueOrOffHeapEntry(LocalRegion owner) {
    return this.getValue(owner);
  }
  @Override
  public Object getRawValue() {
    return this._getValue();
  }
  @Override
  public Version[] getSerializationVersions() {
    return null;
  }
  private final int bucketId;
  @Override
  public Object getValue(GemFireContainer baseContainer) {
    return RegionEntryUtils.getValue(baseContainer, this.bucketId, this);
  }
  @Override
  public Object getValueWithoutFaultIn(GemFireContainer baseContainer) {
    return RegionEntryUtils.getValueWithoutFaultIn(baseContainer,this.bucketId, this);
  }
  @Override
  public ExecRow getRow(GemFireContainer baseContainer) {
    return RegionEntryUtils.getRow(baseContainer, this.bucketId, this, this.tableInfo);
  }
  @Override
  public ExecRow getRowWithoutFaultIn(GemFireContainer baseContainer) {
    return RegionEntryUtils.getRowWithoutFaultIn(baseContainer, this.bucketId, this,
        this.tableInfo);
  }
  @Override
  public int getBucketID() {
    return this.bucketId;
  }
  @Override
  protected StringBuilder appendFieldsToString(final StringBuilder sb) {
    sb.append("key=");
    final Object k = getRawKey();
    final Object val = _getValue();
    RegionEntryUtils.entryKeyString(k, val, getTableInfo(null), sb);
    sb.append("; rawValue=");
    ArrayUtils.objectStringNonRecursive(val, sb);
    sb.append("; lockState=0x").append(Integer.toHexString(getState()));
    sb.append("; bucketId=").append(this.bucketId);
    return sb;
  }
  private static RegionEntryFactory factory = new RegionEntryFactory() {
    public final RegionEntry createEntry(RegionEntryContext context, Object key, Object value) {
      return new VersionedBucketRowLocationThinDiskRegionEntryHeap(context, key, value);
    }
    public final Class<?> getEntryClass() {
      return VersionedBucketRowLocationThinDiskRegionEntryHeap.class;
    }
    public RegionEntryFactory makeVersioned() {
      return this;
    }
    @Override
    public RegionEntryFactory makeOnHeap() {
      return this;
    }
  };
  public static RegionEntryFactory getEntryFactory() {
    return factory;
  }
}
