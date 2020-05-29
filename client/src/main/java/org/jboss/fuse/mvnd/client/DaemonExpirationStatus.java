package org.jboss.fuse.mvnd.client;

/**
 * Expiration status for daemon expiration check results.
 * Note that order here is important, higher ordinal statuses
 * take precedent over lower ordinal statuses when aggregating
 * results.
 */
public enum DaemonExpirationStatus {
    DO_NOT_EXPIRE,
    QUIET_EXPIRE,
    GRACEFUL_EXPIRE,
    IMMEDIATE_EXPIRE;
}