package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.validation.SourceTrustPolicy;
import org.apache.hc.client5.http.DnsResolver;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class PinnedDnsResolver implements DnsResolver {
    private final SourceTrustPolicy trustPolicy;

    public PinnedDnsResolver(SourceTrustPolicy trustPolicy) {
        this.trustPolicy = trustPolicy;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        try {
            trustPolicy.validateResolvedAddresses(host, addresses);
        } catch (IllegalArgumentException exception) {
            UnknownHostException rejected = new UnknownHostException(exception.getMessage());
            rejected.initCause(exception);
            throw rejected;
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return host;
    }
}
