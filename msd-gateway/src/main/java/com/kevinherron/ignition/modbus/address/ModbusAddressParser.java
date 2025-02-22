package com.kevinherron.ignition.modbus.address;

import com.kevinherron.ignition.modbus.address.DataTypeModifier.ByteOrder;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.ByteOrderModifier;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.WordOrder;
import com.kevinherron.ignition.modbus.address.DataTypeModifier.WordOrderModifier;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ModbusArea;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class ModbusAddressParser {

  private static final String AREAS = "C|DI|HR|IR";
  private static final String DATA_TYPES =
      "BOOL|INT16|UINT16|INT32|UINT32|INT64|UINT64|FLOAT|DOUBLE|STRING[1-9][0-9]*";
  private static final String DATA_TYPE_MODIFIERS = "[@BE|@LE|@HL|@LH]+";
  private static final String ARRAY_DIMENSIONS = "\\[\\d+]";

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
              .formatted(
                  AREAS, DATA_TYPES, ARRAY_DIMENSIONS, DATA_TYPE_MODIFIERS, ARRAY_DIMENSIONS),
          Pattern.CASE_INSENSITIVE);

  public static ModbusAddress parse(String address) throws Exception {
    var matcher = ADDRESS_PATTERN.matcher(address);
    if (!matcher.matches()) {
      throw new Exception("invalid address: " + address);
    }

    // System.out.println("0: " + matcher.group(0));
    // System.out.println("1: " + matcher.group(1));
    // System.out.println("2: " + matcher.group(2));
    // System.out.println("3: " + matcher.group(3));
    // System.out.println("4: " + matcher.group(4));
    // System.out.println("5: " + matcher.group(5));
    // System.out.println("6: " + matcher.group(6));
    // System.out.println("7: " + matcher.group(7));
    // System.out.println("8: " + matcher.group(8));
    // System.out.println("9: " + matcher.group(9));

    Integer unitId = parseUnitId(matcher.group(2));

    ModbusArea area =
        parseArea(matcher.group(3))
            .orElseThrow(() -> new Exception("invalid area: " + matcher.group(3)));

    ModbusDataType dataType =
        parseDataType(area, matcher.group(4))
            .orElseThrow(() -> new Exception("invalid DataType: " + matcher.group(4)));

    List<Integer> dimensions =
        parseArrayDimensions(matcher.group(5))
            .orElseThrow(() -> new Exception("invalid dimensions: " + matcher.group(5)));

    Set<DataTypeModifier> dataTypeModifiers =
        parseDataTypeModifiers(matcher.group(6))
            .orElseThrow(() -> new Exception("invalid modifiers: " + matcher.group(6)));

    int offset = Integer.parseInt(matcher.group(7));

    if (matcher.group(9) != null) {
      int bit = Integer.parseInt(matcher.group(9));
      dataType = new ModbusDataType.Bit(dataType, bit);
    }

    if (dimensions.isEmpty()) {
      return new ModbusAddress.ScalarAddress(unitId, area, offset, dataType, dataTypeModifiers);
    } else {
      // TODO arrays
      throw new Exception("array address not implemented");
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

    return Optional.ofNullable(mdt);
  }

  private static Optional<List<Integer>> parseArrayDimensions(String dimensions) {
    if (dimensions == null || dimensions.isEmpty()) {
      return Optional.of(List.of());
    }
    // TODO
    return Optional.empty();
  }

  private static Optional<Set<DataTypeModifier>> parseDataTypeModifiers(String modifiers) {
    if (modifiers == null || modifiers.isEmpty()) {
      return Optional.of(Set.of());
    }

    // TODO this ignores invalid modifiers instead of returning empty

    var set = new HashSet<DataTypeModifier>();

    for (String s : modifiers.split("@")) {
      DataTypeModifier m =
          switch (s.toUpperCase()) {
            case "BE" -> new ByteOrderModifier(ByteOrder.BIG_ENDIAN);
            case "LE" -> new ByteOrderModifier(ByteOrder.LITTLE_ENDIAN);
            case "HL" -> new WordOrderModifier(WordOrder.HIGH_LOW);
            case "LH" -> new WordOrderModifier(WordOrder.LOW_HIGH);
            default -> null;
          };

      if (m != null) {
        set.add(m);
      }
    }

    return Optional.of(set);
  }
}
