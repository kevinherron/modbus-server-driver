# Allowed IP Addresses (per-device connection whitelist)

This plan delivers a per-device, connection-time IPv4 whitelist for Modbus TCP while preserving
the existing unrestricted behavior by default.

Based on repository inspection and the IPv4-only scope decision recorded on 2026-08-06.

**Plan ID:** `allowed-ip-addresses-per-device-connection-whitelist`
**Status:** Ready
**Parent manifest:** None
**Grounded against:** `ignition-8.3` at `cee68db`, inspected 2026-08-06
**Re-ground before:** None

## Scope

This plan covers the per-device configuration, parsing, connection-time enforcement,
documentation, and verification of an IPv4 address whitelist for Modbus TCP. IPv6 whitelist
entries, DNS names, regex rules, deny rules, TLS, and read-only access control are out of scope.

## Current State

Every Modbus TCP server this module creates accepts a connection from any remote that can reach
its bind address and port. `ModbusServerDeviceConfig.Connectivity` defaults `bindAddress` to
`0.0.0.0` (`ModbusServerDeviceConfig.java:20`), so out of the box a device listens on every
interface with no restriction on who may read or write the process image. The module has no
security settings at all today — no allow-list, no TLS, no read-only mode.

## Desired End State

This adds a per-device **Allowed IP Addresses** whitelist, enforced at connection time. It
defaults to `*` (no address restriction) so existing and migrated devices behave exactly as they
do now. Explicit whitelist entries support IPv4 only; when an IPv4 list is configured, a non-IPv4
remote cannot match and is rejected.

Per-device is the right scope: each Ignition device owns its own `ModbusTcpServer`, bind address,
port, and `ProcessImage`, all constructed in `ModbusServerDevice.startup()`
(`ModbusServerDevice.java:55-65`).

## Syntax

A comma-separated list. Each entry is one of:

| Form | Example | Meaning |
|---|---|---|
| Any | `*` | no address restriction (the default) |
| IPv4 literal | `192.168.1.50` | that one address |
| IPv4 CIDR | `192.168.1.0/24` | the block |
| IPv4 trailing wildcard | `192.168.1.*`, `192.168.*`, `10.*` | `/24`, `/16`, `/8` |
| IPv4 short range | `192.168.1.10-20` | `.10` through `.20` inclusive |
| IPv4 full range | `192.168.1.10-192.168.2.5` | inclusive interval |

A remote is allowed if **any** entry matches. Order is irrelevant.

Deliberately **not** supported:

- **Regex.** In regex `192.168.1.*` matches `192.168.199.7` — the `.` is any-char and `.*` is any
  run. The most natural thing a user would type silently whitelists the wrong subnet, with no
  error. A whitelist that over-matches without complaining is worse than no whitelist.
- **Hostnames / DNS names.** Would require either reverse DNS on the remote (blocking work on the
  Netty event loop, and trivially spoofable via PTR records) or a forward lookup at startup that
  goes stale.
- **IPv6 addresses.** This whitelist intentionally supports IPv4 only. Use `*` to preserve the
  unrestricted behavior of an existing server; any explicit IPv4 list rejects non-IPv4 peers.
- **Deny / negation entries** (`!192.168.1.99`). Adds precedence semantics to learn. Can be added
  later without breaking any existing config, since no current entry form starts with `!`.

### Grammar decisions

- **Whitespace is stripped around each entry**, so `192.168.1.0/24, 10.0.5.7` is valid; whitespace
  *inside* an entry is rejected. This is a deliberate divergence from `expandUnitIdRanges` on
  `feature/per-unit-process-images`, which rejects whitespace: unit-ID lists are short tokens, but
  IP lists are long enough that users space them out by reflex, and a leading space in a
  single-line text input is invisible — an error the user cannot see in the field is a UX failure,
  not a strictness win. Stripping relaxes nothing, since whitespace carries no meaning in any
  accepted form and the stripped entry still runs the full grammar.
