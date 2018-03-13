/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyh.wifiinforeader;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.IWifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Show the current status details of Wifi related fields
 */
public class WifiStatusActivity extends Activity {

    private static final String LOG_TAG = "WifiStatusActivity";

    private Button updateButton;
    private TextView mWifiState;
    private TextView mNetworkState;
    private TextView mSupplicantState;
    private TextView mRSSI;
    private TextView mBSSID;
    private TextView mSSID;
    private TextView mHiddenSSID;
    private TextView mIPAddr;
    private TextView mMACAddr;
    private TextView mNetworkId;
    private TextView mLinkSpeed;
    private TextView mScanList;

    private TextView mPingHostname;
    private TextView mHttpClientTest;
    private Button pingTestButton;
    private TextView mScanCount;

    private int count = 0;
    private StringBuffer mScanContent = new StringBuffer();

    private String mPingHostnameResult;
    private String mHttpClientTestResult;


    private WifiManager mWifiManager;
    private IntentFilter mWifiStateFilter;

    private Handler mScanHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            mWifiManager.startScan();

            ++count;
            mScanContent.append("Time: " + System.currentTimeMillis() + " ScanCount: " + count + "\n");
            mScanCount.setText(mScanContent.toString());

            mScanHandler.sendEmptyMessageDelayed(0, 5000);
            return true;
        }
    });


    //============================
    // Activity lifecycle
    //============================

    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                Toast.makeText(WifiStatusActivity.this, "WiFi状态改变", Toast.LENGTH_SHORT).show();
                handleWifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
            } else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                Toast.makeText(WifiStatusActivity.this, "网络状态改变", Toast.LENGTH_SHORT).show();
                handleNetworkStateChanged((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO));
            } else if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                handleScanResultsAvailable();
            } else if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                Toast.makeText(WifiStatusActivity.this, "WiFi已连接", Toast.LENGTH_SHORT).show();
                /* TODO: handle supplicant connection change later */
            } else if (intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                handleSupplicantStateChanged((SupplicantState) intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE), intent.hasExtra(WifiManager.EXTRA_SUPPLICANT_ERROR), intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0));
            } else if (intent.getAction().equals(WifiManager.RSSI_CHANGED_ACTION)) {
                handleSignalChanged(intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0));
            } else if (intent.getAction().equals(WifiManager.NETWORK_IDS_CHANGED_ACTION)) {
                Toast.makeText(WifiStatusActivity.this, "配置网络改变", Toast.LENGTH_SHORT).show();
                /* TODO: handle network id change info later */
            } else {
                Log.e(LOG_TAG, "Received an unknown Wifi Intent");
            }
        }
    };

    //    private final int REQUEST_FIND_LOCATION = 0x111;
    //    private final int REQUEST_COARSE_LOCATION = 0x112;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET};
        for (String per : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, per)) {
                ActivityCompat.requestPermissions(this, permissions, 0);
                finish();
                return;
            }
        }

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        mWifiStateFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mWifiStateFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mWifiStateFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mWifiStateFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mWifiStateFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mWifiStateFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        registerReceiver(mWifiStateReceiver, mWifiStateFilter);

        setContentView(R.layout.wifi_status_test);

        updateButton = (Button) findViewById(R.id.update);
        updateButton.setOnClickListener(updateButtonHandler);

        mWifiState = (TextView) findViewById(R.id.wifi_state);
        mNetworkState = (TextView) findViewById(R.id.network_state);
        mSupplicantState = (TextView) findViewById(R.id.supplicant_state);
        mRSSI = (TextView) findViewById(R.id.rssi);
        mBSSID = (TextView) findViewById(R.id.bssid);
        mSSID = (TextView) findViewById(R.id.ssid);
        mHiddenSSID = (TextView) findViewById(R.id.hidden_ssid);
        mIPAddr = (TextView) findViewById(R.id.ipaddr);
        mMACAddr = (TextView) findViewById(R.id.macaddr);
        mNetworkId = (TextView) findViewById(R.id.networkid);
        mLinkSpeed = (TextView) findViewById(R.id.link_speed);
        mScanList = (TextView) findViewById(R.id.scan_list);

        mPingHostname = (TextView) findViewById(R.id.pingHostname);
        mHttpClientTest = (TextView) findViewById(R.id.httpClientTest);

        pingTestButton = (Button) findViewById(R.id.ping_test);
        pingTestButton.setOnClickListener(mPingButtonHandler);

        mScanCount = (TextView) findViewById(R.id.scan_count);

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mWifiStateReceiver, mWifiStateFilter);
        mScanHandler.sendEmptyMessage(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mWifiStateReceiver);
        mScanHandler.removeMessages(0);
    }

    OnClickListener mPingButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            if (mScanHandler.hasMessages(0)) {
                mScanHandler.removeMessages(0);
            }
            updatePingState();
        }
    };

    OnClickListener updateButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

            setWifiStateText(mWifiManager.getWifiState());
            mBSSID.setText(wifiInfo.getBSSID());
            mHiddenSSID.setText(String.valueOf(wifiInfo.getHiddenSSID()));
            int ipAddr = wifiInfo.getIpAddress();
            StringBuffer ipBuf = new StringBuffer();
            ipBuf.append(ipAddr & 0xff).append('.').
                    append((ipAddr >>>= 8) & 0xff).append('.').
                    append((ipAddr >>>= 8) & 0xff).append('.').
                    append((ipAddr >>>= 8) & 0xff);

            mIPAddr.setText(ipBuf);
            mLinkSpeed.setText(String.valueOf(wifiInfo.getLinkSpeed()) + " Mbps");
            mMACAddr.setText(wifiInfo.getMacAddress());
            mNetworkId.setText(String.valueOf(wifiInfo.getNetworkId()));
            mRSSI.setText(String.valueOf(wifiInfo.getRssi()));
            mSSID.setText(wifiInfo.getSSID());

            SupplicantState supplicantState = wifiInfo.getSupplicantState();
            setSupplicantStateText(supplicantState);
        }
    };

    private void setSupplicantStateText(SupplicantState supplicantState) {
        if (SupplicantState.FOUR_WAY_HANDSHAKE.equals(supplicantState)) {
            mSupplicantState.setText("FOUR WAY HANDSHAKE");
        } else if (SupplicantState.ASSOCIATED.equals(supplicantState)) {
            mSupplicantState.setText("ASSOCIATED");
        } else if (SupplicantState.ASSOCIATING.equals(supplicantState)) {
            mSupplicantState.setText("ASSOCIATING");
        } else if (SupplicantState.COMPLETED.equals(supplicantState)) {
            mSupplicantState.setText("COMPLETED");
        } else if (SupplicantState.DISCONNECTED.equals(supplicantState)) {
            mSupplicantState.setText("DISCONNECTED");
        } else if (SupplicantState.DORMANT.equals(supplicantState)) {
            mSupplicantState.setText("DORMANT");
        } else if (SupplicantState.GROUP_HANDSHAKE.equals(supplicantState)) {
            mSupplicantState.setText("GROUP HANDSHAKE");
        } else if (SupplicantState.INACTIVE.equals(supplicantState)) {
            mSupplicantState.setText("INACTIVE");
        } else if (SupplicantState.INVALID.equals(supplicantState)) {
            mSupplicantState.setText("INVALID");
        } else if (SupplicantState.SCANNING.equals(supplicantState)) {
            mSupplicantState.setText("SCANNING");
        } else if (SupplicantState.UNINITIALIZED.equals(supplicantState)) {
            mSupplicantState.setText("UNINITIALIZED");
        } else {
            mSupplicantState.setText("BAD");
            Log.e(LOG_TAG, "supplicant state is bad");
        }
    }

    private void setWifiStateText(int wifiState) {
        String wifiStateString;
        switch (wifiState) {
            case WifiManager.WIFI_STATE_DISABLING:
                wifiStateString = getString(R.string.wifi_state_disabling);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                wifiStateString = getString(R.string.wifi_state_disabled);
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                wifiStateString = getString(R.string.wifi_state_enabling);
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                wifiStateString = getString(R.string.wifi_state_enabled);
                break;
            case WifiManager.WIFI_STATE_UNKNOWN:
                wifiStateString = getString(R.string.wifi_state_unknown);
                break;
            default:
                wifiStateString = "BAD";
                Log.e(LOG_TAG, "wifi state is bad");
                break;
        }

        mWifiState.setText(wifiStateString);
    }

    private void handleSignalChanged(int rssi) {
        mRSSI.setText(String.valueOf(rssi));
    }

    private void handleWifiStateChanged(int wifiState) {
        setWifiStateText(wifiState);
    }

    private void handleScanResultsAvailable() {
        List<ScanResult> list = mWifiManager.getScanResults();

        StringBuffer scanList = new StringBuffer();
        if (list != null) {
            for (int i = list.size() - 1; i >= 0; i--) {
                final ScanResult scanResult = list.get(i);

                if (scanResult == null) {
                    continue;
                }

                if (TextUtils.isEmpty(scanResult.SSID)) {
                    continue;
                }

                scanList.append(scanResult.SSID + " ");
            }
        }
        Toast.makeText(this, scanList, Toast.LENGTH_SHORT).show();
        mScanList.setText(scanList);
    }

    private void handleSupplicantStateChanged(SupplicantState state, boolean hasError, int error) {
        if (hasError) {
            mSupplicantState.setText("ERROR AUTHENTICATING");
        } else {
            setSupplicantStateText(state);
        }
    }

    private void handleNetworkStateChanged(NetworkInfo networkInfo) {
        if (mWifiManager.isWifiEnabled()) {
            WifiInfo info = mWifiManager.getConnectionInfo();
            String summary = getSummary(networkInfo, info);
            mNetworkState.setText(summary);
        }
    }

    private String getSummary(NetworkInfo networkInfo, WifiInfo info) {
        String summary;
        DetailedState state = networkInfo.getDetailedState();
        boolean isEphemeral = info.getNetworkId() == -1;
        String ssid = info.getSSID();
        String passpointProvider = null;
        if (state == DetailedState.CONNECTED && ssid == null) {
            if (TextUtils.isEmpty(passpointProvider) == false) {
                // Special case for connected + passpoint networks.
                //通过 %1$s 连接
            } else if (isEphemeral) {
                // Special case for connected + ephemeral networks.
                //通过 WLAN 助手连接
            }
        }

        // Case when there is wifi connected without internet connectivity.
        final ConnectivityManager cm = (ConnectivityManager) WifiStatusActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (state == DetailedState.CONNECTED) {
            IWifiManager wifiManager = IWifiManager.Stub.asInterface(ServiceManager.getService(Context.WIFI_SERVICE));
            Network nw;

            try {
                nw = wifiManager.getCurrentNetwork();
            } catch (RemoteException e) {
                nw = null;
            }
            NetworkCapabilities nc = cm.getNetworkCapabilities(nw);
            if (nc != null && !nc.hasCapability(nc.NET_CAPABILITY_VALIDATED)) {
                // no network
                summary = "已连接，无网络";
            }

        }
        String[] formats = getResources().getStringArray((ssid == null) ? R.array.wifi_status : R.array.wifi_status_with_ssid);
        int index = state.ordinal();

        if (index >= formats.length || formats[index].length() == 0) {
            summary = "";
        }
        summary = String.format(formats[index], ssid);
        return summary;
    }

    private final void updatePingState() {
        final Handler handler = new Handler();
        // Set all to unknown since the threads will take a few secs to update.
        mPingHostnameResult = getResources().getString(R.string.radioInfo_unknown);
        mHttpClientTestResult = getResources().getString(R.string.radioInfo_unknown);

        mPingHostname.setText(mPingHostnameResult);
        mHttpClientTest.setText(mHttpClientTestResult);

        final Runnable updatePingResults = new Runnable() {
            public void run() {
                mPingHostname.setText(mPingHostnameResult);
                mHttpClientTest.setText(mHttpClientTestResult);
            }
        };

        Thread hostnameThread = new Thread() {
            @Override
            public void run() {
                pingHostname();
                handler.post(updatePingResults);
            }
        };
        hostnameThread.start();

        Thread httpClientThread = new Thread() {
            @Override
            public void run() {
                httpClientTest();
                handler.post(updatePingResults);
            }
        };
        httpClientThread.start();
    }

    /**
     * Test the connectivity to the target server
     * <p>
     * PING www.a.shifen.com (180.97.33.108) 56(84) bytes of data.<br/>
     * 64 bytes from 180.97.33.108: icmp_seq=1 ttl=54 time=58.6 ms<br/>
     * <p>
     * --- www.a.shifen.com ping statistics ---<br/>
     * 1 packets transmitted, 1 received, 0% packet loss, time 0ms<br/>
     * rtt min/avg/max/mdev = 58.674/58.674/58.674/0.000 ms<br/>
     */
    private final void pingHostname() {
        BufferedInputStream bis = null;
        StringBuffer sb = new StringBuffer();
        try {
            // TODO: Hardcoded for now, make it UI configurable
            int pingCount = 20;
            long pingOutTime = pingCount * 100;
            int pingPackSize = 24;
            String pingTarget = "www.baidu.com";

            Process p = Runtime.getRuntime().exec("ping -c " + pingCount + " -w " + pingOutTime + " -s " + pingPackSize + " " + pingTarget);
            int status = p.waitFor();
            byte[] buff = new byte[1024 * 8];
            bis = new BufferedInputStream(p.getInputStream());
            int length = -1;
            while (-1 != (length = bis.read(buff, 0, buff.length))) {
                String line = new String(Arrays.copyOf(buff, length));
                sb.append(line);
            }
            Log.d(LOG_TAG, "pingHostname: status " + status);
            if (status == 0) {
                mPingHostnameResult = "Pass";
                ArrayList<String> pingResponse = new ArrayList<>();
                String[] responses = sb.toString().split("\n");
                for (String response : responses) {
                    pingResponse.add(response);
                }
                loadResponse(pingResponse, pingCount, pingOutTime, pingPackSize + 8, pingTarget);
            } else {
                mPingHostnameResult = "Fail: Host unreachable";
            }
        } catch (UnknownHostException e) {
            mPingHostnameResult = "Fail: Unknown Host";
        } catch (IOException e) {
            mPingHostnameResult = "Fail: IOException";
        } catch (InterruptedException e) {
            mPingHostnameResult = "Fail: InterruptedException";
        } finally {
            if (null != bis) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.d(LOG_TAG, "pingHostname: " + sb.toString());
        }
    }

    /**
     * Test whether the network is connected to the Internet
     */
    private void httpClientTest() {
        HttpURLConnection urlConnection = null;
        try {
            // TODO: Hardcoded for now, make it UI configurable
            URL url = new URL("https://www.baidu.com");
            urlConnection = (HttpURLConnection) url.openConnection();
            if (urlConnection.getResponseCode() == 200) {
                mHttpClientTestResult = "Pass";
            } else {
                mHttpClientTestResult = "Fail: Code: " + urlConnection.getResponseMessage();
            }
        } catch (IOException e) {
            mHttpClientTestResult = "Fail: IOException";
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private void loadResponse(ArrayList<String> pingResponse, int pingCount, long pingOutTime, int pingPackSize, String pingTarget) {
        Log.d(LOG_TAG, "pingHostname: pingResponse.size " + pingResponse.size());
        if (null != pingResponse && pingResponse.size() > 0) {
            for (String response : pingResponse) {
                if (response.matches("--- .* ping statistics ---")) {
                    //--- 10.14.11.14 ping statistics ---
                    //--- www.a.shifen.com ping statistics ---
                    Log.d(LOG_TAG, "pingHostname: " + "host>> " + response);
                }

                if (response.matches(pingPackSize + " bytes from \\d{0,3}[.]\\d{0,3}[.]\\d{0,3}[.]\\d{0,3}: icmp_seq=\\d* ttl=\\d* time=\\d*[.]?\\d* ms")) {
                    //64 bytes from 10.14.11.14: icmp_seq=1 ttl=64 time=0.276 ms
                    Log.d(LOG_TAG, "pingHostname: " + "1>> " + response);
                }

                if (response.matches("\\d* packets transmitted, \\d* received, \\d{1,3}% packet loss, time \\d*[.]?\\d*ms")) {
                    //1 packets transmitted, 1 received, 0% packet loss, time 0ms
                    Log.d(LOG_TAG, "pingHostname: " + "2>> " + response);
                }

                if (response.matches("rtt [a-z]{0,4}/[a-z]{0,4}/[a-z]{0,4}/[a-z]{0,4} = \\d*[.]?\\d*/\\d*[.]?\\d*/\\d*[.]?\\d*/\\d*[.]?\\d* ms")) {
                    //rtt min/avg/max/mdev = 0.276/0.276/0.276/0.000 ms
                    Log.d(LOG_TAG, "pingHostname: " + "3>> " + response);
                }
            }
        }
    }

}
