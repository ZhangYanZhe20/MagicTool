package com.example.magictool.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PatternMatcher;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class MagicWifiConnector {
    private static final String TAG = "MagicWifiConnector";
    private static final String SSID_PREFIX = "Magic";
    private static final String WIFI_PASSWORD = "dt123456";
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30秒超时

    private final Context context;
    private final WifiManager wifiManager;
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private WifiConnectCallback currentCallback;
    private final Handler mainHandler;
    private boolean isConnecting = false;

    // 连接状态回调接口
    public interface WifiConnectCallback {
        void onConnecting();
        void onConnected(String ssid);
        void onConnectionFailed(String error);
        void onDisconnected();
    }

    public MagicWifiConnector(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 连接以"Magic"开头的WiFi网络
     */
    public void connectToMagicWifi(@NonNull WifiConnectCallback callback) {
        if (isConnecting) {
            callback.onConnectionFailed("正在连接中，请稍候");
            return;
        }

        if (!isWifiEnabled()) {
            callback.onConnectionFailed("WiFi未开启，请先打开WiFi");
            return;
        }

        this.currentCallback = callback;
        this.isConnecting = true;
        callback.onConnecting();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectUsingModernApi();
        } else {
            connectUsingLegacyApi();
        }

        // 设置连接超时
        mainHandler.postDelayed(this::handleConnectionTimeout, CONNECTION_TIMEOUT_MS);
    }

    /**
     * 断开当前连接
     */
    public void disconnect() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Network callback not registered");
            }
            networkCallback = null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            wifiManager.disconnect();
        }

        // 解绑网络
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.bindProcessToNetwork(null);
        }

        isConnecting = false;
        currentCallback = null;
        mainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 检查WiFi是否已启用
     */
    public boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    /**
     * Android 10+ 使用现代API连接
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectUsingModernApi() {
        // 创建WiFi网络规范，匹配以"Magic"开头的SSID
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsidPattern(new PatternMatcher(SSID_PREFIX, PatternMatcher.PATTERN_PREFIX))
                .setWpa2Passphrase(WIFI_PASSWORD)
                .build();

        // 创建网络请求
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        // 创建网络回调
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                handleConnectionSuccess(network);
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                handleConnectionError("未找到可用的Magic开头WiFi网络");
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                handleDisconnection();
            }
        };

        // 发起网络请求
        connectivityManager.requestNetwork(request, networkCallback);
    }

    /**
     * Android 9及以下 使用传统API连接
     */
    private void connectUsingLegacyApi() {
        // 先扫描WiFi网络
        wifiManager.startScan();

        // 延迟执行连接，等待扫描结果
        mainHandler.postDelayed(() -> {
            // 查找以"Magic"开头的WiFi
            String targetSsid = null;
            for (android.net.wifi.ScanResult result : wifiManager.getScanResults()) {
                if (result.SSID.startsWith(SSID_PREFIX)) {
                    targetSsid = result.SSID;
                    break;
                }
            }

            if (targetSsid == null) {
                handleConnectionError("未找到可用的Magic开头WiFi网络");
                return;
            }

            // 配置WiFi连接参数
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + targetSsid + "\"";
            config.preSharedKey = "\"" + WIFI_PASSWORD + "\"";

            // 设置WPA2加密
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);

            // 添加网络配置
            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) {
                // 如果添加失败，尝试更新现有配置
                for (WifiConfiguration existingConfig : wifiManager.getConfiguredNetworks()) {
                    if (existingConfig.SSID.equals(config.SSID)) {
                        networkId = existingConfig.networkId;
                        break;
                    }
                }
            }

            if (networkId == -1) {
                handleConnectionError("无法创建WiFi配置");
                return;
            }

            // 断开当前连接并连接到目标网络
            wifiManager.disconnect();
            boolean success = wifiManager.enableNetwork(networkId, true);
            wifiManager.reconnect();

            if (success) {
                // 等待连接成功
                waitForConnection(targetSsid);
            } else {
                handleConnectionError("连接WiFi失败");
            }
        }, 2000);
    }

    /**
     * 等待传统API连接成功
     */
    private void waitForConnection(final String targetSsid) {
        final int[] attempts = {0};
        final int maxAttempts = 15; // 15秒超时

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnecting) return;

                android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null && wifiInfo.getSSID().equals("\"" + targetSsid + "\"")
                        && wifiInfo.getNetworkId() != -1) {
                    handleConnectionSuccess(null);
                } else if (attempts[0] < maxAttempts) {
                    attempts[0]++;
                    mainHandler.postDelayed(this, 1000);
                } else {
                    handleConnectionError("连接超时");
                }
            }
        }, 1000);
    }

    /**
     * 处理连接成功
     */
    private void handleConnectionSuccess(Network network) {
        mainHandler.removeCallbacksAndMessages(null);
        isConnecting = false;

        // 绑定应用进程到这个网络（Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && network != null) {
            connectivityManager.bindProcessToNetwork(network);
        }

        // 获取连接的SSID
        String ssid = "未知";
        android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null && wifiInfo.getSSID() != null) {
            ssid = wifiInfo.getSSID().replace("\"", "");
        }

        if (currentCallback != null) {
            currentCallback.onConnected(ssid);
        }
    }

    /**
     * 处理连接错误
     */
    private void handleConnectionError(String error) {
        mainHandler.removeCallbacksAndMessages(null);
        isConnecting = false;

        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Network callback not registered");
            }
            networkCallback = null;
        }

        if (currentCallback != null) {
            currentCallback.onConnectionFailed(error);
        }
    }

    /**
     * 处理连接超时
     */
    private void handleConnectionTimeout() {
        if (isConnecting) {
            handleConnectionError("连接超时，请检查WiFi信号");
        }
    }

    /**
     * 处理断开连接
     */
    private void handleDisconnection() {
        if (currentCallback != null) {
            currentCallback.onDisconnected();
        }
    }

}
