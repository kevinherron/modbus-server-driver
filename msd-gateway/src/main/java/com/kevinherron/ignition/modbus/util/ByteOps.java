package com.kevinherron.ignition.modbus.util;

public interface ByteOps<T> {

  /**
   * Get the boolean value at the given {@code index} in {@code bytes}.
   *
   * <p>A zero value is false and any non-zero is true.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the boolean value at the given {@code index}.
   */
  boolean getBoolean(T bytes, int index);

  /**
   * Get the byte value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the byte value at the given {@code index}.
   */
  byte getByte(T bytes, int index);

  /**
   * Get the short value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the short value at the given {@code index}.
   */
  short getShort(T bytes, int index);

  /**
   * Get the int value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the int value at the given {@code index}.
   */
  int getInt(T bytes, int index);

  /**
   * Get the long value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the long value at the given {@code index}.
   */
  long getLong(T bytes, int index);

  /**
   * Get the float value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the float value at the given {@code index}.
   */
  float getFloat(T bytes, int index);

  /**
   * Get the double value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to get the value from.
   * @param index the index into {@code bytes} to get the value at.
   * @return the double value at the given {@code index}.
   */
  double getDouble(T bytes, int index);

  /**
   * Set the boolean value at the given {@code index} in {@code bytes}.
   *
   * <p>A false value is set as zero and a true value is set as one.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the boolean value to set.
   */
  void setBoolean(T bytes, int index, boolean value);

  /**
   * Set the byte value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the byte value to set.
   */
  void setByte(T bytes, int index, byte value);

  /**
   * Set the short value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the short value to set.
   */
  void setShort(T bytes, int index, short value);

  /**
   * Set the int value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the int value to set.
   */
  void setInt(T bytes, int index, int value);

  /**
   * Set the long value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the long value to set.
   */
  void setLong(T bytes, int index, long value);

  /**
   * Set the float value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the float value to set.
   */
  void setFloat(T bytes, int index, float value);

  /**
   * Set the double value at the given {@code index} in {@code bytes}.
   *
   * @param bytes the bytes to set the value in.
   * @param index the index into {@code bytes} to set the value at.
   * @param value the double value to set.
   */
  void setDouble(T bytes, int index, double value);

}