- **Validate each entry against a character whitelist** of `[0-9./*-]` before parsing.
  This is not redundant with stripping: `Character.isWhitespace(' ')` is `false`, so a
  non-breaking space pasted from a web page or a runbook survives both `strip()` and `trim()`
  (verified), as does a zero-width space. One whitelist rule kills NBSP, ZWSP, smart quotes, and
  interior spaces together, with a clear "unsupported character" message instead of a baffling one.
- **Wildcards must be trailing and octet-aligned.** `192.168.*.5` is rejected. `192.168.*` and
  `192.168.*.*` both mean `/16` — accepting both costs nothing and avoids a pointless ticket.
- **`*.*.*.*` is rejected**, pointing the user at `*` or `0.0.0.0/0`. It reads as "everything" but
  duplicates two clearer supported forms and invites confusion with shell-style glob syntax.
- **Cap the entry count** (~256) and add `@MaxLength(4096)` to the field. Each entry compiles to a
  single range so there is no expansion blowup; this is purely to stop a pasted 50k-line list.
- **A dotted quad must have all four octets** unless the last part is `*`. `192.168.1` is
  rejected.
- **Leading zeros are rejected** (`192.168.001.1`). Historic `inet_aton` treats a leading zero as
  octal, so `010` reads as 8 to some parsers and 10 to others — never silently pick one. Guava's
  `InetAddresses.forString` already rejects these (verified), along with short quads like
  `192.168.1`, over-long forms like `1.2.3.4.5`, and the empty string.
- **CIDR host bits must be zero.** `192.168.1.5/24` is rejected rather than silently masked to
  `192.168.1.0/24`; the user either meant a host or a network and should say which. `/0`, `/32`
  are legal.
- **Prefix length must be `0-32`.** IPv6 prefix lengths and address forms are not accepted.
- **Ranges must not be reversed.** The lower endpoint must be less than or equal to the upper
  endpoint.
- **Empty entries are rejected**, so `a,,b` and a trailing comma fail validation rather than being
  silently dropped (the existing `parseRanges` drops them, which hides typos).
- **Duplicate and overlapping entries are allowed** — harmless in an allow-only list.

### Blank and missing values

- `security` object absent from stored JSON, or `allowedIpAddresses` JSON-null → coerced to `*` by
  the record's compact constructor. This is the upgrade path: devices migrated from 8.1 have no
  column for this field (`ModbusServerModuleHook.java:48-67` maps only the legacy columns), so
  allow-all is the only behavior-preserving reading.
- Blank string reaching `validate()` → rejected with a field message, so a user cannot save an
  empty box and believe they have locked something down.
- Blank string reaching runtime anyway → treated as `*`, with a WARN log. Defense in depth; must
  never fail closed on an upgrade.

Note this is why the compact constructor coerces **only null, never blank** — coercing blank would
make the validation rule above unreachable.

## Work Package 1: Deliver the IPv4 connection whitelist

This package delivers the configuration model, strict IPv4 parser and matcher, Netty enforcement,
save-time validation, documentation, and verification as one coherent security capability.

**ID:** `WP1`
**Depends on:** Nothing
**Done when:** Each device can enforce a validated IPv4 whitelist before processing Modbus traffic,
the default `*` remains unrestricted, configuration migration remains compatible, and all listed
tests and verification gates pass.
**Checkpoint:** None

### 1.1 Add the `Security` configuration record

**File:** `msd-gateway/src/main/java/com/kevinherron/ignition/modbus/ModbusServerDeviceConfig.java`

Add a fourth top-level component plus a compact constructor, following the precedent on
`feature/per-unit-process-images`
(`git show 24a228c:msd-gateway/src/main/java/com/kevinherron/ignition/modbus/ModbusServerDeviceConfig.java`,
which does exactly this for `processImage`):

