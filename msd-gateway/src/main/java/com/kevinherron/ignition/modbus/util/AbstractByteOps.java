package com.kevinherron.ignition.modbus.util;

public abstract class AbstractByteOps<T> implements ByteOps<T> {

  private final OrderedOps orderedOps;

  public AbstractByteOps(OrderedOps orderedOps) {
    this.orderedOps = orderedOps;
  }

  protected abstract int get(T bytes, int index);

  protected abstract void set(T bytes, int index, int value);

  @Override
  public boolean getBoolean(T bytes, int index) {
    return get(bytes, index) != 0;
  }

  @Override
  public byte getByte(T bytes, int index) {
    return (byte) get(bytes, index);
  }

  @Override
  public short getShort(T bytes, int index) {
    return orderedOps.getShort(idx -> get(bytes, index + idx));
  }

  @Override
  public int getInt(T bytes, int index) {
    return orderedOps.getInt(idx -> get(bytes, index + idx));
  }

  @Override
  public long getLong(T bytes, int index) {
    return orderedOps.getLong(idx -> get(bytes, index + idx));
  }

  @Override
  public float getFloat(T bytes, int index) {
    return Float.intBitsToFloat(getInt(bytes, index));
  }

  @Override
  public double getDouble(T bytes, int index) {
    return Double.longBitsToDouble(getLong(bytes, index));
  }

  @Override
  public void setBoolean(T bytes, int index, boolean value) {
    if (value) {
      set(bytes, index, 1);
    } else {
      set(bytes, index, 0);
    }
  }

  @Override
  public void setByte(T bytes, int index, byte value) {
    set(bytes, index, value);
  }

  @Override
  public void setShort(T bytes, int index, short value) {
    orderedOps.setShort(value, (idx, b) -> set(bytes, index + idx, b));
  }

  @Override
  public void setInt(T bytes, int index, int value) {
    orderedOps.setInt(value, (idx, b) -> set(bytes, index + idx, b));
  }

  @Override
  public void setLong(T bytes, int index, long value) {
    orderedOps.setLong(value, (idx, b) -> set(bytes, index + idx, b));
  }

  @Override
  public void setFloat(T bytes, int index, float value) {
    setInt(bytes, index, Float.floatToRawIntBits(value));
  }

  @Override
  public void setDouble(T bytes, int index, double value) {
    setLong(bytes, index, Double.doubleToRawLongBits(value));
  }

}
