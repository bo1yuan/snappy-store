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

package com.gemstone.gemfire.internal.concurrent;

import java.util.Map;
import java.util.Map.Entry;

import com.gemstone.gemfire.internal.util.ArrayUtils;

/**
 * An Entry maintaining a key and a value. The value may be changed using the
 * <tt>setValue</tt> method. This class facilitates the process of building
 * custom map implementations. For example, it may be convenient to return
 * arrays of <tt>SimpleEntry</tt> instances in method
 * <tt>Map.entrySet().toArray</tt>.
 */
public class SimpleReusableEntry<K, V> implements Map.Entry<K, V> {

  protected K key; // GemStone change; made non-final to enable reuse
  private V value;
  private final boolean compareValues;

  /**
   * Creates an entry representing a mapping from the specified key to the
   * specified value.
   * 
   * @param key
   *          the key represented by this entry
   * @param value
   *          the value represented by this entry
   */
  public SimpleReusableEntry(K key, V value, boolean compareValues) {
    this.key = key;
    this.value = value;
    this.compareValues = compareValues;
  }

  /**
   * Creates an entry representing the same mapping as the specified entry.
   * 
   * @param entry
   *          the entry to copy
   */
  public SimpleReusableEntry(final Entry<? extends K, ? extends V> entry,
      boolean compareValues) {
    this.key = entry.getKey();
    this.value = entry.getValue();
    this.compareValues = compareValues;
  }

  /**
   * Returns the key corresponding to this entry.
   * 
   * @return the key corresponding to this entry
   */
  public final K getKey() {
    return this.key;
  }

  /**
   * Returns the value corresponding to this entry.
   * 
   * @return the value corresponding to this entry
   */
  public final V getValue() {
    return this.value;
  }

  /**
   * Replaces the value corresponding to this entry with the specified value.
   * 
   * @param value
   *          new value to be stored in this entry
   * @return the old value corresponding to the entry
   */
  public V setValue(final V value) {
    final V oldValue = this.value;
    this.value = value;
    return oldValue;
  }

  public final void setRawKeyValue(K key, V value) {
    this.key = key;
    this.value = value;
  }

  /**
   * Compares the specified object with this entry for equality. Returns
   * {@code true} if the given object is also a map entry and the two entries
   * represent the same mapping. More formally, two entries {@code e1} and
   * {@code e2} represent the same mapping if
   * 
   * <pre>
   * (e1.getKey() == null ? e2.getKey() == null : e1.getKey().equals(
   *     e2.getKey())) &amp;&amp; (e1.getValue() == null ?
   *         e2.getValue() == null : e1.getValue().equals(e2.getValue()))
   * </pre>
   * 
   * This ensures that the {@code equals} method works properly across
   * different implementations of the {@code Map.Entry} interface.
   * 
   * @param o
   *          object to be compared for equality with this map entry
   * @return {@code true} if the specified object is equal to this map entry
   * @see #hashCode
   */
  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof Map.Entry<?, ?>)) {
      return false;
    }
    final Map.Entry<?, ?> e = (Map.Entry<?, ?>)o;
    if (this.compareValues) {
      return ArrayUtils.objectEquals(this.key, e.getKey())
          && ArrayUtils.objectEquals(this.value, e.getValue());
    }
    return this.key == e.getKey() && this.value == e.getValue();
  }

  /**
   * Returns the hash code value for this map entry. The hash code of a map
   * entry {@code e} is defined to be:
   * 
   * <pre>
   * (e.getKey() == null ? 0 : e.getKey().hashCode())
   *     &circ; (e.getValue() == null ? 0 : e.getValue().hashCode())
   * </pre>
   * 
   * This ensures that {@code e1.equals(e2)} implies that
   * {@code e1.hashCode()==e2.hashCode()} for any two Entries {@code e1} and
   * {@code e2}, as required by the general contract of
   * {@link Object#hashCode}.
   * 
   * @return the hash code value for this map entry
   * @see #equals
   */
  @Override
  public int hashCode() {
    if (this.compareValues) {
      return (this.key != null ? this.key.hashCode() : 0)
          ^ (this.value != null ? this.value.hashCode() : 0);
    }
    return System.identityHashCode(this.key)
        ^ System.identityHashCode(this.value);
  }

  /**
   * Returns a String representation of this map entry. This implementation
   * returns the string representation of this entry's key followed by the
   * equals character ("<tt>=</tt>") followed by the string representation of
   * this entry's value.
   * 
   * @return a String representation of this map entry
   */
  @Override
  public String toString() {
    return this.key + "=" + this.value;
  }
}