```java
public record ModbusServerDeviceConfig(
    Connectivity connectivity, Browsing browsing, Persistence persistence, Security security) {

  /** Applies documented defaults to optional nested settings. */
  public ModbusServerDeviceConfig {
    security = security == null ? new Security(null) : security;
  }

  public record Security(
      @FormCategory("SECURITY")
          @FormField(FormFieldType.TEXT)
          @Label("Allowed IP Addresses *")
          @Required
          @Description(
              "Comma-separated list of remote addresses allowed to connect. Accepts \"*\", "
                  + "IPv4 literals, IPv4 CIDR blocks, trailing IPv4 wildcards, and IPv4 ranges.")
          @DefaultValue("*")
          String allowedIpAddresses) {

    /** Preserves allow-all for configurations saved before this setting existed. */
    public Security {
      allowedIpAddresses = allowedIpAddresses == null ? "*" : allowedIpAddresses;
    }
  }
}
```

ia-gson 2.10.1 is record-aware and invokes canonical constructors, so the compact constructors run
on decode. No `.properties` change is needed — 8.3 form labels come from the annotations, not the
legacy `ModbusServerDeviceSettings.properties` bundle. `SECURITY` is a new form category; the
existing three are plain uppercase string literals with no registration anywhere, so nothing else
needs to change.

Keep the module's convention of marking required fields with a trailing `*` in the label
(`"Bind Address *"`, `"Port *"`). Although that places `Allowed IP Addresses *` above a field whose
default is also `*`, consistency with the rest of the form is preferable to introducing a one-off
label rule.

### 1.2 Implement the dependency-free IPv4 filter

**New file:** `msd-gateway/src/main/java/com/kevinherron/ignition/modbus/security/AllowedIpAddressFilter.java`

New package `com.kevinherron.ignition.modbus.security`, matching the existing `address/` and
`util/` sub-package style.

Single internal representation: **every entry compiles to an inclusive four-byte range**
`[low, high]`, plus a match-all sentinel. Exact literal, CIDR, wildcard, and range all collapse
into it, so matching is one code path — require a four-byte remote address, then perform the
unsigned bytewise comparison `low <= addr <= high`.

```java
public final class AllowedIpAddressFilter {
  public static AllowedIpAddressFilter parse(String csv);  // throws IllegalArgumentException
  public boolean allowsAll();
  public boolean isAllowed(InetAddress address);
}
```

Parsing and matching, using `java.base` only:

- **Hand-roll IPv4.** `InetAddress.getByName` implements full `inet_aton`: `192.168.1` becomes
  `192.168.0.1`, `192.168.001.1` becomes `192.168.1.1`, and `3232235777` becomes `192.168.1.1`
  (all verified). None of those may be accepted, and the wildcard/range/leading-zero rules have to
  be hand-written regardless — so there is nothing to reuse here.
- Guava's `InetAddresses.forString` is a reasonable alternative — it is strict and DNS-free — and
  its dependency path is more solid than it first appears: `ignition-common → common:8.3.0 →
  guava:32.0.1-jre` (verified), i.e. a transitive of Ignition's *core* jar. But it is still an
  undeclared transitive, and a small purpose-built IPv4 parser needs no such argument for a
  security control.
- Match with `java.util.Arrays.compareUnsigned(byte[], byte[])` after requiring
  `address.getAddress().length == 4`, rather than a hand-written `& 0xFF` loop. A non-four-byte
  remote never matches an explicit IPv4 whitelist. The `*` sentinel bypasses matching entirely to
  preserve the existing unrestricted behavior.
- Error messages name the offending token, following the `expandUnitIdRanges` convention
  (`"invalid allowed IP address entry: " + entry`).

### 1.3 Add the Netty connection filter adapter

**New file:** `msd-gateway/src/main/java/com/kevinherron/ignition/modbus/security/AllowedIpAddressHandler.java`

