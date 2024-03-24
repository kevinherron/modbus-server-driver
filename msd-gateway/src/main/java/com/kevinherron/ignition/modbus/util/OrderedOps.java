package com.kevinherron.ignition.modbus.util;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Operations that span multiple bytes/words that need to be aware of the underlying byte/word
 * order.
 */
public interface OrderedOps {

  short getShort(Function<Integer, Integer> getByte);

  int getInt(Function<Integer, Integer> getByte);

  long getLong(Function<Integer, Integer> getByte);

  void setShort(short value, BiConsumer<Integer, Integer> setByte);

  void setInt(int value, BiConsumer<Integer, Integer> setByte);

  void setLong(long value, BiConsumer<Integer, Integer> setByte);

  final class BigEndianOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      return (short) (b0 << 8 | b1);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      int b2 = getByte.apply(2);
      int b3 = getByte.apply(3);
      return b0 << 24 | b1 << 16 | b2 << 8 | b3;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte) {
      long b0 = getByte.apply(0);
      long b1 = getByte.apply(1);
      long b2 = getByte.apply(2);
      long b3 = getByte.apply(3);
      long b4 = getByte.apply(4);
      long b5 = getByte.apply(5);
      long b6 = getByte.apply(6);
      long b7 = getByte.apply(7);
      return b0 << 56 | b1 << 48 | b2 << 40 | b3 << 32 | b4 << 24 | b5 << 16 | b6 << 8 | b7;
    }

    @Override
    public void setShort(short value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value >> 8 & 0xFF);
      setByte.accept(1, value & 0xFF);
    }

    @Override
    public void setInt(int value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value >> 24 & 0xFF);
      setByte.accept(1, value >> 16 & 0xFF);
      setByte.accept(2, value >> 8 & 0xFF);
      setByte.accept(3, value & 0xFF);
    }

    @Override
    public void setLong(long value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, (int) (value >> 56 & 0xFF));
      setByte.accept(1, (int) (value >> 48 & 0xFF));
      setByte.accept(2, (int) (value >> 40 & 0xFF));
      setByte.accept(3, (int) (value >> 32 & 0xFF));
      setByte.accept(4, (int) (value >> 24 & 0xFF));
      setByte.accept(5, (int) (value >> 16 & 0xFF));
      setByte.accept(6, (int) (value >> 8 & 0xFF));
      setByte.accept(7, (int) (value & 0xFF));
    }

  }

  final class LittleEndianOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      return (short) (b1 << 8 | b0);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      int b2 = getByte.apply(2);
      int b3 = getByte.apply(3);
      return b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte) {
      long b0 = getByte.apply(0);
      long b1 = getByte.apply(1);
      long b2 = getByte.apply(2);
      long b3 = getByte.apply(3);
      long b4 = getByte.apply(4);
      long b5 = getByte.apply(5);
      long b6 = getByte.apply(6);
      long b7 = getByte.apply(7);
      return b7 << 56 | b6 << 48 | b5 << 40 | b4 << 32 | b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    @Override
    public void setShort(short value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value & 0xFF);
      setByte.accept(1, value >> 8 & 0xFF);
    }

    @Override
    public void setInt(int value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value & 0xFF);
      setByte.accept(1, value >> 8 & 0xFF);
      setByte.accept(2, value >> 16 & 0xFF);
      setByte.accept(3, value >> 24 & 0xFF);
    }

    @Override
    public void setLong(long value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, (int) (value & 0xFF));
      setByte.accept(1, (int) (value >> 8 & 0xFF));
      setByte.accept(2, (int) (value >> 16 & 0xFF));
      setByte.accept(3, (int) (value >> 24 & 0xFF));
      setByte.accept(4, (int) (value >> 32 & 0xFF));
      setByte.accept(5, (int) (value >> 40 & 0xFF));
      setByte.accept(6, (int) (value >> 48 & 0xFF));
      setByte.accept(7, (int) (value >> 56 & 0xFF));
    }

  }

  final class BigEndianWordSwappedOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      return (short) (b0 << 8 | b1);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      int b2 = getByte.apply(2);
      int b3 = getByte.apply(3);
      return b2 << 24 | b3 << 16 | b0 << 8 | b1;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte) {
      long b0 = getByte.apply(0);
      long b1 = getByte.apply(1);
      long b2 = getByte.apply(2);
      long b3 = getByte.apply(3);
      long b4 = getByte.apply(4);
      long b5 = getByte.apply(5);
      long b6 = getByte.apply(6);
      long b7 = getByte.apply(7);
      return b6 << 56 | b7 << 48 | b4 << 40 | b5 << 32 | b2 << 24 | b3 << 16 | b0 << 8 | b1;
    }

    @Override
    public void setShort(short value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value >> 8 & 0xFF);
      setByte.accept(1, value & 0xFF);
    }

    @Override
    public void setInt(int value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value >> 8 & 0xFF);
      setByte.accept(1, value & 0xFF);
      setByte.accept(2, value >> 24 & 0xFF);
      setByte.accept(3, value >> 16 & 0xFF);
    }

    @Override
    public void setLong(long value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, (int) (value >> 8 & 0xFF));
      setByte.accept(1, (int) (value & 0xFF));
      setByte.accept(2, (int) (value >> 24 & 0xFF));
      setByte.accept(3, (int) (value >> 16 & 0xFF));
      setByte.accept(4, (int) (value >> 40 & 0xFF));
      setByte.accept(5, (int) (value >> 32 & 0xFF));
      setByte.accept(6, (int) (value >> 56 & 0xFF));
      setByte.accept(7, (int) (value >> 48 & 0xFF));
    }

  }

  final class LittleEndianWordSwappedOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      return (short) (b1 << 8 | b0);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte) {
      int b0 = getByte.apply(0);
      int b1 = getByte.apply(1);
      int b2 = getByte.apply(2);
      int b3 = getByte.apply(3);
      return b3 << 8 | b2 | b1 << 24 | b0 << 16;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte) {
      long b0 = getByte.apply(0);
      long b1 = getByte.apply(1);
      long b2 = getByte.apply(2);
      long b3 = getByte.apply(3);
      long b4 = getByte.apply(4);
      long b5 = getByte.apply(5);
      long b6 = getByte.apply(6);
      long b7 = getByte.apply(7);
      return b7 << 8 | b6 | b5 << 24 | b4 << 16 | b3 << 40 | b2 << 32 | b1 << 56 | b0 << 48;
    }

    @Override
    public void setShort(short value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(0, value & 0xFF);
      setByte.accept(1, value >> 8 & 0xFF);
    }

    @Override
    public void setInt(int value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(1, value >> 24 & 0xFF);
      setByte.accept(0, value >> 16 & 0xFF);
      setByte.accept(3, value >> 8 & 0xFF);
      setByte.accept(2, value & 0xFF);
    }

    @Override
    public void setLong(long value, BiConsumer<Integer, Integer> setByte) {
      setByte.accept(1, (int) (value >> 56 & 0xFF));
      setByte.accept(0, (int) (value >> 48 & 0xFF));
      setByte.accept(3, (int) (value >> 40 & 0xFF));
      setByte.accept(2, (int) (value >> 32 & 0xFF));
      setByte.accept(5, (int) (value >> 24 & 0xFF));
      setByte.accept(4, (int) (value >> 16 & 0xFF));
      setByte.accept(7, (int) (value >> 8 & 0xFF));
      setByte.accept(6, (int) (value & 0xFF));
    }

  }

}
