package com.kevinherron.ignition.modbus.util;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Operations that span multiple bytes/words that need to be aware of the underlying byte/word
 * order.
 */
public interface OrderedOps {

  /**
   * Get a short value using the given {@code getByte} function, starting at {@code index}.
   *
   * @param getByte a function that takes an index and returns a byte value.
   * @param index the index to start at.
   * @return the assembled short value.
   */
  short getShort(Function<Integer, Integer> getByte, int index);

  /**
   * Get an int value using the given {@code getByte} function, starting at {@code index}.
   *
   * @param getByte a function that takes an index and returns a byte value.
   * @param index the index to start at.
   * @return the assembled int value.
   */
  int getInt(Function<Integer, Integer> getByte, int index);

  /**
   * Get a long value using the given {@code getByte} function, starting at {@code index}.
   *
   * @param getByte a function that takes an index and returns a byte value.
   * @param index the index to start at.
   * @return the assembled long value.
   */
  long getLong(Function<Integer, Integer> getByte, int index);

  /**
   * Set the bytes of a short value using the given {@code setByte} function, starting
   * {@code index}.
   *
   * @param setByte a function that takes an index and a byte value to set.
   * @param index the index to start at.
   * @param value the short value to set.
   */
  void setShort(BiConsumer<Integer, Integer> setByte, int index, short value);

  /**
   * Set the bytes of an int value using the given {@code setByte} function, starting at
   * {@code index}.
   *
   * @param setByte a function that takes an index and a byte value to set.
   * @param index the index to start at.
   * @param value the int value to set.
   */
  void setInt(BiConsumer<Integer, Integer> setByte, int index, int value);

  /**
   * Set the bytes of a long value using the given {@code setByte} function, starting at
   * {@code index}.
   *
   * @param setByte a function that takes an index and a byte value to set.
   * @param index the index to start at.
   * @param value the long value to set.
   */
  void setLong(BiConsumer<Integer, Integer> setByte, int index, long value);