Subclass Netty's `AbstractRemoteAddressFilter<InetSocketAddress>` (netty-handler, provided):
override `accept(ctx, remoteAddress)` to delegate to the filter, and `channelRejected(ctx,
remoteAddress)` to log. Roughly 20 lines; all the logic worth testing lives in
`AllowedIpAddressFilter`.

Verified mechanics that make this correct:

- `AbstractRemoteAddressFilter.handleNewChannel` calls `ctx.pipeline().remove(this)` before
  deciding, then either accepts or — when `channelRejected` returns null — calls `ctx.close()`
  (`AbstractRemoteAddressFilter.java:55-87`). So it is one-shot, stateless, and safe to mark
  `@Sharable` and reuse a single instance for the device.
- Adding it with `addFirst` from inside `initChannel` is safe. `ChannelInitializer.handlerAdded`
  runs `initChannel` once the channel is registered but *before* `channelRegistered`/`channelActive`
  are fired into the pipeline (`ChannelInitializer.java:106-118`), so a late-added handler still
  receives them; and the fallback `channelRegistered` path deliberately re-fires from the pipeline
  HEAD rather than from `ctx` for exactly this reason (`ChannelInitializer.java:75-89`).
- The rejection therefore happens before the first inbound read reaches `ModbusTcpCodec`, so no
  Modbus PDU from a blocked remote is ever decoded, let alone applied to the process image.
- `NettyTcpServerTransport` adds every accepted channel to its internal `clientChannels` list
  before the customizer runs; a rejected channel is removed again on `channelInactive`. Harmless.

**The handler must be annotated `@ChannelHandler.Sharable`.** `AbstractRemoteAddressFilter` extends
`ChannelInboundHandlerAdapter`, which is not sharable, and `DefaultChannelPipeline.checkMultiplicity`
sets `added = true` on first add and **never resets it** — not even in `handlerRemoved`
(`DefaultChannelPipeline.java:544-554`, verified). Because the filter removes itself from the
pipeline, a single reused instance would throw `ChannelPipelineException` on the *second* client,
not the first. Either annotate it (it is stateless once the ruleset is compiled) or construct one
per channel. A one-connection smoke test passes either way, so **the integration test must open two
connections.**

**Do not use `io.netty.handler.ipfilter.IpSubnetFilterRule`.** It covers only literal subnet rules,
not the accepted wildcard and range grammar, and its string constructor resolves through
`InetAddress.getByName`, so a typo can trigger a blocking DNS lookup on the thread validating the
form. Keeping parsing in `AllowedIpAddressFilter` gives validation and runtime one strict,
DNS-free implementation.

Log rejections at DEBUG individually, plus a per-device counter emitting at most one INFO per
minute (`"rejected N connections during the interval; most recent from x.x.x.x"`). Port 502 is
scanned continuously, so one WARN per rejection would flood the gateway log. Deliberately keep no
`Map` keyed by remote address: under a spoofed-source scan it would be a memory-exhaustion vector.
Keep device status unchanged; `ModbusServerDevice.getStatus()`
(`ModbusServerDevice.java:49-51`) continues to report `"Listening"` or `"Error"`, while rejection
activity remains in the bounded logs.

### 1.4 Enforce the filter during device startup

**File:** `msd-gateway/src/main/java/com/kevinherron/ignition/modbus/ModbusServerDevice.java`

In `startup()` (`ModbusServerDevice.java:55-63`), parse **before** constructing the transport, then
install the handler:

```java
AllowedIpAddressFilter ipFilter;
try {
  ipFilter = AllowedIpAddressFilter.parse(deviceConfig.security().allowedIpAddresses());
} catch (IllegalArgumentException e) {
  status = "Error: invalid allowed IP addresses";
  logger.error("Invalid allowed IP addresses; not binding Modbus server", e);
  return;                       // fail closed — do not bind
}
// Configure the transport as before, then install the filter when it is restrictive.
if (!ipFilter.allowsAll()) {
  cfg.pipelineCustomizer = pipeline -> pipeline.addFirst("ipFilter", handler);
}
```

