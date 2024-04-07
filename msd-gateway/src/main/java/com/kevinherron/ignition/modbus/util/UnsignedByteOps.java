package com.kevinherron.ignition.modbus.util;

import org.joou.UByte;
import org.joou.UInteger;
import org.joou.ULong;
import org.joou.UShort;

/**
 * Additional operations for working with unsigned numbers.
 *
 * @param <T> the type of bytes.
 */
public final class UnsignedByteOps<T> implements ByteOps<T> {

  /**
   * Create a new {@link UnsignedByteOps} derived from the given {@link ByteOps}.
   *
   * @param ops the {@link ByteOps} to derive from.
   * @param <T> the type of bytes.
   * @return a new {@link UnsignedByteOps}.
   */
  public static <T> UnsignedByteOps<T> of(ByteOps<T> ops) {
    return new UnsignedByteOps<>(ops);
  }

  private final ByteOps<T> delegate;

  public UnsignedByteOps(ByteOps<T> delegate) {
    this.delegate = delegate;
  }

  /**
   * Get the {@link UByte} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the {@link UByte} value at the given {@code index}.
   */
  public UByte getUByte(T bytes, int index) {
    return UByte.valueOf(getByte(bytes, index));
  }

  /**
   * Get the {@link UShort} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the {@link UShort} value at the given {@code index}.
   */
  public UShort getUShort(T bytes, int index) {
    return UShort.valueOf(getShort(bytes, index));
  }

  /**
   * Get the {@link UInteger} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the {@link UInteger} value at the given {@code index}.
   */
  public UInteger getUInt(T bytes, int index) {
    return UInteger.valueOf(getInt(bytes, index));
  }

  /**
   * Get the {@link ULong} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the {@link ULong} value at the given {@code index}.
   */
  public ULong getULong(T bytes, int index) {
    return ULong.valueOf(getLong(bytes, index));
  }

  /**
   * Set the {@link UByte} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the {@link UByte} value to set.
   */
  public void setUByte(T bytes, int index, UByte value) {
    setByte(bytes, index, value.byteValue());
  }

  /**
   * Set the {@link UShort} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the {@link UShort} value to set.
   */
  public void setUShort(T bytes, int index, UShort value) {
    setShort(bytes, index, value.shortValue());
  }

  /**
   * Set the {@link UInteger} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the {@link UInteger} value to set.
   */
  public void setUInt(T bytes, int index, UInteger value) {
    setInt(bytes, index, value.intValue());
  }

  /**
   * Set the {@link ULong} value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the {@link ULong} value to set.
   */
  public void setULong(T bytes, int index, ULong value) {
    setLong(bytes, index, value.longValue());
  }

  //region ByteOps delegated methods

  @Override
  public boolean getBoolean(T bytes, int index) {
    return delegate.getBoolean(bytes, index);
  }

  @Override
  public byte getByte(T bytes, int index) {
    return delegate.getByte(bytes, index);
  }

  @Override
  public short getShort(T bytes, int index) {
    return delegate.getShort(bytes, index);
  }

  @Override
  public int getInt(T bytes, int index) {
    return delegate.getInt(bytes, index);
  }

  @Override
  public long getLong(T bytes, int index) {
    return delegate.getLong(bytes, index);
  }

  @Override
  public float getFloat(T bytes, int index) {
    return delegate.getFloat(bytes, index);
  }

  @Override
  public double getDouble(T bytes, int index) {
    return delegate.getDouble(bytes, index);
  }

  @Override
  public void setBoolean(T bytes, int index, boolean value) {
    delegate.setBoolean(bytes, index, value);
  }

  @Override
  public void setByte(T bytes, int index, byte value) {
    delegate.setByte(bytes, index, value);
  }

  @Override
  public void setShort(T bytes, int index, short value) {
    delegate.setShort(bytes, index, value);
  }

  @Override
  public void setInt(T bytes, int index, int value) {
    delegate.setInt(bytes, index, value);
  }

  @Override
  public void setLong(T bytes, int index, long value) {
    delegate.setLong(bytes, index, value);
  }

  @Override
  public void setFloat(T bytes, int index, float value) {
    delegate.setFloat(bytes, index, value);
  }

  @Override
  public void setDouble(T bytes, int index, double value) {
    delegate.setDouble(bytes, index, value);
  }

  //endregion

}
