package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.digitalpetri.modbus.server.ProcessImage.Transaction;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ArrayAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ScalarAddress;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import com.kevinherron.ignition.modbus.util.ModbusByteUtil;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.sdk.core.NumericRange;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.util.ArrayUtil;

/**
 * Reads and writes OPC UA values backed by entries in a Modbus process image.
 *
 * <p>This class is the value-access boundary between {@link ModbusAddressSpace} and a process-image
 * {@link Transaction}. Callers own the transaction scope; this class applies the address's data
 * type and array dimensions, interprets OPC UA index ranges, and reads or mutates entries through
 * the supplied transaction.
 */
final class ModbusValueAccess {

  /** Shared default for absent register entries; read-only, never mutated. */
  private static final byte[] EMPTY_REGISTER = new byte[2];

  private static final Pattern NUMERIC_RANGE_PATTERN =
      Pattern.compile("[0-9]+(?::[0-9]+)?(?:,[0-9]+(?::[0-9]+)?)*");

  private ModbusValueAccess() {}

  /**
   * Reads the value identified by {@code address} from an existing process-image transaction.
   *
   * <p>An absent or empty {@code indexRange} returns the complete value. A non-empty range returns
   * only the selected array elements or, for String values, the selected characters. Arrays with
   * more than one dimension are returned as a {@link Matrix}.
   *
   * @param tx the transaction that supplies the process-image snapshot.
   * @param address the Modbus address and value representation to read.
   * @param indexRange the OPC UA numeric range to apply, or {@code null} for the complete value.
   * @return the value represented as an OPC UA {@link Variant}.
   * @throws UaException if the range is invalid, selects no data, or the stored value cannot be
   *     decoded.
   */
  static Variant readValueAttribute(Transaction tx, ModbusAddress address, String indexRange)
      throws UaException {

    NumericRange range = null;
    if (indexRange != null && !indexRange.isEmpty()) {
      range = parseNumericRange(indexRange);
      // OPC UA Part 4 defines IndexRange on scalar String values as sub-string selection.
      if (address instanceof ScalarAddress
          && !(address.getDataType() instanceof ModbusDataType.String)) {
        throw new UaException(StatusCodes.Bad_IndexRangeNoData);
      }
      if (address instanceof ArrayAddress array) {
        // Read only the slices selected by the range's first dimension instead of decoding
        // the whole array; the remaining dimensions are applied to the sub-array below.
        NumericRange.Bounds bounds = range.getBounds()[0];
        if (bounds.getHigh() >= array.getDimensions()[0]) {
          throw new UaException(StatusCodes.Bad_IndexRangeNoData);
        }
        address = subArrayAddress(array, bounds);
        range = rebaseFirstDimension(indexRange, bounds);
      }
    }

    ModbusAddress readAddress = address;

    Variant fullValue =
        switch (readAddress.getArea()) {
          case COILS -> readBooleanValue(tx, readAddress, false);
          case DISCRETE_INPUTS -> readBooleanValue(tx, readAddress, true);
          case HOLDING_REGISTERS -> {
            byte[] bs = readHoldingRegisters(tx, readAddress);

            yield new Variant(ModbusByteUtil.getValueForBytes(bs, readAddress));
          }
          case INPUT_REGISTERS -> {
            byte[] bs = readInputRegisters(tx, readAddress);

            yield new Variant(ModbusByteUtil.getValueForBytes(bs, readAddress));
          }
        };

    if (range != null) {
      return new Variant(readValueAtRange(fullValue.getValue(), range));
    }
    return fullValue;
  }

  /** Narrow {@code array} to the slices of its first dimension selected by {@code bounds}. */
  private static ArrayAddress subArrayAddress(ArrayAddress array, NumericRange.Bounds bounds) {
    int[] dimensions = array.getDimensions().clone();
    dimensions[0] = bounds.getHigh() - bounds.getLow() + 1;

    int offsetShift = bounds.getLow() * firstDimensionSliceSize(array) * entriesPerElement(array);

    return new ArrayAddress(
        array.getUnitId().orElse(null),
        array.getArea(),
        array.getOffset() + offsetShift,
        array.getDataType(),
        array.getDataTypeModifiers(),
        dimensions);
  }

