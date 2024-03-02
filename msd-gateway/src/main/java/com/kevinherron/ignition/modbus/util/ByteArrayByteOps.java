package com.kevinherron.ignition.modbus.util;

public class ByteArrayByteOps extends AbstractByteOps<byte[]> {

  public static final ByteArrayByteOps BIG_ENDIAN =
      new ByteArrayByteOps(new OrderedOps.BigEndianOps());

  public static final ByteArrayByteOps LITTLE_ENDIAN =
      new ByteArrayByteOps(new OrderedOps.LittleEndianOps());

  public static final ByteArrayByteOps BIG_ENDIAN_WORD_SWAPPED =
      new ByteArrayByteOps(new OrderedOps.BigEndianWordSwappedOps());

  public static final ByteArrayByteOps LITTLE_ENDIAN_WORD_SWAPPED =
      new ByteArrayByteOps(new OrderedOps.LittleEndianWordSwappedOps());


  public ByteArrayByteOps(OrderedOps orderedOps) {
    super(orderedOps);
  }

  @Override
  protected int get(byte[] bytes, int index) {
    return bytes[index] & 0xFF;
  }

  @Override
  protected void set(byte[] bytes, int index, int value) {
    bytes[index] = (byte) (value & 0xFF);
  }

}
