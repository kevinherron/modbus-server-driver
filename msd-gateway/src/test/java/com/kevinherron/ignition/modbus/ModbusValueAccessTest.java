/*
 * Copyright (c) 2026 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.server.ProcessImage;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModbusValueAccessTest {

  @Nested
  class Read {

    // The four Modbus areas are independent stores; selecting the wrong one can return a
    // plausible value from the same offset and hide the routing error.
    @Test
    void readsFromTheAreaSelectedByTheAddress() throws Exception {
      ProcessImage processImage = new ProcessImage();
      processImage.with(
          tx -> {
            tx.writeCoils(values -> values.put(10, true));
            tx.writeDiscreteInputs(values -> values.put(10, false));
            tx.writeHoldingRegisters(values -> values.put(10, new byte[] {0, 11}));
            tx.writeInputRegisters(values -> values.put(10, new byte[] {0, 22}));
          });

      assertEquals(true, read(processImage, "C10", null).getValue());
      assertEquals(false, read(processImage, "DI10", null).getValue());
      assertEquals((short) 11, read(processImage, "HR<int16>10", null).getValue());
      assertEquals((short) 22, read(processImage, "IR<int16>10", null).getValue());
    }

    // Sparse process images are normal; absent entries must have deterministic Modbus zero
    // semantics instead of leaking nulls into OPC UA values.
    @Test
    void absentProcessImageEntriesReadAsZeroValues() throws Exception {
      ProcessImage processImage = new ProcessImage();

      assertEquals(false, read(processImage, "C0", null).getValue());
      assertEquals(false, read(processImage, "DI0", null).getValue());
      assertEquals(0, read(processImage, "HR<int32>0", null).getValue());
      assertEquals(ushort(0), read(processImage, "IR<uint16>0", null).getValue());
    }

    // A first-dimension slice must advance by both the row width and the registers per element;
    // otherwise ranged reads of multi-register values start at the wrong Modbus offset.
    @Test
    void rangedMatrixReadAccountsForRegistersPerElement() throws Exception {
      ProcessImage processImage = new ProcessImage();
      putInt32Values(processImage, 100, new int[] {10, 11, 20, 21, 30, 31});

      Matrix value =
          assertInstanceOf(
              Matrix.class, read(processImage, "HR<int32[3][2]>100", "1,0:1").getValue());

      assertArrayEquals(new int[] {1, 2}, value.getDimensions());
      assertArrayEquals(new Integer[] {20, 21}, (Integer[]) value.getElements());
    }
  }

  @Nested
  class Write {

    // Each write must mutate only the process-image area named by its address; the same offset may
    // legitimately hold unrelated values in all four areas.
    @Test
    void writesOnlyToTheAreaSelectedByTheAddress() throws Exception {
      ProcessImage processImage = new ProcessImage();

      write(processImage, "C10", true, null);
      write(processImage, "DI10", false, null);
      write(processImage, "HR<int16>10", (short) 11, null);
      write(processImage, "IR<int16>10", (short) 22, null);

      processImage.with(
          tx -> {
            assertEquals(Map.of(10, true), tx.readCoils(Map::copyOf));
            assertEquals(Map.of(10, false), tx.readDiscreteInputs(Map::copyOf));
            assertArrayEquals(
                new byte[] {0, 11}, tx.readHoldingRegisters(registers -> registers.get(10)));
            assertArrayEquals(
                new byte[] {0, 22}, tx.readInputRegisters(registers -> registers.get(10)));
          });
    }

    // High-bit writes exercise the signed narrowing and unsigned wrappers used to encode every
    // supported integer width without losing the other bits in the register value.
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.kevinherron.ignition.modbus.ModbusValueAccessTest#highBitWriteArguments")
    void settingHighBitPreservesNumericWidth(
        String dataType,
        String address,
        String underlyingAddress,
        Object initialValue,
        Object expectedValue)
        throws Exception {
      ProcessImage processImage = new ProcessImage();
      write(processImage, underlyingAddress, initialValue, null);

      write(processImage, address, true, null);

      assertEquals(expectedValue, read(processImage, underlyingAddress, null).getValue());
    }

    // A bit address is a read-modify-write view of its underlying registers; clearing one bit must
    // not disturb any of the other 31 bits.
    @Test
    void clearingBitPreservesOtherBits() throws Exception {
      ProcessImage processImage = new ProcessImage();
      write(processImage, "HR<uint32>50", uint(0xFFFF_FFFFL), null);

      write(processImage, "HR<uint32>50.20", false, null);

      assertEquals(uint(0xFFEF_FFFFL), read(processImage, "HR<uint32>50", null).getValue());
    }

    // A selected matrix row spans its width times the registers per element; omitting either factor
    // writes the update to the wrong Modbus offsets.
    @Test
    void rangedMatrixWriteAccountsForRegistersPerElement() throws Exception {
      ProcessImage processImage = new ProcessImage();
      putInt32Values(processImage, 100, new int[] {10, 11, 20, 21, 30, 31});

      write(
          processImage,
          "HR<int32[3][2]>100",
          new Matrix(new Integer[] {200, 201}, new int[] {1, 2}),
          "1,0:1");

      assertArrayEquals(
          new Integer[] {10, 11, 200, 201, 30, 31},
          (Integer[]) read(processImage, "HR<int32[6]>100", null).getValue());
    }

    // Invalid values must be rejected before the read-modify-write path mutates the underlying
    // registers, or a bad OPC UA request could corrupt an otherwise valid process image.
    @Test
    void invalidBitWriteLeavesRegistersUnchanged() throws Exception {
      ProcessImage processImage = new ProcessImage();
      write(processImage, "HR<uint32>50", uint(0x1234_5678L), null);

      UaRuntimeException exception =
          assertThrows(
              UaRuntimeException.class,
              () -> write(processImage, "HR<uint32>50.20", "not a Boolean", null));

      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          UaException.extract(exception).orElseThrow().getStatusCode().getValue());
      assertEquals(uint(0x1234_5678L), read(processImage, "HR<uint32>50", null).getValue());
    }
  }

  private static Stream<Arguments> highBitWriteArguments() {
    return Stream.of(
        Arguments.of("Int16", "HR<int16>0.15", "HR<int16>0", (short) 1, (short) 0x8001),
        Arguments.of("UInt16", "HR<uint16>10.15", "HR<uint16>10", ushort(1), ushort(0x8001)),
        Arguments.of("Int32", "HR<int32>20.31", "HR<int32>20", 1, 0x8000_0001),
        Arguments.of("UInt32", "HR<uint32>30.31", "HR<uint32>30", uint(1), uint(0x8000_0001L)),
        Arguments.of("Int64", "HR<int64>40.63", "HR<int64>40", 1L, Long.MIN_VALUE | 1L),
        Arguments.of(
            "UInt64", "HR<uint64>50.63", "HR<uint64>50", ulong(1), ulong(Long.MIN_VALUE | 1L)));
  }

  private static Variant read(ProcessImage processImage, String address, String indexRange)
      throws UaException {
    ModbusAddress parsedAddress = parse(address);
    try {
      return processImage.get(
          tx -> {
            try {
              return ModbusValueAccess.readValueAttribute(tx, parsedAddress, indexRange);
            } catch (UaException e) {
              throw new UaRuntimeException(e);
            }
          });
    } catch (UaRuntimeException e) {
      throw UaException.extract(e).orElseThrow();
    }
  }

  private static void write(
      ProcessImage processImage, String address, Object value, String indexRange) {
    ModbusAddress parsedAddress = parse(address);
    processImage.with(
        tx ->
            ModbusValueAccess.writeValueAttribute(
                tx, parsedAddress, new Variant(value), indexRange));
  }

  private static ModbusAddress parse(String address) {
    try {
      return ModbusAddressParser.parse(address);
    } catch (Exception e) {
      throw new AssertionError("invalid test address: " + address, e);
    }
  }

  private static void putInt32Values(ProcessImage processImage, int offset, int[] values) {
    processImage.with(
        tx ->
            tx.writeHoldingRegisters(
                registers -> {
                  for (int i = 0; i < values.length; i++) {
                    int value = values[i];
                    registers.put(
                        offset + i * 2, new byte[] {(byte) (value >>> 24), (byte) (value >>> 16)});
                    registers.put(
                        offset + i * 2 + 1, new byte[] {(byte) (value >>> 8), (byte) value});
                  }
                }));
  }
}
