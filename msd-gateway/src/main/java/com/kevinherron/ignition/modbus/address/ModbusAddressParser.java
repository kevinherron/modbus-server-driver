package com.kevinherron.ignition.modbus.address;

import com.kevinherron.ignition.modbus.address.DataTypeModifier.ByteOrder;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.ByteOrderModifier;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.WordOrder;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.WordOrderModifier;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ModbusArea;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class ModbusAddressParser {

  private static final long ADDRESS_SPACE_SIZE = 65536L;
  private static final String AREAS = "C|DI|HR|IR";
  private static final String DATA_TYPES =
      "BOOL|INT16|UINT16|INT32|UINT32|INT64|UINT64|FLOAT|DOUBLE|STRING[1-9][0-9]*";
  private static final String DATA_TYPE_MODIFIERS = "(?:@BE|@LE|@HL|@LH)+";
  private static final String SUBSCRIPTS = "\\[\\d+]";

  private static final Pattern SUBSCRIPT_PATTERN = Pattern.compile("\\[(\\d+)]");

  static final Pattern ADDRESS_PATTERN =
      Pattern.compile(
          """
          ((\\d+)\\.)?\
          (%s)\
          (?:<(%s)((?:%s){0,3})(?:(%s)?)>)?\
          (\\d+)\
          ((?:%s){0,3})?\
          (?:\\.(\\d+))?\
          """
              .formatted(AREAS, DATA_TYPES, SUBSCRIPTS, DATA_TYPE_MODIFIERS, SUBSCRIPTS),
          Pattern.CASE_INSENSITIVE);

  public static ModbusAddress parse(String address) throws Exception {
    var matcher = ADDRESS_PATTERN.matcher(address);
    if (!matcher.matches()) {
      throw new Exception("invalid address: " + address);
    }

    Integer unitId = parseUnitId(matcher.group(2));

    ModbusArea area =
        parseArea(matcher.group(3))
            .orElseThrow(() -> new Exception("invalid area: " + matcher.group(3)));

    ModbusDataType dataType =
        parseDataType(area, matcher.group(4))
            .orElseThrow(() -> new Exception("invalid DataType: " + matcher.group(4)));

    List<Integer> dimensions =
        parseDimensions(matcher.group(5))
            .orElseThrow(() -> new Exception("invalid dimensions: " + matcher.group(5)));

    Set<DataTypeModifier> dataTypeModifiers =
        parseDataTypeModifiers(matcher.group(6))
            .orElseThrow(() -> new Exception("invalid modifiers: " + matcher.group(6)));

    int offset = Integer.parseInt(matcher.group(7));

    List<Integer> indices =
        parseIndices(matcher.group(8))
            .orElseThrow(() -> new Exception("invalid indices: " + matcher.group(8)));

    if (matcher.group(9) != null) {
      int bit = Integer.parseInt(matcher.group(9));
      validateBit(dataType, bit);
      dataType = new ModbusDataType.Bit(dataType, bit);
    }

    if (!dimensions.isEmpty()) {
      if (indices.isEmpty() && dataType instanceof ModbusDataType.Bit) {
        throw new Exception("bit specifiers are not allowed on array addresses");
      }

      validateExtent(area, offset, calculateElementCount(dimensions), dataType);
    }

    if (!indices.isEmpty()) {
      // element within an array

      if (dimensions.isEmpty()) {
        throw new Exception("indices require array dimensions");
      }

      if (dimensions.size() != indices.size()) {
        throw new Exception(
            "number of indices (%d) doesn't match number of dimensions (%d)"
                .formatted(indices.size(), dimensions.size()));
      }

      long linearIndex = 0;
      for (int i = 0; i < indices.size(); i++) {
        int index = indices.get(i);
        int dimension = dimensions.get(i);

        if (index < 0 || index >= dimension) {
          throw new Exception("index " + index + " out of bounds for dimension " + dimension);
        }

        try {
          linearIndex = Math.addExact(Math.multiplyExact(linearIndex, dimension), index);
        } catch (ArithmeticException e) {
          throw new Exception("indexed address extent exceeds the Modbus address space", e);
        }
      }

      long calculatedOffset;
      try {
        calculatedOffset =
            Math.addExact(
                offset, Math.multiplyExact(linearIndex, entriesPerElement(area, dataType)));
      } catch (ArithmeticException e) {
        throw new Exception("indexed address extent exceeds the Modbus address space", e);
      }

      validateExtent(area, calculatedOffset, 1, dataType);

      return new ModbusAddress.ScalarAddress(
          unitId, area, (int) calculatedOffset, dataType, dataTypeModifiers);
    } else if (dimensions.isEmpty()) {
      // scalar address, no indices or dimensions

      validateExtent(area, offset, 1, dataType);

      return new ModbusAddress.ScalarAddress(unitId, area, offset, dataType, dataTypeModifiers);
    } else {
      // array address, no indices but dimensions are present

      int[] dimensionsArray = dimensions.stream().mapToInt(Integer::intValue).toArray();

      return new ModbusAddress.ArrayAddress(
          unitId, area, offset, dataType, dataTypeModifiers, dimensionsArray);
    }
  }

  private static long calculateElementCount(List<Integer> dimensions) throws Exception {
    long elementCount = 1;
    try {
      for (int dimension : dimensions) {
        elementCount = Math.multiplyExact(elementCount, dimension);
      }
      return elementCount;
    } catch (ArithmeticException e) {
      throw new Exception("array extent exceeds the Modbus address space", e);
    }
  }

  private static void validateExtent(
      ModbusArea area, long offset, long elementCount, ModbusDataType dataType) throws Exception {
    if (offset < 0 || offset >= ADDRESS_SPACE_SIZE) {
      throw new Exception("offset %d is outside the Modbus address space".formatted(offset));
    }

    long entriesPerElement = entriesPerElement(area, dataType);
    if (entriesPerElement <= 0) {
      throw new Exception("entries per element must be positive");
    }

    long extent;
    try {
      extent = Math.addExact(offset, Math.multiplyExact(elementCount, entriesPerElement));
    } catch (ArithmeticException e) {
      throw new Exception("address extent exceeds the Modbus address space", e);
    }

    if (extent > ADDRESS_SPACE_SIZE) {
      throw new Exception("address extent %d exceeds the Modbus address space".formatted(extent));
    }
  }

  private static void validateBit(ModbusDataType underlyingType, int bit) throws Exception {
    boolean integerType =
        underlyingType instanceof ModbusDataType.Int16
            || underlyingType instanceof ModbusDataType.UInt16
            || underlyingType instanceof ModbusDataType.Int32
            || underlyingType instanceof ModbusDataType.UInt32
            || underlyingType instanceof ModbusDataType.Int64
            || underlyingType instanceof ModbusDataType.UInt64;

    if (!integerType) {
      throw new Exception("bit selection requires an integer data type: " + underlyingType);
    }

    int bitWidth = underlyingType.getRegisterCount() * 16;
    if (bit < 0 || bit >= bitWidth) {
      throw new Exception(
          "bit index %d out of range for %d-bit data type".formatted(bit, bitWidth));
    }
  }

  private static int entriesPerElement(ModbusArea area, ModbusDataType dataType) {
    return switch (area) {
      case COILS, DISCRETE_INPUTS -> 1;
      case HOLDING_REGISTERS, INPUT_REGISTERS -> dataType.getRegisterCount();
    };
  }

  public static boolean isValidAddress(String address) {
    try {
      parse(address);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static @Nullable Integer parseUnitId(String unitId) throws Exception {
    if (unitId != null) {
      try {
        int i = Integer.parseInt(unitId);
        if (i < 0 || i > 255) {
          throw new Exception("invalid unitId: " + unitId);
        }
        return i;
      } catch (NumberFormatException ignored) {
        throw new Exception("invalid unitId: " + unitId);
      }
    } else {
      return null;
    }
  }

  private static Optional<ModbusArea> parseArea(String area) {
    ModbusArea a =
        switch (area.toUpperCase()) {
          case "C" -> ModbusArea.COILS;
          case "DI" -> ModbusArea.DISCRETE_INPUTS;
          case "HR" -> ModbusArea.HOLDING_REGISTERS;
          case "IR" -> ModbusArea.INPUT_REGISTERS;
          default -> null;
        };

    return Optional.ofNullable(a);
  }

  private static Optional<ModbusDataType> parseDataType(ModbusArea area, String dataType) {
    if (dataType == null || dataType.isEmpty()) {
      return switch (area) {
        case COILS, DISCRETE_INPUTS -> Optional.of(new ModbusDataType.Bool());
        case HOLDING_REGISTERS, INPUT_REGISTERS -> Optional.of(new ModbusDataType.Int16());
      };
    }

    ModbusDataType mdt =
        switch (dataType.toUpperCase()) {
          case "BOOL" -> new ModbusDataType.Bool();
          case "INT16" -> new ModbusDataType.Int16();
          case "UINT16" -> new ModbusDataType.UInt16();
          case "INT32" -> new ModbusDataType.Int32();
          case "UINT32" -> new ModbusDataType.UInt32();
          case "INT64" -> new ModbusDataType.Int64();
          case "UINT64" -> new ModbusDataType.UInt64();
          case "FLOAT" -> new ModbusDataType.Float32();
          case "DOUBLE" -> new ModbusDataType.Double64();
          default -> null;
        };

    if (mdt == null) {
      if (dataType.toUpperCase().startsWith("STRING")) {
        try {
          int length = Integer.parseInt(dataType.substring(6));
          mdt = new ModbusDataType.String(length);
        } catch (NumberFormatException ignored) {
          // ignored
        }
      }
    }

    // Coil and discrete input entries are single bits; register-only data types would
    // advertise an OPC UA DataType the boolean read/write paths can never satisfy.
    if ((area == ModbusArea.COILS || area == ModbusArea.DISCRETE_INPUTS)
        && !(mdt instanceof ModbusDataType.Bool)) {
      return Optional.empty();
    }

    return Optional.ofNullable(mdt);
  }

  private static Optional<List<Integer>> parseDimensions(String dimensions) {
    return parseSubscripts(dimensions, 1);
  }

  private static Optional<List<Integer>> parseIndices(String indices) {
    return parseSubscripts(indices, 0);
  }

  private static Optional<List<Integer>> parseSubscripts(String subscripts, int minAllowed) {
    if (subscripts == null || subscripts.isEmpty()) {
      return Optional.of(List.of());
    }

    try {
      var dimensionList = new ArrayList<Integer>();
      var matcher = SUBSCRIPT_PATTERN.matcher(subscripts);

      while (matcher.find()) {
        int dimension = Integer.parseInt(matcher.group(1));
        if (dimension < minAllowed) {
          return Optional.empty();
        }
        dimensionList.add(dimension);
      }

      return Optional.of(dimensionList);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Optional<Set<DataTypeModifier>> parseDataTypeModifiers(String modifiers) {
    if (modifiers == null || modifiers.isEmpty()) {
      return Optional.of(Set.of());
    }

    var set = new HashSet<DataTypeModifier>();

    if (!modifiers.startsWith("@")) {
      return Optional.empty();
    }

    for (String s : modifiers.substring(1).split("@", -1)) {
      DataTypeModifier m =
          switch (s.toUpperCase()) {
            case "BE" -> new ByteOrderModifier(ByteOrder.BIG_ENDIAN);
            case "LE" -> new ByteOrderModifier(ByteOrder.LITTLE_ENDIAN);
            case "HL" -> new WordOrderModifier(WordOrder.HIGH_LOW);
            case "LH" -> new WordOrderModifier(WordOrder.LOW_HIGH);
            default -> null;
          };

      if (m == null) {
        return Optional.empty();
      }

      boolean conflictsWithExisting =
          set.stream()
              .anyMatch(existing -> existing.getClass() == m.getClass() && !existing.equals(m));
      if (conflictsWithExisting) {
        return Optional.empty();
      }

      set.add(m);
    }

    return Optional.of(set);
  }
}
