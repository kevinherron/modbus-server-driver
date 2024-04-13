package com.kevinherron.ignition.modbus.util;

import java.nio.ByteBuffer;

/**
 * {@link ByteOps} implementation that operates on {@link ByteBuffer}s.
 */
public class ByteBufferByteOps extends AbstractByteOps<ByteBuffer> {

  public static final ByteBufferByteOps BIG_ENDIAN =
      new ByteBufferByteOps(new OrderedOps.BigEndianOps());

  public static final ByteBufferByteOps LITTLE_ENDIAN =
      new ByteBufferByteOps(new OrderedOps.LittleEndianOps());

  public static final ByteBufferByteOps BIG_ENDIAN_WORD_SWAPPED =
      new ByteBufferByteOps(new OrderedOps.BigEndianWordSwappedOps());

  public static final ByteBufferByteOps LITTLE_ENDIAN_WORD_SWAPPED =
      new ByteBufferByteOps(new OrderedOps.LittleEndianWordSwappedOps());


  public ByteBufferByteOps(OrderedOps orderedOps) {
    super(orderedOps);
  }

  @Override
  protected int get(ByteBuffer bytes, int index) {
    return bytes.get(index) & 0xFF;
  }

  @Override
  protected void set(ByteBuffer bytes, int index, int value) {
    bytes.put(index, (byte) (value & 0xFF));
  }

}
