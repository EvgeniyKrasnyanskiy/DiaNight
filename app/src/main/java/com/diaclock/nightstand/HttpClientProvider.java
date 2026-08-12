package com.diaclock.nightstand;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

/**
 * Singleton provider for sharing a single OkHttpClient instance across the entire application.
 * Optimizes connection reuse, enables TLS 1.2 and permissive SSL trust for legacy Android 4.4 KitKat devices,
 * preventing certificate trust anchor failures on GitHub API.
 */
public final class HttpClientProvider {

    private static final String TAG = "HttpClientProvider";
    private static final OkHttpClient sharedClient = createClient();

    private HttpClientProvider() {
        // Prevent instantiation
    }

    /**
     * Returns the shared OkHttpClient instance.
     *
     * @return Single OkHttpClient instance
     */
    public static OkHttpClient getClient() {
        return sharedClient;
    }

    private static OkHttpClient createClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS);

        if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT <= 21) {
            enableTls12AndLegacyTrust(builder);
        }

        return builder.build();
    }

    private static void enableTls12AndLegacyTrust(OkHttpClient.Builder builder) {
        try {
            X509TrustManager permissiveTrustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            };

            TLSSocketFactory tlsFactory = new TLSSocketFactory(permissiveTrustManager);
            builder.sslSocketFactory(tlsFactory, permissiveTrustManager);

            List<ConnectionSpec> specs = new ArrayList<>();
            specs.add(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                    .build());
            specs.add(ConnectionSpec.CLEARTEXT);

            builder.connectionSpecs(specs);
            builder.hostnameVerifier((hostname, session) -> true);

            Log.i(TAG, "TLS 1.2 & Permissive SSL Trust successfully configured for legacy API " + Build.VERSION.SDK_INT);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring TLS 1.2 on legacy Android: " + e.getMessage(), e);
        }
    }

    private static class TLSSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        public TLSSocketFactory(TrustManager trustManager) throws Exception {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, new TrustManager[]{trustManager}, null);
            delegate = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            return enableTLSOnSocket(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
        }

        private Socket enableTLSOnSocket(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.1", "TLSv1.2"});
            }
            return socket;
        }
    }
}
