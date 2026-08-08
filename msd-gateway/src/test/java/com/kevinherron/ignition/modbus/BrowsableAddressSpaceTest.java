package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kevinherron.ignition.modbus.BrowsableAddressSpace.FolderDefinition;
import com.kevinherron.ignition.modbus.BrowsableAddressSpace.Range;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrowsableAddressSpaceTest {

  @Nested
  class UnitIdRangeExpansion {

    // Stable ordering and de-duplication keep the browse tree deterministic when users enter
    // overlapping or out-of-order expressions.
    @Test
    void expandsUnitIdRangesInAscendingOrderWithoutDuplicates() {
      assertEquals(
          List.of(0, 2, 10, 11, 12, 255),
          BrowsableAddressSpace.expandUnitIdRanges("10-12,2,11,0,255"));
    }

    @Test
    void emptyUnitIdRangesProducesNoUnits() {
      assertEquals(List.of(), BrowsableAddressSpace.expandUnitIdRanges(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "256", "2-1", "1,,2", "1, 2", "one", "1-2-3", " ", "2147483648"})
    void invalidUnitIdRangesFailExpansion(String ranges) {
      assertThrows(
          IllegalArgumentException.class,
          () -> BrowsableAddressSpace.expandUnitIdRanges(ranges),
          () -> "invalid range accepted: " + ranges);
    }

    @Test
    void nullUnitIdRangesFailExpansion() {
      assertThrows(
          IllegalArgumentException.class, () -> BrowsableAddressSpace.expandUnitIdRanges(null));
    }
  }

  @Nested
  class FolderHierarchy {

    // Unified mode is the compatibility default, so configured unit IDs must not alter its
    // historical root folders or their identifiers.
    @Test
    void unifiedFolderDefinitionsRemainUnqualifiedAndAtTheRoot() {
      assertEquals(
          List.of(
              folder("Coils", "", "Coils", "Coils"),
              folder("DiscreteInputs", "", "DiscreteInputs", "DiscreteInputs"),
              folder("HoldingRegisters", "", "HoldingRegisters", "HoldingRegisters"),
              folder("InputRegisters", "", "InputRegisters", "InputRegisters")),
          BrowsableAddressSpace.folderDefinitions(false, List.of(7)));
    }

    // Browse ranges control discovery only; the generated hierarchy must include exactly the
    // configured, normalized units without creating duplicate folders.
    @Test
    void separateFolderDefinitionsContainOnlySortedConfiguredUnits() {
      List<FolderDefinition> definitions =
          BrowsableAddressSpace.folderDefinitions(true, List.of(2, 0, 2));

      assertEquals(
          List.of(
              folder("Unit0", "", "Unit0", "Unit 0"),
              folder("Unit0.Coils", "Unit0", "Coils", "Coils"),
              folder("Unit0.DiscreteInputs", "Unit0", "DiscreteInputs", "DiscreteInputs"),
              folder("Unit0.HoldingRegisters", "Unit0", "HoldingRegisters", "HoldingRegisters"),
              folder("Unit0.InputRegisters", "Unit0", "InputRegisters", "InputRegisters"),
              folder("Unit2", "", "Unit2", "Unit 2"),
              folder("Unit2.Coils", "Unit2", "Coils", "Coils"),
              folder("Unit2.DiscreteInputs", "Unit2", "DiscreteInputs", "DiscreteInputs"),
              folder("Unit2.HoldingRegisters", "Unit2", "HoldingRegisters", "HoldingRegisters"),
              folder("Unit2.InputRegisters", "Unit2", "InputRegisters", "InputRegisters")),
          definitions);
    }

    @Test
    void separateFolderDefinitionsAreEmptyWhenNoUnitsAreConfigured() {
      assertEquals(List.of(), BrowsableAddressSpace.folderDefinitions(true, List.of()));
    }
  }

  @Nested
  class BrowseIdentifiers {

    // Existing OPC UA clients rely on these unqualified NodeIds in unified mode.
    @Test
    void unifiedAreaBrowseIdentifiersRemainUnqualified() {
      List<Range> ranges = List.of(new Range(0, 1));

      assertEquals(List.of("C0", "C1"), BrowsableAddressSpace.browseIdentifiers("C", ranges, null));
      assertEquals(
          List.of("_HR0_", "_HR1_"), BrowsableAddressSpace.browseIdentifiers("HR", ranges, null));
    }

    // Separate-mode leaves remain directly addressable while register grouping nodes live under
    // their unit folder in the browse hierarchy.
    @Test
    void separateAreaBrowseIdentifiersUseQualifiedLeavesAndGroupingNodes() {
      List<Range> ranges = List.of(new Range(5, 6));

      assertEquals(
          List.of("7.C5", "7.C6"), BrowsableAddressSpace.browseIdentifiers("C", ranges, 7));
      assertEquals(
          List.of("Unit7._HR5_", "Unit7._HR6_"),
          BrowsableAddressSpace.browseIdentifiers("HR", ranges, 7));
    }

    @Test
    void registerGroupingNodesExposeUnitQualifiedVariableIdentifiers() {
      assertEquals(
          List.of(
              "1.HR<int16>4",
              "1.HR<uint16>4",
              "1.HR<int32>4",
              "1.HR<uint32>4",
              "1.HR<int64>4",
              "1.HR<uint64>4",
              "1.HR<float>4",
              "1.HR<double>4"),
          BrowsableAddressSpace.registerAddressIdentifiers("HR", 4, 1));
    }

    @Test
    void identifierHelpersIncludeLegalUnitIdAndAddressBoundaries() {
      assertEquals("Unit0", BrowsableAddressSpace.unitFolderIdentifier(0));
      assertEquals(
          "Unit255.InputRegisters",
          BrowsableAddressSpace.areaFolderIdentifier(255, "InputRegisters"));
      assertEquals(
          "Unit255._IR65535_", BrowsableAddressSpace.registerFolderIdentifier(255, "IR", 65535));
      assertEquals("255.DI0", BrowsableAddressSpace.variableIdentifier(255, "DI0"));
    }

    @Test
    void identifierHelpersRejectUnitIdsOutsideModbusBounds() {
      assertThrows(
          IllegalArgumentException.class, () -> BrowsableAddressSpace.unitFolderIdentifier(-1));
      assertThrows(
          IllegalArgumentException.class,
          () -> BrowsableAddressSpace.variableIdentifier(256, "C0"));
    }
  }

  private static FolderDefinition folder(
      String identifier, String parentIdentifier, String browseName, String displayName) {
    return new FolderDefinition(identifier, parentIdentifier, browseName, displayName);
  }
}
