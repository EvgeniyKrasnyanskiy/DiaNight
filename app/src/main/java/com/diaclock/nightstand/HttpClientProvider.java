package com.diaclock.nightstand;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

/**
 * Singleton provider for sharing a single OkHttpClient instance across the entire application.
 * Optimizes connection reuse, enables TLS 1.2 compatibility for legacy Android 4.4 KitKat devices,
 * and prevents resource leaks from multiple clients.
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
            enableTls12(builder);
        }

        return builder.build();
    }

    private static void enableTls12(OkHttpClient.Builder builder) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            X509TrustManager x509TrustManager = null;
            if (trustManagers != null && trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager) {
                x509TrustManager = (X509TrustManager) trustManagers[0];
            }

            if (x509TrustManager != null) {
                TLSSocketFactory tlsFactory = new TLSSocketFactory();
                builder.sslSocketFactory(tlsFactory, x509TrustManager);

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                        .build());
                specs.add(ConnectionSpec.CLEARTEXT);

                builder.connectionSpecs(specs);
                Log.i(TAG, "TLS 1.2 successfully enabled for API " + Build.VERSION.SDK_INT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling TLS 1.2 on legacy Android: " + e.getMessage(), e);
        }
    }

    private static class TLSSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        public TLSSocketFactory() throws Exception {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
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