**Fail closed on an unparseable value.** `validate()` runs on save, so a value that throws at
startup means a hand-edited resource, a gwbk from a downgrade, or a bug. Falling back to allow-all
there would turn a config error into a silently open port; refusing to bind is the only defensible
default for a security control, and it matches the existing `status = "Error"` path
(`ModbusServerDevice.java:86-91`). Note this is distinct from the blank case, which is a deliberate
allow-all.

Skip installing the handler entirely when `ipFilter.allowsAll()`, so the default configuration
produces a byte-for-byte identical pipeline to today — the safest possible upgrade story, and zero
per-connection cost for the common case.

Config changes restart the device, and `NettyTcpServerTransport.unbind()` closes every tracked
channel, so a tightened whitelist takes effect immediately. Worth documenting that this happens
because the server rebinds, not because the filter re-evaluates open connections — the whitelist is
checked once, at connect time. Do not try to make it hot-swappable; the all-or-nothing restart is
safer and matches every other setting.

### 1.5 Validate the whitelist with the runtime parser

**File:** `msd-gateway/src/main/java/com/kevinherron/ignition/modbus/ModbusServerDeviceExtensionPoint.java`

Extend `validate()` (`ModbusServerDeviceExtensionPoint.java:33-76`). Delegate to the same
`parse()` the runtime uses so validation and enforcement cannot drift — the convention stated
explicitly in `24a228c`'s `validateUnitIdBrowseRanges`:

```java
String allowed = settings.security().allowedIpAddresses();
errors.requireNotBlank("security.allowedIpAddresses", allowed);
if (allowed != null && !allowed.isBlank()) {
  try {
    AllowedIpAddressFilter.parse(allowed);
  } catch (IllegalArgumentException e) {
    errors.addFieldMessage("security.allowedIpAddresses", e.getMessage());
  }
}
```

`ValidationErrors.Builder.requireNotBlank(String, String)` exists on the 8.3 API (verified) and is
unused elsewhere in this module; it produces the standard blank-field message for the dotted path.

### 1.6 Document connection security

**File:** `README.md`

Add a `## Connection Security` section. Per the convention set by `24a228c`'s README diff, refer
to the setting by its **JSON config path** (`security.allowedIpAddresses`), not its UI label, and
document the default and each accepted entry form with examples.

Four things the section must say, because each is a likely misreading:

- **It restricts Modbus only.** The same `ProcessImage` (`ModbusServerDevice.java:24`) is readable
  and writable over OPC UA, which this setting does not touch — that surface is secured centrally
  by Ignition (endpoints, user auth, certificate trust). This is the single most likely
  misunderstanding: someone locks one of two doors and believes the data is closed off.
- **Loopback is `127.0.0.1`.** IPv6 loopback and all other IPv6 address forms are outside this
  whitelist's supported syntax; use an IPv4 connection when an explicit list is configured.
- **Behind NAT, a Docker bridge, or a reverse proxy every client shares one source address**, so
  the whitelist cannot distinguish them.
- **It complements `bindAddress`, it does not replace it.** With the default `0.0.0.0` the socket
  listens on all IPv4 interfaces and the whitelist decides which IPv4 peers may connect. If an
  existing device intentionally uses a non-IPv4 bind address, leave the whitelist at `*`; explicit
  whitelist entries are IPv4-only and non-IPv4 peers cannot match them.

### Tests

New `msd-gateway/src/test/java/com/kevinherron/ignition/modbus/security/AllowedIpAddressFilterTest.java`,
in the repo's style — JUnit 6.1.0 Jupiter only (no AssertJ/Mockito), plain `Assertions.*`,
`@Nested` groups, `@ParameterizedTest(name = "{0}")` with `@MethodSource`/`@ValueSource`, and a
*why* comment above each test rather than `@DisplayName`:

