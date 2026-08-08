package com.kevinherron.ignition.modbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.AttributeReader;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaServerNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies the configured OPC UA browse hierarchy for a {@link ModbusServerDevice}.
 *
 * <p>This fragment creates device, area, unit, and register-group folders. Variable NodeIds remain
 * address strings resolved by {@link ModbusAddressSpace}; browsing therefore does not create or
 * authorize process images. Unified mode exposes the legacy root area folders, while separate mode
 * replaces them with the configured unit folders.
 *
 * <p>The owning device starts and stops this fragment with its other OPC UA address space.
 */
public class BrowsableAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private static final List<String> AREA_NAMES =
      List.of("Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters");
  private static final List<String> REGISTER_DATA_TYPES =
      List.of("int16", "uint16", "int32", "uint32", "int64", "uint64", "float", "double");
  // Digit counts are bounded so client-supplied NodeIds can never overflow Integer.parseInt.
  private static final Pattern ENUMERATED_AREA_PATTERN =
      Pattern.compile("_(C|DI|HR|IR)(\\d{1,5})_");
  private static final Pattern UNIT_ENUMERATED_AREA_PATTERN =
      Pattern.compile("Unit(\\d{1,3})\\._(HR|IR)(\\d{1,5})_");
  private static final Pattern UNIT_AREA_FOLDER_PATTERN =
      Pattern.compile("Unit(\\d{1,3})\\.(Coils|DiscreteInputs|HoldingRegisters|InputRegisters)");
  private static final Pattern UNIT_RANGE_PATTERN = Pattern.compile("(\\d+)(?:-(\\d+))?");

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AddressSpaceFilter filter;

  private final ModbusServerDevice device;
  private final boolean separatePerUnitId;
  private final Set<Integer> configuredUnitIds;
  private final SubscriptionModel subscriptionModel;

  /**
   * Creates an unstarted browse fragment for a device.
   *
   * @param server the OPC UA server that owns the address space.
   * @param device the device whose settings define the browse hierarchy.
   */
  public BrowsableAddressSpace(OpcUaServer server, ModbusServerDevice device) {
    super(server, device);

    this.device = device;
    separatePerUnitId = device.deviceConfig.processImage().separatePerUnitId();
    configuredUnitIds =
        Set.copyOf(expandUnitIdRanges(device.deviceConfig.browsing().unitIdBrowseRanges()));

    filter =
        SimpleAddressSpaceFilter.create(
            nodeId -> {
              if (logger.isDebugEnabled()) {
                logger.debug("filtering: {}", nodeId);
              }

              if (getNodeManager().containsNode(nodeId)) {
                return true;
              } else {
                String id = nodeId.getIdentifier().toString();
                id = id.substring(device.deviceContext.getName().length() + 2);

                if (separatePerUnitId) {
                  Matcher matcher = UNIT_ENUMERATED_AREA_PATTERN.matcher(id);
                  return matcher.matches()
                      && configuredUnitIds.contains(Integer.parseInt(matcher.group(1)));
                } else {
                  return switch (id) {
                    case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" -> true;
                    default -> ENUMERATED_AREA_PATTERN.matcher(id).matches();
                  };
                }
              }
            });

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public List<ReferenceResult> browse(
      BrowseContext context, ViewDescription view, List<NodeId> nodeIds) {

    var results = new ArrayList<ReferenceResult>();

    for (NodeId nodeId : nodeIds) {
      results.add(browse(context, view, nodeId));
    }

    return results;
  }

  private ReferenceResult browse(BrowseContext context, ViewDescription view, NodeId nodeId) {
    String id = nodeId.getIdentifier().toString();
    id = id.substring(device.deviceContext.getName().length() + 2);

    if (separatePerUnitId) {
      Matcher areaMatcher = UNIT_AREA_FOLDER_PATTERN.matcher(id);
      if (areaMatcher.matches()) {
        int unitId = Integer.parseInt(areaMatcher.group(1));
        if (configuredUnitIds.contains(unitId)) {
          return browseArea(nodeId, areaMatcher.group(2), unitId);
        }
      }

      Matcher matcher = UNIT_ENUMERATED_AREA_PATTERN.matcher(id);
      if (matcher.matches()) {
        int unitId = Integer.parseInt(matcher.group(1));
        if (configuredUnitIds.contains(unitId)) {
          String area = matcher.group(2);
          int address = Integer.parseInt(matcher.group(3));

          return ReferenceResult.of(createRegisterAddressReferences(nodeId, area, address, unitId));
        }
      }

      return super.browse(context, view, List.of(nodeId)).get(0);
    }

    return switch (id) {
      case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" ->
          browseArea(nodeId, id, null);
      default -> {
        Matcher matcher = ENUMERATED_AREA_PATTERN.matcher(id);
        if (matcher.matches()) {
          String area = matcher.group(1);
          int address = Integer.parseInt(matcher.group(2));
          yield ReferenceResult.of(createRegisterAddressReferences(nodeId, area, address, null));
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Browsing super with: {}", nodeId);
          }
          yield super.browse(context, view, List.of(nodeId)).get(0);
        }
      }
    };
  }

  private ReferenceResult browseArea(NodeId nodeId, String areaName, Integer unitId) {
    String ranges =
        switch (areaName) {
          case "Coils" -> device.deviceConfig.browsing().coilBrowseRanges();
          case "DiscreteInputs" -> device.deviceConfig.browsing().discreteInputBrowseRanges();
          case "HoldingRegisters" -> device.deviceConfig.browsing().holdingRegisterBrowseRanges();
          case "InputRegisters" -> device.deviceConfig.browsing().inputRegisterBrowseRanges();
          default -> throw new IllegalArgumentException("unknown area: " + areaName);
        };

    if (ranges == null || ranges.isEmpty()) {
      return ReferenceResult.of(List.of());
    }

    String area =
        switch (areaName) {
          case "Coils" -> "C";
          case "DiscreteInputs" -> "DI";
          case "HoldingRegisters" -> "HR";
          case "InputRegisters" -> "IR";
          default -> throw new IllegalArgumentException("unknown area: " + areaName);
        };

    var references = new ArrayList<Reference>();
    for (String identifier : browseIdentifiers(area, parseRanges(ranges), unitId)) {
      references.add(
          new Reference(
              nodeId,
              NodeIds.HasComponent,
              device.deviceContext.nodeId(identifier).expanded(),
              Reference.Direction.FORWARD));
    }

    return ReferenceResult.of(references);
  }

  /**
   * Builds the child identifiers exposed beneath one Modbus area folder.
   *
   * @param area the Modbus area abbreviation: {@code C}, {@code DI}, {@code HR}, or {@code IR}.
   * @param ranges the address ranges to expose.
   * @param unitId the unit ID qualifier, or {@code null} for unified identifiers.
   * @return the variable or register-group identifiers in range order.
   * @throws IllegalArgumentException if {@code area} is not supported.
   */
  static List<String> browseIdentifiers(String area, List<Range> ranges, Integer unitId) {
    var identifiers = new ArrayList<String>();
    for (Range range : ranges) {
      for (int i = range.start; i <= range.end; i++) {
        if (area.equals("HR") || area.equals("IR")) {
          identifiers.add(
              unitId == null
                  ? "_%s%d_".formatted(area, i)
                  : registerFolderIdentifier(unitId, area, i));
        } else if (area.equals("C") || area.equals("DI")) {
          String address = "%s%d".formatted(area, i);
          identifiers.add(unitId == null ? address : variableIdentifier(unitId, address));
        } else {
          throw new IllegalArgumentException("unknown area: " + area);
        }
      }
    }

    return identifiers;
  }

  private List<Reference> createRegisterAddressReferences(
      NodeId parentNodeId, String area, int address, Integer unitId) {

    var references = new ArrayList<Reference>();

    switch (area) {
      case "HR", "IR" -> {
        for (String identifier : registerAddressIdentifiers(area, address, unitId)) {
          NodeId targetNodeId = device.deviceContext.nodeId(identifier);

          references.add(
              new Reference(
                  parentNodeId,
                  NodeIds.HasComponent,
                  targetNodeId.expanded(),
                  Reference.Direction.FORWARD));
        }
      }
      default -> {
        // intentional fall-through
      }
    }

    return references;
  }

  /**
   * Builds the typed variable identifiers exposed beneath a register-group folder.
   *
   * @param area the {@code HR} or {@code IR} area abbreviation.
   * @param address the register offset represented by the folder.
   * @param unitId the unit ID qualifier, or {@code null} for unified identifiers.
   * @return one variable identifier for each supported register data type.
   * @throws IllegalArgumentException if {@code area} is not a register area.
   */
  static List<String> registerAddressIdentifiers(String area, int address, Integer unitId) {
    if (!area.equals("HR") && !area.equals("IR")) {
      throw new IllegalArgumentException("not a register area: " + area);
    }

    var identifiers = new ArrayList<String>();
    for (String dataType : REGISTER_DATA_TYPES) {
      String registerAddress = "%s<%s>%d".formatted(area, dataType, address);
      identifiers.add(
          unitId == null ? registerAddress : variableIdentifier(unitId, registerAddress));
    }
    return identifiers;
  }

  @Override
  public List<DataValue> read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds) {

    var values = new ArrayList<DataValue>();

    for (ReadValueId readValueId : readValueIds) {
      UaServerNode node = getNodeManager().get(readValueId.getNodeId());

      if (node != null) {
        DataValue value =
            AttributeReader.readAttribute(
                context,
                node,
                readValueId.getAttributeId(),
                timestamps,
                readValueId.getIndexRange(),
                readValueId.getDataEncoding());

        values.add(value);
      } else {
        String id = readValueId.getNodeId().getIdentifier().toString();
        id = id.substring(device.deviceContext.getName().length() + 2);

        switch (id) {
          case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" -> {
            DataValue value =
                AttributeId.from(readValueId.getAttributeId())
                    .map(
                        attributeId -> {
                          Variant variant = readAttribute(readValueId.getNodeId(), attributeId);
                          return new DataValue(variant);
                        })
                    .orElseGet(() -> new DataValue(StatusCodes.Bad_AttributeIdInvalid));

            values.add(value);
          }
          default -> {
            boolean syntheticNode =
                separatePerUnitId
                    ? isConfiguredUnitRegisterFolder(id)
                    : ENUMERATED_AREA_PATTERN.matcher(id).matches();

            if (syntheticNode) {
              DataValue value =
                  AttributeId.from(readValueId.getAttributeId())
                      .map(
                          attributeId -> {
                            Variant variant = readAttribute(readValueId.getNodeId(), attributeId);
                            return new DataValue(variant);
                          })
                      .orElseGet(() -> new DataValue(StatusCodes.Bad_AttributeIdInvalid));

              values.add(value);
            } else {
              values.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
            }
          }
        }
      }
    }

    return values;
  }

  private boolean isConfiguredUnitRegisterFolder(String identifier) {
    Matcher matcher = UNIT_ENUMERATED_AREA_PATTERN.matcher(identifier);
    return matcher.matches() && configuredUnitIds.contains(Integer.parseInt(matcher.group(1)));
  }

  private Variant readAttribute(NodeId nodeId, AttributeId attributeId) {
    Object o =
        switch (attributeId) {
          case NodeId -> nodeId;
          case NodeClass -> NodeClass.Object;
          case BrowseName -> device.deviceContext.qualifiedName(syntheticFolderName(nodeId));
          case DisplayName, Description -> LocalizedText.english(syntheticFolderName(nodeId));
          default -> null;
        };

    return o == null ? Variant.NULL_VALUE : new Variant(o);
  }

  private String syntheticFolderName(NodeId nodeId) {
    String id = nodeId.getIdentifier().toString();
    String addr = id.substring(device.deviceContext.getName().length() + 2);
    // Separate-mode identifiers are unit-qualified ("Unit7._HR0_"); the folder's name is only
    // the register-group segment, matching the unified-mode name ("HR0").
    int dot = addr.indexOf('.');
    if (dot >= 0) {
      addr = addr.substring(dot + 1);
    }
    return addr.replace("_", "");
  }

  @Override
  public void onDataItemsCreated(List<DataItem> items) {
    subscriptionModel.onDataItemsCreated(items);
  }

  @Override
  public void onDataItemsModified(List<DataItem> items) {
    subscriptionModel.onDataItemsModified(items);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> items) {
    subscriptionModel.onDataItemsDeleted(items);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> items) {
    subscriptionModel.onMonitoringModeChanged(items);
  }

  private void addNodes() {
    // create a folder node for our configured device
    var deviceNode =
        new UaFolderNode(
            getNodeContext(),
            device.deviceContext.nodeId(""),
            device.deviceContext.qualifiedName(
                String.format("[%s]", device.deviceContext.getName())),
            new LocalizedText(String.format("[%s]", device.deviceContext.getName())));

    // add the folder node to the server
    getNodeManager().addNode(deviceNode);

    // add a reference to the root "Devices" folder node
    deviceNode.addReference(
        new Reference(
            deviceNode.getNodeId(),
            NodeIds.Organizes,
            device.deviceContext.getRootNodeId().expanded(),
            Reference.Direction.INVERSE));

    Map<String, UaFolderNode> parentNodes = new HashMap<>();
    parentNodes.put("", deviceNode);

    for (FolderDefinition definition :
        folderDefinitions(separatePerUnitId, configuredUnitIds.stream().sorted().toList())) {
      var folderNode =
          new UaFolderNode(
              getNodeContext(),
              device.deviceContext.nodeId(definition.identifier()),
              device.deviceContext.qualifiedName(definition.browseName()),
              new LocalizedText(definition.displayName()));

      getNodeManager().addNode(folderNode);
      parentNodes.get(definition.parentIdentifier()).addOrganizes(folderNode);
      parentNodes.put(definition.identifier(), folderNode);
    }
  }

  record FolderDefinition(
      String identifier, String parentIdentifier, String browseName, String displayName) {}

  /**
   * Describes the concrete folder nodes for unified or separate browsing.
   *
   * @param separatePerUnitId whether unit folders replace the unified root area folders.
   * @param unitIds the unit IDs whose folders should be exposed.
   * @return folder definitions with unit IDs sorted and de-duplicated.
   * @throws IllegalArgumentException if a unit ID is outside 0 through 255.
   */
  static List<FolderDefinition> folderDefinitions(
      boolean separatePerUnitId, List<Integer> unitIds) {

    var definitions = new ArrayList<FolderDefinition>();

    if (separatePerUnitId) {
      for (int unitId : new TreeSet<>(unitIds)) {
        String unitIdentifier = unitFolderIdentifier(unitId);
        definitions.add(new FolderDefinition(unitIdentifier, "", unitIdentifier, "Unit " + unitId));

        for (String areaName : AREA_NAMES) {
          definitions.add(
              new FolderDefinition(
                  areaFolderIdentifier(unitId, areaName), unitIdentifier, areaName, areaName));
        }
      }
    } else {
      for (String areaName : AREA_NAMES) {
        definitions.add(new FolderDefinition(areaName, "", areaName, areaName));
      }
    }

    return List.copyOf(definitions);
  }

  static String unitFolderIdentifier(int unitId) {
    validateUnitId(unitId);
    return "Unit" + unitId;
  }

  static String areaFolderIdentifier(int unitId, String areaName) {
    validateUnitId(unitId);
    if (!AREA_NAMES.contains(areaName)) {
      throw new IllegalArgumentException("unknown area: " + areaName);
    }
    return "%s.%s".formatted(unitFolderIdentifier(unitId), areaName);
  }

  static String registerFolderIdentifier(int unitId, String area, int address) {
    validateUnitId(unitId);
    if (!area.equals("HR") && !area.equals("IR")) {
      throw new IllegalArgumentException("not a register area: " + area);
    }
    return "%s._%s%d_".formatted(unitFolderIdentifier(unitId), area, address);
  }

  static String variableIdentifier(int unitId, String address) {
    validateUnitId(unitId);
    return "%d.%s".formatted(unitId, address);
  }

  private static void validateUnitId(int unitId) {
    ProcessImageManager.validateUnitId(unitId);
  }

  /**
   * Expands a unit ID browse expression into an ascending, de-duplicated list.
   *
   * @param ranges comma-separated unit IDs and inclusive ranges, or an empty string.
   * @return the selected unit IDs, or an empty list for an empty expression.
   * @throws IllegalArgumentException if the expression is null, malformed, reversed, or outside 0
   *     through 255.
   */
  static List<Integer> expandUnitIdRanges(String ranges) {
    if (ranges == null) {
      throw new IllegalArgumentException("unit ID browse ranges must not be null");
    }
    if (ranges.isEmpty()) {
      return List.of();
    }

    var unitIds = new TreeSet<Integer>();
    for (String range : ranges.split(",", -1)) {
      Matcher matcher = UNIT_RANGE_PATTERN.matcher(range);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("invalid unit ID browse range: " + range);
      }

      int start = parseUnitId(matcher.group(1));
      int end = matcher.group(2) == null ? start : parseUnitId(matcher.group(2));
      if (end < start) {
        throw new IllegalArgumentException("unit ID browse range is reversed: " + range);
      }

      for (int unitId = start; unitId <= end; unitId++) {
        unitIds.add(unitId);
      }
    }

    return List.copyOf(unitIds);
  }

  private static int parseUnitId(String value) {
    final int unitId;
    try {
      unitId = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("invalid unit ID: " + value, e);
    }

    validateUnitId(unitId);
    return unitId;
  }

  record Range(int start, int end) {}

  static List<Range> parseRanges(String ranges) {
    var rangeList = new ArrayList<Range>();
    for (String range : ranges.split(",")) {
      String[] parts = range.split("-");
      if (parts.length == 1) {
        int start = Math.min(Integer.parseInt(parts[0]), 65535);
        rangeList.add(new Range(start, start));
      } else if (parts.length == 2) {
        int start = Math.min(Integer.parseInt(parts[0]), 65535);
        int end = Math.min(Integer.parseInt(parts[1]), 65535);
        rangeList.add(new Range(start, end));
      }
    }
    return rangeList;
  }
}