  /** Rewrite {@code indexRange} so its first dimension is relative to the sub-array read. */
  private static NumericRange rebaseFirstDimension(String indexRange, NumericRange.Bounds bounds)
      throws UaException {

    String first =
        bounds.getLow() == bounds.getHigh()
            ? "0"
            : "0:%d".formatted(bounds.getHigh() - bounds.getLow());
    int comma = indexRange.indexOf(',');
    return parseNumericRange(comma < 0 ? first : first + indexRange.substring(comma));
  }

  /** Number of elements in one slice of the array's first dimension. */
  private static int firstDimensionSliceSize(ArrayAddress array) {
    int[] dimensions = array.getDimensions();
    int sliceSize = 1;
    for (int i = 1; i < dimensions.length; i++) {
      sliceSize *= dimensions[i];
    }
    return sliceSize;
  }

  /** Coils and discrete inputs store one entry per element; register areas store registers. */
  private static int entriesPerElement(ModbusAddress address) {
    return switch (address.getArea()) {
      case COILS, DISCRETE_INPUTS -> 1;
      case HOLDING_REGISTERS, INPUT_REGISTERS -> address.getDataType().getRegisterCount();
    };
  }

  private static Variant readBooleanValue(
      Transaction tx, ModbusAddress address, boolean discreteInputs) {

    if (address instanceof ArrayAddress array) {
      boolean[] values =
          discreteInputs
              ? tx.readDiscreteInputs(map -> readBooleans(map, array))
              : tx.readCoils(map -> readBooleans(map, array));

      return new Variant(shapeBooleanArray(array, values));
    } else if (address instanceof ScalarAddress scalar) {
      boolean value =
          discreteInputs
              ? tx.readDiscreteInputs(map -> map.getOrDefault(scalar.getOffset(), false))
              : tx.readCoils(map -> map.getOrDefault(scalar.getOffset(), false));

      return new Variant(value);
    } else {
      throw new IllegalArgumentException("address: " + address);
    }
  }

  /**
   * Returns the portion of an OPC UA value selected by a numeric range.
   *
   * @param value the scalar String, array, or matrix value to read from.
   * @param range the range whose dimensions apply to {@code value}.
   * @return the selected value, using a {@link Matrix} when its rank is greater than one.
   * @throws UaException if the range's rank does not match the value or the range selects no data.
   */
  static Object readValueAtRange(Object value, NumericRange range) throws UaException {
    if (value instanceof Matrix matrix) {
      value = matrix.nestedArrayValue();
    }

    validateRangeDimensionCount(value, range);

    Object valueAtRange = NumericRange.readFromValueAtRange(value, range);
    if (ArrayUtil.getValueRank(valueAtRange) > 1) {
      valueAtRange = new Matrix(valueAtRange);
    }

    return valueAtRange;
  }

  /**
   * Reads the Boolean elements spanned by an array address.
   *
   * @param booleans the process-image entries keyed by Modbus offset.
   * @param address the array extent to read.
   * @return the flattened values in Modbus offset order; absent entries are {@code false}.
   */
  static boolean[] readBooleans(Map<Integer, Boolean> booleans, ArrayAddress address) {
    int totalElements = address.getElementCount();

    boolean[] values = new boolean[totalElements];

    for (int elementIndex = 0; elementIndex < totalElements; elementIndex++) {
      int elementOffset = address.getOffset() + elementIndex;
      values[elementIndex] = booleans.getOrDefault(elementOffset, false);
    }

    return values;
  }

