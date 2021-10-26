package com.alibaba.dcm;

import com.alibaba.dcm.internal.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.alibaba.dcm.internal.InetAddressCacheUtilCommons.NEVER_EXPIRATION;
import static com.alibaba.dcm.internal.JavaVersionUtil.isJdkAtMost8;


/**
 * Setting dns (in fact dns cache).
 * <p>
 * Throw {@link DnsCacheManipulatorException} if operation fail for all methods.
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @see DnsCacheEntry
 * @see DnsCacheManipulatorException
 */
public class DnsCacheManipulator {
    /**
     * Set a <b>never expired</b> dns cache entry
     *
     * @param host host
     * @param ips  ips
     * @throws DnsCacheManipulatorException Operation fail
     * @see DnsCacheManipulator#setDnsCache(long, java.lang.String, java.lang.String...)
     */
    public static void setDnsCache(@Nonnull String host, @Nonnull String... ips) {
        try {
            if (isJdkAtMost8()) {
                InetAddressCacheUtilForJdk8Minus.setInetAddressCache(host, ips, NEVER_EXPIRATION);
            } else {
                InetAddressCacheUtilForJdk9Plus.setInetAddressCache(host, ips, NEVER_EXPIRATION);
            }
        } catch (Exception e) {
            final String message = String.format("Fail to setDnsCache for host %s ip %s, cause: %s",
                    host, Arrays.toString(ips), e);
            throw new DnsCacheManipulatorException(message, e);
        }
    }

    /**
     * Set a dns cache entry.
     *
     * @param expireMillis expire time in milliseconds.
     * @param host         host
     * @param ips          ips
     * @throws DnsCacheManipulatorException Operation fail
     */
    public static void setDnsCache(long expireMillis, @Nonnull String host, @Nonnull String... ips) {
        try {
            if (isJdkAtMost8()) {
                InetAddressCacheUtilForJdk8Minus.setInetAddressCache(host, ips, expireMillis);
            } else {
                //need nanos to mills
                InetAddressCacheUtilForJdk9Plus.setInetAddressCache(host, ips, expireMillis);
            }
        } catch (Exception e) {
            final String message = String.format("Fail to setDnsCache for host %s ip %s expireMillis %s, cause: %s",
                    host, Arrays.toString(ips), expireMillis, e);
            throw new DnsCacheManipulatorException(message, e);
        }
    }

    private static final Pattern COMMA_SEPARATOR = Pattern.compile("\\s*,\\s*");