  final class BigEndianOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      return (short) (b0 << 8 | b1);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      int b2 = getByte.apply(index + 2);
      int b3 = getByte.apply(index + 3);
      return b0 << 24 | b1 << 16 | b2 << 8 | b3;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte, int index) {
      long b0 = getByte.apply(index);
      long b1 = getByte.apply(index + 1);
      long b2 = getByte.apply(index + 2);
      long b3 = getByte.apply(index + 3);
      long b4 = getByte.apply(index + 4);
      long b5 = getByte.apply(index + 5);
      long b6 = getByte.apply(index + 6);
      long b7 = getByte.apply(index + 7);
      return b0 << 56 | b1 << 48 | b2 << 40 | b3 << 32 | b4 << 24 | b5 << 16 | b6 << 8 | b7;
    }

    @Override
    public void setShort(BiConsumer<Integer, Integer> setByte, int index, short value) {
      setByte.accept(index, value >> 8 & 0xFF);
      setByte.accept(index + 1, value & 0xFF);
    }

    @Override
    public void setInt(BiConsumer<Integer, Integer> setByte, int index, int value) {
      setByte.accept(index, value >> 24 & 0xFF);
      setByte.accept(index + 1, value >> 16 & 0xFF);
      setByte.accept(index + 2, value >> 8 & 0xFF);
      setByte.accept(index + 3, value & 0xFF);
    }

    @Override
    public void setLong(BiConsumer<Integer, Integer> setByte, int index, long value) {
      setByte.accept(index, (int) (value >> 56 & 0xFF));
      setByte.accept(index + 1, (int) (value >> 48 & 0xFF));
      setByte.accept(index + 2, (int) (value >> 40 & 0xFF));
      setByte.accept(index + 3, (int) (value >> 32 & 0xFF));
      setByte.accept(index + 4, (int) (value >> 24 & 0xFF));
      setByte.accept(index + 5, (int) (value >> 16 & 0xFF));
      setByte.accept(index + 6, (int) (value >> 8 & 0xFF));
      setByte.accept(index + 7, (int) (value & 0xFF));
    }

  }

  final class LittleEndianOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      return (short) (b1 << 8 | b0);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      int b2 = getByte.apply(index + 2);
      int b3 = getByte.apply(index + 3);
      return b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte, int index) {
      long b0 = getByte.apply(index);
      long b1 = getByte.apply(index + 1);
      long b2 = getByte.apply(index + 2);
      long b3 = getByte.apply(index + 3);
      long b4 = getByte.apply(index + 4);
      long b5 = getByte.apply(index + 5);
      long b6 = getByte.apply(index + 6);
      long b7 = getByte.apply(index + 7);
      return b7 << 56 | b6 << 48 | b5 << 40 | b4 << 32 | b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    @Override
    public void setShort(BiConsumer<Integer, Integer> setByte, int index, short value) {
      setByte.accept(index, value & 0xFF);
      setByte.accept(index + 1, value >> 8 & 0xFF);
    }

    @Override
    public void setInt(BiConsumer<Integer, Integer> setByte, int index, int value) {
      setByte.accept(index, value & 0xFF);
      setByte.accept(index + 1, value >> 8 & 0xFF);
      setByte.accept(index + 2, value >> 16 & 0xFF);
      setByte.accept(index + 3, value >> 24 & 0xFF);
    }

    @Override
    public void setLong(BiConsumer<Integer, Integer> setByte, int index, long value) {
      setByte.accept(index, (int) (value & 0xFF));
      setByte.accept(index + 1, (int) (value >> 8 & 0xFF));
      setByte.accept(index + 2, (int) (value >> 16 & 0xFF));
      setByte.accept(index + 3, (int) (value >> 24 & 0xFF));
      setByte.accept(index + 4, (int) (value >> 32 & 0xFF));
      setByte.accept(index + 5, (int) (value >> 40 & 0xFF));
      setByte.accept(index + 6, (int) (value >> 48 & 0xFF));
      setByte.accept(index + 7, (int) (value >> 56 & 0xFF));
    }

  }

  final class BigEndianWordSwappedOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      return (short) (b0 << 8 | b1);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      int b2 = getByte.apply(index + 2);
      int b3 = getByte.apply(index + 3);
      return b2 << 24 | b3 << 16 | b0 << 8 | b1;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte, int index) {
      long b0 = getByte.apply(index);
      long b1 = getByte.apply(index + 1);
      long b2 = getByte.apply(index + 2);
      long b3 = getByte.apply(index + 3);
      long b4 = getByte.apply(index + 4);
      long b5 = getByte.apply(index + 5);
      long b6 = getByte.apply(index + 6);
      long b7 = getByte.apply(index + 7);
      return b6 << 56 | b7 << 48 | b4 << 40 | b5 << 32 | b2 << 24 | b3 << 16 | b0 << 8 | b1;
    }

    @Override
    public void setShort(BiConsumer<Integer, Integer> setByte, int index, short value) {
      setByte.accept(index, value >> 8 & 0xFF);
      setByte.accept(index + 1, value & 0xFF);
    }

    @Override
    public void setInt(BiConsumer<Integer, Integer> setByte, int index, int value) {
      setByte.accept(index, value >> 8 & 0xFF);
      setByte.accept(index + 1, value & 0xFF);
      setByte.accept(index + 2, value >> 24 & 0xFF);
      setByte.accept(index + 3, value >> 16 & 0xFF);
    }

    @Override
    public void setLong(BiConsumer<Integer, Integer> setByte, int index, long value) {
      setByte.accept(index, (int) (value >> 8 & 0xFF));
      setByte.accept(index + 1, (int) (value & 0xFF));
      setByte.accept(index + 2, (int) (value >> 24 & 0xFF));
      setByte.accept(index + 3, (int) (value >> 16 & 0xFF));
      setByte.accept(index + 4, (int) (value >> 40 & 0xFF));
      setByte.accept(index + 5, (int) (value >> 32 & 0xFF));
      setByte.accept(index + 6, (int) (value >> 56 & 0xFF));
      setByte.accept(index + 7, (int) (value >> 48 & 0xFF));
    }

  }

  final class LittleEndianWordSwappedOps implements OrderedOps {

    @Override
    public short getShort(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      return (short) (b1 << 8 | b0);
    }

    @Override
    public int getInt(Function<Integer, Integer> getByte, int index) {
      int b0 = getByte.apply(index);
      int b1 = getByte.apply(index + 1);
      int b2 = getByte.apply(index + 2);
      int b3 = getByte.apply(index + 3);
      return b3 << 8 | b2 | b1 << 24 | b0 << 16;
    }

    @Override
    public long getLong(Function<Integer, Integer> getByte, int index) {
      long b0 = getByte.apply(index);
      long b1 = getByte.apply(index + 1);
      long b2 = getByte.apply(index + 2);
      long b3 = getByte.apply(index + 3);
      long b4 = getByte.apply(index + 4);
      long b5 = getByte.apply(index + 5);
      long b6 = getByte.apply(index + 6);
      long b7 = getByte.apply(index + 7);
      return b7 << 8 | b6 | b5 << 24 | b4 << 16 | b3 << 40 | b2 << 32 | b1 << 56 | b0 << 48;
    }

    @Override
    public void setShort(BiConsumer<Integer, Integer> setByte, int index, short value) {
      setByte.accept(index, value & 0xFF);
      setByte.accept(index + 1, value >> 8 & 0xFF);
    }

    @Override
    public void setInt(BiConsumer<Integer, Integer> setByte, int index, int value) {
      setByte.accept(index, value >> 16 & 0xFF);
      setByte.accept(index + 1, value >> 24 & 0xFF);
      setByte.accept(index + 2, value & 0xFF);
      setByte.accept(index + 3, value >> 8 & 0xFF);
    }

    @Override
    public void setLong(BiConsumer<Integer, Integer> setByte, int index, long value) {
      setByte.accept(index, (int) (value >> 48 & 0xFF));
      setByte.accept(index + 1, (int) (value >> 56 & 0xFF));
      setByte.accept(index + 2, (int) (value >> 32 & 0xFF));
      setByte.accept(index + 3, (int) (value >> 40 & 0xFF));
      setByte.accept(index + 4, (int) (value >> 16 & 0xFF));
      setByte.accept(index + 5, (int) (value >> 24 & 0xFF));
      setByte.accept(index + 6, (int) (value & 0xFF));
      setByte.accept(index + 7, (int) (value >> 8 & 0xFF));
    }

  }

}