- Every accepted entry form parses; every rejected form throws `IllegalArgumentException`, covering
  each grammar decision listed above (non-trailing wildcard, leading zeros, host bits set, reversed
  range, IPv6 address forms, empty entry, short dotted quad).
- Match matrices: in-range / boundary / just-outside for CIDR, wildcard, and both range forms.
- `*`, null, and blank all preserve unrestricted behavior.
- A non-four-byte remote does not match an explicit IPv4 list.
- Mirror the README's examples verbatim in a parameterized test, the way
  `ModbusAddressParserTest` does with `parsesEveryValidReadmeAddressExample`.

New `ModbusServerDeviceConfigTest.java` — model it on `git show
24a228c:msd-gateway/src/test/java/com/kevinherron/ignition/modbus/ModbusServerDeviceConfigTest.java`,
which is the canonical shape (`@Nested JsonCompatibility`, a `roundTrip` helper over
`com.inductiveautomation.ignition.common.gson.Gson`):

- JSON with no `security` object decodes to `*` — the migration-safety guarantee.
- JSON with `"security": {}` decodes to `*`.
- Round-trip preserves an explicit list.

### Verification

Run the end-to-end gates:

```
mvn -B package          # compiles + checkstyle (validate phase) + unit tests
mvn -Pintegration verify   # requires Docker; runs ModbusToOpcUaIT / ArrayModbusToOpcUaIT
```

The integration tests restore an 8.1-era `ignition.gwbk` that has no column for this field, so the
migrated device must come up with `*` and keep accepting Modbus connections. **Those existing ITs
passing unchanged is the regression test** for "the default preserves current behavior".

Add one IT that **opens two connections in sequence** against a device with a non-`*` list. This is
not redundant with the unit tests: it is the only thing that catches a missing `@Sharable`, which
fails on the second client and not the first. It should also confirm that a run of rejected
connections leaves the device able to serve a subsequent allowed one, since
`NettyTcpServerTransport` adds every accepted channel to its internal list *before* the customizer
runs and relies on `channelInactive` to remove it. Setting a non-default value means regenerating
the `.gwbk` or driving the gateway config API at runtime; note also that the testcontainers client
connects from the Docker network, so the list must whitelist the container subnet, not the host.

Manual check: configure a device with `security.allowedIpAddresses` set to an address other than
the test machine's, confirm the connection is refused and the rejection appears at DEBUG or in the
rate-limited INFO summary, then widen it to `*` and confirm the connection succeeds.

### Implementation Notes

*None yet.*

## Notes

- **No new dependencies.** Netty 4.1.121 (incl. `netty-handler`) and Guava 32.0.1-jre are already
  `provided` via the Ignition SDK. `maven-enforcer-plugin` enforces `dependencyConvergence`, so
  keeping the dependency set unchanged matters. Any Netty use must stay `provided`-scope —
  `msd-gateway/pom.xml:44-52` deliberately excludes `io.netty:*` from `modbus-tcp` so Ignition's
  Netty is used.
- **This is TCP-only.** The module has no serial transport. If `modbus-serial` is ever added,
  `ModbusRtuRequestContext` carries no socket address, so this field would have to be hidden for
  that transport.
- Merging `feature/per-unit-process-images` will conflict trivially here: both add a top-level
  record component and a compact constructor to `ModbusServerDeviceConfig`.
- **This is the module's first `io.netty` import** — `grep -rn "io\.netty" msd-gateway/src` is
  currently empty. It compiles only because the SDK supplies Netty transitively.
- **Out of scope, but noticed while reading:** `validate()` does not check `bindAddress` at all
  today, so a typo there fails at `startup()` with status `"Error"` rather than at save time.
  `ValidationUtils.checkValidInetAddress` exists in the SDK and is unused. Say the word and
  I'll fix it alongside this; otherwise I'll leave it.
- Checkstyle is Google style with a 100-column limit and Javadoc checks at warning severity; format
  with google-java-format.
