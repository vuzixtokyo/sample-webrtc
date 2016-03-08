package com.vuzix.samplewebrtc.android;

/*
   Copyright 2016 Vuzix Corporation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

/*
 * Copyright (c) 2015, Ericsson AB.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionChannel {
    public static final String TAG = SessionChannel.class.getSimpleName();

    public static final int USER_ID_BITS = 40;
    public static final int USER_ID_RADIX = 32;

    private final Map<String, Peer> mPeers = new HashMap<>();

    private final Handler mMainHandler = new Handler();
    private Handler mSendHandler;

    private final String mClientToServerUrl;
    private final String mServerToClientUrl;

    private JoinPeerListener mJoinListener;
    private DisconnectListener mDisconnectListener;
    private SessionFullListener mSessionFullListener;

    public void setJoinListener(JoinPeerListener joinListener) {
        mJoinListener = joinListener;
    }

    public void setDisconnectListener(DisconnectListener disconnectListener) {
        mDisconnectListener = disconnectListener;
    }

    public void setSessionFullListener(SessionFullListener sessionFullListener) {
        mSessionFullListener = sessionFullListener;
    }

    public SessionChannel(String serverUrl, String session) {
        String userId = new BigInteger(USER_ID_BITS, new SecureRandom()).toString(USER_ID_RADIX);
        mServerToClientUrl = serverUrl + "/stoc/" + session + "/" + userId;
        mClientToServerUrl = serverUrl + "/ctos/" + session + "/" + userId;
        open();
    }

    private ConnectionThread mConnectionThread;

    private synchronized void open() {
        close();

        // 送信用ハンドラを作成
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mSendHandler = new Handler();
                Looper.loop();
            }
        }.start();

        mConnectionThread = new ConnectionThread();
        mConnectionThread.start();
    }

    private synchronized void close() {
        if (mConnectionThread == null) {
            return;
        }

        mConnectionThread.isFinished.set(true);
        mConnectionThread = null;
    }

    private class ConnectionThread extends Thread {

        private static final String EVENT = "event";
        private static final String DATA = "data";
        private static final String PREFIX_USER = "user-";
        private static final String JOIN = "join";
        private static final String LEAVE = "leave";
        private static final String SESSION_FULL = "sessionfull";
        private static final String BUSY = "busy";

        private final AtomicBoolean isFinished = new AtomicBoolean();

        @Override
        public void run() {
            Log.d(TAG, "ConnectionThread started.");

            Log.d(TAG, "connecting....");

            HttpURLConnection urlConnection = null;
            try {
                Log.d(TAG, mServerToClientUrl);
                urlConnection = (HttpURLConnection) new URL(mServerToClientUrl).openConnection();

                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(false);
                urlConnection.connect();

                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));

                readEvent(bufferedReader);

            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Peer peer : mPeers.values()) {
                            peer.onDisconnect();
                        }

                        if (mDisconnectListener != null) {
                            mDisconnectListener.onDisconnect();
                        }
                    }
                });

            }

        }

        private void readEvent(final BufferedReader bufferedReader) throws IOException {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() < 2) {
                    continue;
                }

                String[] eventSplit = line.split(":", 2);

                if (eventSplit.length != 2 || !eventSplit[0].equals(EVENT)) {
                    Log.w(TAG, "SSE: invalid event: " + line + " => " + Arrays.toString(eventSplit));
                    while (!(line = bufferedReader.readLine()).isEmpty()) {
                        Log.w(TAG, "SSE: skipped after malformed event: " + line);
                    }
                    break;
                }

                final String event = eventSplit[1];

                if ((line = bufferedReader.readLine()) != null) {
                    final String[] dataSplit = line.split(":", 2);

                    if (dataSplit.length != 2 || !dataSplit[0].equals(DATA)) {
                        Log.w(TAG, "SSE: invalid data: " + line + " => " + Arrays.toString(dataSplit));
                    }
                    final String data = dataSplit[1];

                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleEvent(event, data);
                        }
                    });
                }
            }
        }

        private void handleEvent(String event, String data) {
            Log.d(TAG, "event:" + event + ", data:" + data);

            if (event.startsWith(PREFIX_USER)) {
                String peerId = event.substring(PREFIX_USER.length());
                Peer peerChannel = mPeers.get(peerId);
                if (peerChannel != null) {
                    peerChannel.onMessage(data);
                }
            } else if (event.equals(LEAVE)) {
                Peer peerChannel = mPeers.remove(data);
                if (peerChannel != null) {
                    peerChannel.onDisconnect();
                }
            } else if (event.equals(JOIN)) {
                Peer peerChannel = new Peer(mClientToServerUrl, data);
                mPeers.put(data, peerChannel);
                if (mJoinListener != null) {
                    mJoinListener.onPeerJoin(peerChannel);
                }
            } else if (event.equals(SESSION_FULL) || event.equals(BUSY)) {
                if (mSessionFullListener != null) {
                    mSessionFullListener.onSessionFull();
                }
            } else {
                Log.w(TAG, "unknown event: " + event);
            }
        }
    }

    public interface MessageListener {
        void onMessage(JSONObject data);
    }

    public interface SessionFullListener {
        void onSessionFull();
    }

    public interface DisconnectListener {
        void onDisconnect();
    }

    public interface JoinPeerListener {
        void onPeerJoin(Peer peer);
    }

    public interface PeerDisconnectListener {
        void onPeerDisconnect(Peer peer);
    }

    public void send(final Peer peer, final JSONObject message) {
        mSendHandler.post(new Runnable() {
            @Override
            public void run() {
                if (peer.mDisconnected) {
                    return;
                }

                HttpURLConnection urlConnection = null;
                try {
                    urlConnection = (HttpURLConnection) new URL(peer.mPeerUrl).openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoOutput(true);

                    OutputStream os = urlConnection.getOutputStream();
                    os.write(message.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                        Log.e(TAG, "response " + responseCode);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "IOException", e);
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }
            }
        });
    }

    public static class Peer {

        private final String mId;
        private final String mPeerUrl;
        private boolean mDisconnected = false;

        private MessageListener mMessageListener;
        private PeerDisconnectListener mDisconnectListener;

        public String getId() {
            return mId;
        }

        private Peer(String serverUrl, String peerId) {
            mId = peerId;
            mPeerUrl = serverUrl + "/" + peerId;
        }

        private void onMessage(String message) {
            Log.d(TAG, "onMessage " + message);

            if (mDisconnected) {
                return;
            }
            if (mMessageListener != null) {
                try {
                    JSONObject json = new JSONObject(message);
                    mMessageListener.onMessage(json);
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException", e);
                }
            }
        }

        private void onDisconnect() {
            Log.d(TAG, "onDisconnect ");

            mDisconnected = true;

            if (mDisconnectListener != null) {
                mDisconnectListener.onPeerDisconnect(this);
                mDisconnectListener = null;
                mMessageListener = null;
            }
        }

        public void setMessageListener(MessageListener messageListener) {
            mMessageListener = messageListener;
        }

        public void setDisconnectListener(PeerDisconnectListener onDisconnectListener) {
            mDisconnectListener = onDisconnectListener;
        }
    }
}
