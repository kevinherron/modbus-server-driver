package com.kevinherron.ignition.modbus.util;

public interface ByteOps<T> {

  boolean getBoolean(T bytes, int index);

  short getShort(T bytes, int index);

  int getInt(T bytes, int index);

  long getLong(T bytes, int index);

  float getFloat(T bytes, int index);

  double getDouble(T bytes, int index);

  void setBoolean(T bytes, int index, boolean value);

  void setShort(T bytes, int index, short value);

  void setInt(T bytes, int index, int value);

  void setLong(T bytes, int index, long value);

  void setFloat(T bytes, int index, float value);

  void setDouble(T bytes, int index, double value);

}