    /**
     * Set dns cache entries by properties
     *
     * @param properties input properties. e.g. {@code www.example.com=42.42.42.42}, or {@code www.example.com=42.42.42.42,43.43.43.43}
     * @throws DnsCacheManipulatorException Operation fail
     */
    public static void setDnsCache(@Nonnull Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            final String host = (String) entry.getKey();
            String ipList = (String) entry.getValue();

            ipList = ipList.trim();
            if (ipList.isEmpty()) continue;

            final String[] ips = COMMA_SEPARATOR.split(ipList);
            setDnsCache(host, ips);
        }
    }

    /**
     * Load dns config from properties file {@code dns-cache.properties} on classpath, then set to dns cache.
     * <p>
     * {@code dns-cache.properties} can be reset/customized by {@code JVM -D option} {@code dcm.config.filename}
     *
     * @throws DnsCacheManipulatorException Operation fail
     * @see DnsCacheManipulator#setDnsCache(java.util.Properties)
     * @see DnsCacheManipulator#loadDnsCacheConfig(java.lang.String)
     */
    public static void loadDnsCacheConfig() {
        final String DCM_CONFIG_FILE_NAME_KEY = "dcm.config.filename";
        final String dcmConfigFileName = System.getProperty(DCM_CONFIG_FILE_NAME_KEY, "dns-cache.properties");
        loadDnsCacheConfig(dcmConfigFileName);
    }

    /**
     * Load dns config from the specified properties file on classpath, then set dns cache.
     *
     * @param propertiesFileName specified properties file name on classpath.
     * @throws DnsCacheManipulatorException Operation fail
     * @see DnsCacheManipulator#setDnsCache(java.util.Properties)
     */
    public static void loadDnsCacheConfig(@Nonnull String propertiesFileName) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFileName);
        if (inputStream == null) {
            inputStream = DnsCacheManipulator.class.getClassLoader().getResourceAsStream(propertiesFileName);
        }
        if (inputStream == null) {
            throw new DnsCacheManipulatorException("Fail to find " + propertiesFileName + " on classpath!");
        }

        try {
            Properties properties = new Properties();
            properties.load(inputStream);
            inputStream.close();
            setDnsCache(properties);
        } catch (Exception e) {
            final String message = String.format("Fail to loadDnsCacheConfig from %s, cause: %s",
                    propertiesFileName, e);
            throw new DnsCacheManipulatorException(message, e);
        }
    }

    /**
     * Get dns cache.
     *
     * @return dns cache. return {@code null} if no entry for host or dns cache is expired.
     * @throws DnsCacheManipulatorException Operation fail
     */
    @Nullable
    public static DnsCacheEntry getDnsCache(@Nonnull String host) {
        try {
            if (isJdkAtMost8()) {
                return InetAddressCacheUtilForJdk8Minus.getInetAddressCache(host);
            } else {
                return InetAddressCacheUtilForJdk9Plus.getInetAddressCache(host);
            }
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to getDnsCache, cause: " + e, e);
        }
    }

    /**
     * Get all dns cache entries.
     *
     * @return dns cache entries
     * @throws DnsCacheManipulatorException Operation fail
     * @deprecated use {@link #listDnsCache} instead.
     */
    @Deprecated
    @Nonnull
    public static List<DnsCacheEntry> getAllDnsCache() {
        return listDnsCache();
    }

    /**
     * Get all dns cache entries.
     *
     * @return dns cache entries
     * @throws DnsCacheManipulatorException Operation fail
     * @see #getWholeDnsCache()
     * @since 1.2.0
     */
    @Nonnull
    public static List<DnsCacheEntry> listDnsCache() {
        try {
            if (isJdkAtMost8()) {
                return InetAddressCacheUtilForJdk8Minus.listInetAddressCache().getCache();
            } else {
                return InetAddressCacheUtilForJdk9Plus.listInetAddressCache().getCache();
            }
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to listDnsCache, cause: " + e, e);
        }
    }

    /**
     * Get whole dns cache info.
     *
     * @return dns cache entries
     * @throws DnsCacheManipulatorException Operation fail
     * @since 1.2.0
     */
    @Nonnull
    public static DnsCache getWholeDnsCache() {
        try {
            if (isJdkAtMost8()) {
                return InetAddressCacheUtilForJdk8Minus.listInetAddressCache();
            } else {
                return InetAddressCacheUtilForJdk9Plus.listInetAddressCache();
            }
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to getWholeDnsCache, cause: " + e, e);
        }
    }

    /**
     * Remove dns cache entry, cause lookup dns server for host after.
     *
     * @param host host
     * @throws DnsCacheManipulatorException Operation fail
     * @see DnsCacheManipulator#clearDnsCache
     */
    public static void removeDnsCache(@Nonnull String host) {
        try {
            if (isJdkAtMost8()) {
                InetAddressCacheUtilForJdk8Minus.removeInetAddressCache(host);
            } else {
                InetAddressCacheUtilForJdk9Plus.removeInetAddressCache(host);
            }
        } catch (Exception e) {
            final String message = String.format("Fail to removeDnsCache for host %s, cause: %s", host, e);
            throw new DnsCacheManipulatorException(message, e);
        }
    }

    /**
     * Clear all dns cache entries, cause lookup dns server for all host after.
     *
     * @throws DnsCacheManipulatorException Operation fail
     */
    public static void clearDnsCache() {
        try {
            if (isJdkAtMost8()) {
                InetAddressCacheUtilForJdk8Minus.clearInetAddressCache();
            } else {
                InetAddressCacheUtilForJdk9Plus.clearInetAddressCache();
            }
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to clearDnsCache, cause: " + e, e);
        }
    }

    /**
     * Get JVM DNS cache policy.
     *
     * @return cache seconds.
     * <p>
     * <ul>
     * <li> {@code -1} means never expired.(In effect, all negative value)</li>
     * <li> {@code 0} never cached.</li>
     * </ul>
     * @throws DnsCacheManipulatorException Operation fail
     * @since 1.3.0
     */
    public static int getDnsCachePolicy() {
        try {
            return InetAddressCacheUtilCommons.getDnsCachePolicy();
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to getDnsCachePolicy, cause: " + e, e);
        }
    }

    /**
     * Set JVM DNS cache policy
     * <p>
     * NOTE: if Security Manage is turn on, JVM DNS cache policy set will not take effective. You can check by method {@link #getDnsCachePolicy()}.
     *
     * @param cacheSeconds set default dns cache time. Special input case:
     *                     <ul>
     *                     <li> {@code -1} means never expired.(In effect, all negative value)</li>
     *                     <li> {@code 0} never cached.</li>
     *                     </ul>
     * @throws DnsCacheManipulatorException Operation fail
     * @since 1.3.0
     */
    public static void setDnsCachePolicy(int cacheSeconds) {
        try {
            InetAddressCacheUtilCommons.setDnsCachePolicy(cacheSeconds);
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to setDnsCachePolicy, cause: " + e, e);
        }
    }

    /**
     * JVM DNS negative cache policy
     *
     * @return negative cache seconds.
     * <p>
     * <ul>
     * <li> {@code -1} means never expired.(In effect, all negative value)</li>
     * <li> {@code 0} never cached.</li>
     * </ul>
     * @throws DnsCacheManipulatorException Operation fail
     * @since 1.3.0
     */
    public static int getDnsNegativeCachePolicy() {
        try {
            return InetAddressCacheUtilCommons.getDnsNegativeCachePolicy();
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to getDnsNegativeCachePolicy, cause: " + e, e);
        }
    }

    /**
     * Set JVM DNS negative cache policy
     *
     * @param negativeCacheSeconds set default dns cache time. Special input case:
     *                             <ul>
     *                             <li> {@code -1} means never expired.(In effect, all negative value)</li>
     *                             <li> {@code 0} never cached.</li>
     *                             </ul>
     * @throws DnsCacheManipulatorException Operation fail
     * @since 1.3.0
     */
    public static void setDnsNegativeCachePolicy(int negativeCacheSeconds) {
        try {
            InetAddressCacheUtilCommons.setDnsNegativeCachePolicy(negativeCacheSeconds);
        } catch (Exception e) {
            throw new DnsCacheManipulatorException("Fail to setDnsNegativeCachePolicy, cause: " + e, e);
        }
    }

    private DnsCacheManipulator() {
    }
}
