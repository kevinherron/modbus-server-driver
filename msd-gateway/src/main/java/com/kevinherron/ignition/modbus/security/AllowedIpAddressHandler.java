package com.kevinherron.ignition.modbus.security;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces an {@link AllowedIpAddressFilter} when a Modbus TCP channel is established.
 *
 * <p>Install one instance before the Modbus protocol handlers and share it across the channels
 * owned by one device. The handler is safe to use from multiple event-loop threads. Rejected
 * channels are closed before protocol handling, each rejection is logged at DEBUG, and per-handler
 * INFO summaries are emitted at most once per minute.
 *
 * <p>Callers can omit this handler when {@link AllowedIpAddressFilter#allowsAll()} reports an
 * unrestricted policy.
 */
@ChannelHandler.Sharable
public final class AllowedIpAddressHandler extends AbstractRemoteAddressFilter<InetSocketAddress> {

  private static final long REPORT_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AllowedIpAddressFilter filter;

  private long intervalStartedNanos = System.nanoTime() - REPORT_INTERVAL_NANOS;
  private long rejectedCount;
  private String lastRejectedAddress;
  private boolean flushScheduled;

  /**
   * Creates a connection-time gate for one device's compiled policy.
   *
   * @param filter the non-null policy applied to each channel's remote address.
   */
  public AllowedIpAddressHandler(AllowedIpAddressFilter filter) {
    this.filter = filter;
  }

  @Override
  protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
    return filter.isAllowed(remoteAddress.getAddress());
  }

  @Override
  protected ChannelFuture channelRejected(
      ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {

    String address = formatAddress(remoteAddress);
    logger.debug("Rejected Modbus connection from {}", address);
    recordRejection(ctx, address);

    return null;
  }

  private synchronized void recordRejection(ChannelHandlerContext ctx, String address) {
    rejectedCount++;
    lastRejectedAddress = address;

    long now = System.nanoTime();
    long elapsed = now - intervalStartedNanos;
    if (elapsed >= REPORT_INTERVAL_NANOS) {
      logSummary();
      intervalStartedNanos = now;
    } else if (!flushScheduled) {
      // A burst that stops before the interval elapses would otherwise never be summarized.
      flushScheduled = true;
      ctx.executor()
          .schedule(this::flushRejections, REPORT_INTERVAL_NANOS - elapsed, TimeUnit.NANOSECONDS);
    }
  }

  private synchronized void flushRejections() {
    flushScheduled = false;
    if (rejectedCount > 0) {
      logSummary();
      intervalStartedNanos = System.nanoTime();
    }
  }

  private void logSummary() {
    logger.info(
        "rejected {} connections during the interval; most recent from {}",
        rejectedCount,
        lastRejectedAddress);
    rejectedCount = 0;
  }

  private static String formatAddress(InetSocketAddress remoteAddress) {
    InetAddress address = remoteAddress.getAddress();
    return address != null ? address.getHostAddress() : remoteAddress.getHostString();
  }
}