  /**
   * Shapes flattened Boolean elements for the OPC UA value described by an array address.
   *
   * @param address the array dimensions to apply.
   * @param values the Boolean elements in Modbus offset order.
   * @return a boxed array for a one-dimensional address, or a {@link Matrix} for a higher rank.
   */
  static Object shapeBooleanArray(ArrayAddress address, boolean[] values) {
    Boolean[] boxedValues = new Boolean[values.length];
    for (int i = 0; i < values.length; i++) {
      boxedValues[i] = values[i];
    }

    int[] dimensions = address.getDimensions();

    if (dimensions.length == 1) {
      return boxedValues;
    } else {
      return new Matrix(boxedValues, dimensions, OpcUaDataType.Boolean);
    }
  }

  private static byte[] readHoldingRegisters(Transaction tx, ModbusAddress address) {
    return tx.readHoldingRegisters(registers -> readRegisters(registers, address));
  }

  private static byte[] readInputRegisters(Transaction tx, ModbusAddress address) {
    return tx.readInputRegisters(registers -> readRegisters(registers, address));
  }

  /**
   * Reads the register bytes spanned by a scalar or array address.
   *
   * @param registers the process-image entries keyed by Modbus register offset.
   * @param address the address whose complete register extent should be read.
   * @return the concatenated register bytes in ascending offset order; absent registers contribute
   *     two zero bytes.
   */
  static byte[] readRegisters(Map<Integer, byte[]> registers, ModbusAddress address) {
    int elementCount =
        address instanceof ArrayAddress arrayAddress ? arrayAddress.getElementCount() : 1;
    int registerCount = address.getDataType().getRegisterCount();

    var value = new byte[elementCount * registerCount * 2];

    for (int i = 0; i < value.length / 2; i++) {
      byte[] bs = registers.getOrDefault(address.getOffset() + i, EMPTY_REGISTER);
      value[i * 2] = bs[0];
      value[i * 2 + 1] = bs[1];
    }

    return value;
  }

  /**
   * Writes an OPC UA value through an existing process-image transaction.
   *
   * <p>An absent or empty {@code indexRange} replaces the complete value. A non-empty range merges
   * the selected array elements or String characters into the current value so unselected OPC UA
   * value components are preserved.
   *
   * @param tx the transaction through which process-image entries are mutated.
   * @param address the Modbus address and value representation to write.
   * @param variant the OPC UA value to encode.
   * @param indexRange the OPC UA numeric range to update, or {@code null} for the complete value.
   * @throws UaRuntimeException if the range is invalid, the value does not match the selected shape
   *     or data type, or the value cannot be encoded.
   */
  static void writeValueAttribute(
      Transaction tx, ModbusAddress address, Variant variant, String indexRange) {

    switch (address.getArea()) {
      case COILS ->
          tx.writeCoils(coilMap -> writeBooleanValue(coilMap, address, variant, indexRange));
      case DISCRETE_INPUTS ->
          tx.writeDiscreteInputs(
              discreteInputMap ->
                  writeBooleanValue(discreteInputMap, address, variant, indexRange));
      case HOLDING_REGISTERS ->
          tx.writeHoldingRegisters(
              holdingRegisterMap ->
                  writeRegisterValue(holdingRegisterMap, address, variant, indexRange));
      case INPUT_REGISTERS ->
          tx.writeInputRegisters(
              inputRegisterMap ->
                  writeRegisterValue(inputRegisterMap, address, variant, indexRange));
    }
  }

  /**
   * Resolve the full value to write, merging {@code variant} into the current value when an index
   * range is present.
   */
  private static Variant resolveWriteValue(
      ModbusAddress address, Variant variant, NumericRange range, CurrentValueReader currentValue)
      throws UaException {

    if (range == null) {
      return variant;
    }

    // Arrays support element ranges; scalar String values support sub-string ranges.
    boolean rangeSupported =
        address instanceof ArrayAddress || address.getDataType() instanceof ModbusDataType.String;
    if (!rangeSupported) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }

    return new Variant(writeValueAtRange(currentValue.read(address), variant.getValue(), range));
  }

  @FunctionalInterface
  private interface CurrentValueReader {
    Object read(ModbusAddress address) throws UaException;
  }

  private static void writeBooleanValue(
      Map<Integer, Boolean> booleanMap, ModbusAddress address, Variant variant, String indexRange) {

    try {
      NumericRange range =
          indexRange == null || indexRange.isEmpty() ? null : parseNumericRange(indexRange);

      Variant fullVariant =
          resolveWriteValue(
              address,
              variant,
              range,
              addr -> {
                // Only ArrayAddress reaches here: scalar coils are Bool, so
                // resolveWriteValue rejects their index-range writes.
                ArrayAddress array = (ArrayAddress) addr;
                // Copy the extent out of the transaction-scoped map first: its element
                // lookups are O(map size).
                Map<Integer, Boolean> current =
                    copyRange(booleanMap, array.getOffset(), array.getElementCount());
                return shapeBooleanArray(array, readBooleans(current, array));
              });

      if (address instanceof ArrayAddress array) {
        int firstElement = 0;
        int elementLimit = array.getElementCount();
        if (range != null) {
          // A ranged write must leave elements outside the selected first-dimension
          // slices untouched.
          int sliceSize = firstDimensionSliceSize(array);
          NumericRange.Bounds bounds = range.getBounds()[0];
          firstElement = bounds.getLow() * sliceSize;
          elementLimit = Math.min((bounds.getHigh() + 1) * sliceSize, elementLimit);
        }
        writeBooleanArray(booleanMap, array, fullVariant, firstElement, elementLimit);
      } else if (address instanceof ScalarAddress scalar) {
        if (fullVariant.getValue() instanceof Boolean b) {
          booleanMap.put(scalar.getOffset(), b);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else {
        throw new IllegalArgumentException("address: " + address);
      }
    } catch (UaException e) {
      throw new UaRuntimeException(e);
    }
  }

  private static void writeRegisterValue(
      Map<Integer, byte[]> registerMap, ModbusAddress address, Variant variant, String indexRange) {

    try {
      NumericRange range =
          indexRange == null || indexRange.isEmpty() ? null : parseNumericRange(indexRange);

      Variant fullVariant =
          resolveWriteValue(
              address,
              variant,
              range,
              addr -> {
                // Copy the extent out of the transaction-scoped map first: its element
                // lookups are O(map size).
                Map<Integer, byte[]> current =
                    copyRange(registerMap, addr.getOffset(), registerExtent(addr));
                return ModbusByteUtil.getValueForBytes(readRegisters(current, addr), addr);
              });

      if (range != null
          && address.getDataType() instanceof ModbusDataType.String
          && isSubStringRange(address, range)) {
        validateMergedStringByteCapacity(address, fullVariant.getValue(), range);
      }

      if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
        writeBitToRegister(registerMap, address, fullVariant, dataType);
      } else {
        byte[] registers = getRegisterWriteBytes(address, fullVariant);
        int firstRegister = 0;
        int registerLimit = registers.length / 2;
        if (range != null && address instanceof ArrayAddress array) {
          // A ranged write must leave registers outside the selected first-dimension
          // slices untouched: the merge round-trip is lossy for String and Bool elements.
          int registersPerSlice =
              firstDimensionSliceSize(array) * array.getDataType().getRegisterCount();
          NumericRange.Bounds bounds = range.getBounds()[0];
          firstRegister = bounds.getLow() * registersPerSlice;
          registerLimit = Math.min((bounds.getHigh() + 1) * registersPerSlice, registerLimit);
        }
        for (int i = firstRegister; i < registerLimit; i++) {
          byte[] value = new byte[] {registers[i * 2], registers[i * 2 + 1]};
          registerMap.put(address.getOffset() + i, value);
        }
      }
    } catch (UaException e) {
      throw new UaRuntimeException(e);
    }
  }

  /** Total number of registers spanned by {@code address}. */
  private static int registerExtent(ModbusAddress address) {
    int elementCount =
        address instanceof ArrayAddress arrayAddress ? arrayAddress.getElementCount() : 1;
    return elementCount * address.getDataType().getRegisterCount();
  }

  /** Copy the entries in {@code [offset, offset + count)} out of {@code map}. */
  private static <V> Map<Integer, V> copyRange(Map<Integer, V> map, int offset, int count) {
    var copy = new HashMap<Integer, V>();
    for (Map.Entry<Integer, V> entry : map.entrySet()) {
      int key = entry.getKey();
      if (key >= offset && key - offset < count) {
        copy.put(key, entry.getValue());
      }
    }
    return copy;
  }

  /** An extra final range dimension selects characters within a String element. */
  private static boolean isSubStringRange(ModbusAddress address, NumericRange range) {
    int rank = address instanceof ArrayAddress array ? array.getDimensions().length : 0;
    return range.getBounds().length == rank + 1;
  }

  /**
   * Reject sub-string merges whose UTF-8 encoding no longer fits the element's registers; silently
   * truncating would destroy characters outside the selected range.
   */
  private static void validateMergedStringByteCapacity(
      ModbusAddress address, Object merged, NumericRange range) throws UaException {

    int capacity = address.getDataType().getRegisterCount() * 2;

    Object elements = merged instanceof Matrix matrix ? matrix.getElements() : merged;
    if (elements instanceof String string) {
      if (string.getBytes(StandardCharsets.UTF_8).length > capacity) {
        throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
      }
      return;
    }

    int first = 0;
    int limit = Array.getLength(elements);
    if (address instanceof ArrayAddress array) {
      // Only elements in the selected first-dimension slices are written back.
      int sliceSize = firstDimensionSliceSize(array);
      NumericRange.Bounds bounds = range.getBounds()[0];
      first = bounds.getLow() * sliceSize;
      limit = Math.min((bounds.getHigh() + 1) * sliceSize, limit);
    }
    for (int i = first; i < limit; i++) {
      if (Array.get(elements, i) instanceof String string
          && string.getBytes(StandardCharsets.UTF_8).length > capacity) {
        throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
      }
    }
  }

  /**
   * Merges an update into the portion of an OPC UA value selected by a numeric range.
   *
   * @param currentValue the complete scalar String, array, or matrix value to update.
   * @param updateValue the replacement value whose shape must match the selected range.
   * @param range the range identifying the portion to replace.
   * @return the merged value, using a {@link Matrix} when its rank is greater than one.
   * @throws UaException if either value is absent, the range selects no data, or the update's type
   *     or shape is incompatible with the selected range.
   */
  static Object writeValueAtRange(Object currentValue, Object updateValue, NumericRange range)
      throws UaException {

    if (currentValue == null) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }
    if (updateValue == null) {
      throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
    }

    if (currentValue instanceof Matrix matrix) {
      currentValue = matrix.nestedArrayValue();
    }
    if (updateValue instanceof Matrix matrix) {
      if (matrix.isNull()) {
        throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
      }
      updateValue = matrix.nestedArrayValue();
    }

    validateRangeDimensionCount(currentValue, range);

    int[] currentDimensions = ArrayUtil.getDimensions(currentValue);
    NumericRange.Bounds[] bounds = range.getBounds();
    // Bounds past the end of the current value select no data; check before the shape
    // comparisons so the client sees Bad_IndexRangeNoData rather than a shape mismatch.
    for (int i = 0; i < currentDimensions.length; i++) {
      if (bounds[i].getHigh() >= currentDimensions[i]) {
        throw new UaException(StatusCodes.Bad_IndexRangeNoData);
      }
    }

    if (ArrayUtil.getBoxedType(currentValue) != ArrayUtil.getBoxedType(updateValue)) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }

    int[] updateDimensions = ArrayUtil.getDimensions(updateValue);
    // For String values an extra final range dimension selects characters within an
    // element, so the update value has one fewer dimension than the range has bounds.
    boolean subStringRange =
        hasStringLeaf(currentValue) && bounds.length == currentDimensions.length + 1;
    int expectedUpdateRank = subStringRange ? bounds.length - 1 : bounds.length;
    if (updateDimensions.length != expectedUpdateRank) {
      throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
    }
    for (int i = 0; i < updateDimensions.length; i++) {
      long expectedLength = (long) bounds[i].getHigh() - bounds[i].getLow() + 1;
      if (updateDimensions[i] != expectedLength) {
        throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
      }
    }
    if (subStringRange) {
      NumericRange.Bounds stringBounds = bounds[bounds.length - 1];
      long expectedLength = (long) stringBounds.getHigh() - stringBounds.getLow() + 1;
      validateStringLengths(updateValue, expectedLength);
    }

    Object valueAtRange = NumericRange.writeToValueAtRange(currentValue, updateValue, range);
    if (ArrayUtil.getValueRank(valueAtRange) > 1) {
      valueAtRange = new Matrix(valueAtRange);
    }

    return valueAtRange;
  }

  /**
   * Parses the OPC UA numeric-range syntax accepted by this address space.
   *
   * @param indexRange the non-empty range text to parse.
   * @return the parsed numeric range.
   * @throws UaException if the text is not a well-formed numeric range.
   */
  static NumericRange parseNumericRange(String indexRange) throws UaException {
    if (!NUMERIC_RANGE_PATTERN.matcher(indexRange).matches()) {
      throw new UaException(StatusCodes.Bad_IndexRangeInvalid);
    }
    return NumericRange.parse(indexRange);
  }

  private static void validateRangeDimensionCount(Object value, NumericRange range)
      throws UaException {

    int valueRank = ArrayUtil.getDimensions(value).length;
    int rangeDimensions = range.getBounds().length;
    boolean valid =
        hasStringLeaf(value)
            ? rangeDimensions > 0
                && (rangeDimensions == valueRank || rangeDimensions == valueRank + 1)
            : rangeDimensions == valueRank;

    if (!valid) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }
  }

  private static void validateStringLengths(Object value, long expectedLength) throws UaException {
    if (value == null) {
      throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
    }
    if (value instanceof String string) {
      if (string.length() != expectedLength) {
        throw new UaException(StatusCodes.Bad_IndexRangeDataMismatch);
      }
      return;
    }

    for (int i = 0; i < Array.getLength(value); i++) {
      validateStringLengths(Array.get(value, i), expectedLength);
    }
  }

  private static boolean hasStringLeaf(Object value) {
    Class<?> type = value.getClass();
    while (type.isArray()) {
      type = type.getComponentType();
    }
    return type == String.class;
  }

  /**
   * Encodes an OPC UA value into the complete register extent described by an address.
   *
   * @param address the address whose data type, modifiers, and dimensions control encoding.
   * @param variant the value to encode.
   * @return the encoded bytes in Modbus register order.
   * @throws UaException if the value does not match the address's data type or dimensions.
   */
  static byte[] getRegisterWriteBytes(ModbusAddress address, Variant variant) throws UaException {
    return ModbusByteUtil.getBytesForValue(variant.getValue(), address);
  }

  /**
   * Replaces the complete Boolean array represented by an address.
   *
   * <p>The value is validated before the map is mutated. One-dimensional addresses accept a boxed
   * {@code Boolean[]} or primitive {@code boolean[]}; higher-rank addresses require a {@link
   * Matrix} with matching dimensions.
   *
   * @param booleanMap the process-image entries to mutate.
   * @param array the destination offset and dimensions.
   * @param variant the complete array value to write.
   * @throws UaException if the value has the wrong type or shape, or contains a null element.
   */
  static void writeBooleanArray(
      Map<Integer, Boolean> booleanMap, ArrayAddress array, Variant variant) throws UaException {

    writeBooleanArray(booleanMap, array, variant, 0, array.getElementCount());
  }

  private static void writeBooleanArray(
      Map<Integer, Boolean> booleanMap,
      ArrayAddress array,
      Variant variant,
      int firstElement,
      int elementLimit)
      throws UaException {

    Object value = variant.getValue();

    if (array.getDimensions().length > 1) {
      if (!(value instanceof Matrix matrix)
          || matrix.isNull()
          || !Arrays.equals(matrix.getDimensions(), array.getDimensions())) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
      value = matrix.getElements();
    }

    Boolean[] booleans = boxedBooleanArray(value, array.getElementCount());

    for (int i = firstElement; i < elementLimit; i++) {
      booleanMap.put(array.getOffset() + i, booleans[i]);
    }
  }

  /**
   * Accepts boxed or primitive boolean arrays ({@link Matrix} supports both backings) and rejects
   * null elements, which would poison the process image and NPE on later reads.
   */
  private static Boolean[] boxedBooleanArray(Object value, int expectedLength) throws UaException {
    if (value instanceof boolean[] primitive) {
      if (primitive.length != expectedLength) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
      Boolean[] boxed = new Boolean[primitive.length];
      for (int i = 0; i < primitive.length; i++) {
        boxed[i] = primitive[i];
      }
      return boxed;
    }

    if (!(value instanceof Boolean[] boxed) || boxed.length != expectedLength) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }
    for (Boolean b : boxed) {
      if (b == null) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    }
    return boxed;
  }

  private static void writeBitToRegister(
      Map<Integer, byte[]> registerMap,
      ModbusAddress address,
      Variant variant,
      ModbusDataType.Bit dataType)
      throws UaException {

    int bitIndex = dataType.bit();
    ModbusDataType underlyingType = dataType.underlyingType();

    var bytes = new byte[underlyingType.getRegisterCount() * 2];

    for (int i = 0; i < bytes.length / 2; i++) {
      byte[] value = registerMap.getOrDefault(address.getOffset() + i, EMPTY_REGISTER);
      bytes[i * 2] = value[0];
      bytes[i * 2 + 1] = value[1];
    }

    Object underlyingValue =
        ModbusByteUtil.getScalarValueForBytes(
            bytes, underlyingType, address.getDataTypeModifiers());

    if (underlyingValue instanceof Number n) {
      long mask = 1L << bitIndex;
      long v = n.longValue();
      if (variant.getValue() instanceof Boolean b) {
        if (b) {
          v |= mask;
        } else {
          v &= ~mask;
        }
        byte[] newBytes =
            ModbusByteUtil.getBytesForScalarValue(
                castToUnderlying(v, underlyingType),
                underlyingType,
                address.getDataTypeModifiers());

        for (int i = 0; i < newBytes.length / 2; i++) {
          byte[] value = new byte[] {newBytes[i * 2], newBytes[i * 2 + 1]};
          registerMap.put(address.getOffset() + i, value);
        }
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InternalError);
    }
  }

  private static Number castToUnderlying(long value, ModbusDataType dataType) {
    if (dataType instanceof ModbusDataType.Int16) {
      return (short) value;
    } else if (dataType instanceof ModbusDataType.Int32) {
      return (int) value;
    } else if (dataType instanceof ModbusDataType.Int64) {
      return value;
    } else if (dataType instanceof ModbusDataType.UInt16) {
      return ushort((int) value);
    } else if (dataType instanceof ModbusDataType.UInt32) {
      return uint(value);
    } else if (dataType instanceof ModbusDataType.UInt64) {
      return ulong(value);
    } else {
      throw new IllegalArgumentException("value=" + value + ", dataType=" + dataType);
    }
  }
}
