package com.vuzix.samplewebrtc.android;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ericsson.research.owr.Owr;
import com.ericsson.research.owr.sdk.InvalidDescriptionException;
import com.ericsson.research.owr.sdk.RtcCandidate;
import com.ericsson.research.owr.sdk.RtcCandidates;
import com.ericsson.research.owr.sdk.RtcConfig;
import com.ericsson.research.owr.sdk.RtcConfigs;
import com.ericsson.research.owr.sdk.RtcSession;
import com.ericsson.research.owr.sdk.RtcSessions;
import com.ericsson.research.owr.sdk.SessionDescription;
import com.ericsson.research.owr.sdk.SessionDescriptions;
import com.ericsson.research.owr.sdk.SimpleStreamSet;
import com.ericsson.research.owr.sdk.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String CANDIDATE = "candidate";
    private static final String SDP = "sdp";

    private final RtcConfig RTC_CONFIG = RtcConfigs.defaultConfig(BuildConfig.ICE_SERVER);

    static {
        Owr.init();
        Owr.runInBackground();
    }

    @Bind(R.id.session_id)
    TextView mSessionId;

    @Bind(R.id.texture_view)
    TextureView mVideoView;

    private SessionChannel mSessionChannel;

    private final RtcSession.OnLocalCandidateListener mOnLocalCandidateListener
            = new RtcSession.OnLocalCandidateListener() {
        @Override
        public void onLocalCandidate(RtcCandidate candidate) {
            Log.d(TAG, "onLocalCandidate");
            if (mPeerChannel == null) {
                return;
            }

            Log.d(TAG, "onLocalCandidate2");

            try {
                JSONObject json = new JSONObject();
                json.putOpt("candidate", RtcCandidates.toJsep(candidate));
                json.getJSONObject("candidate").put("sdpMid", "video");
                mSessionChannel.send(mPeerChannel, json);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException", e);
            }
        }
    };

    private SessionChannel.Peer mPeerChannel;

    private final RtcSession.OnLocalDescriptionListener mOnLocalDescriptionListener
            = new RtcSession.OnLocalDescriptionListener() {
        @Override
        public void onLocalDescription(SessionDescription localDescription) {
            Log.d(TAG, "onLocalDescription");

            if (mPeerChannel == null) {
                return;
            }

            try {
                JSONObject json = new JSONObject();
                json.putOpt(SDP, SessionDescriptions.toJsep(localDescription));
                Log.d(TAG, "sending sdp: " + json);
                mSessionChannel.send(mPeerChannel, json);
            } catch (JSONException e) {
                Log.d(TAG, "JSONException", e);
            }
        }
    };

    private final SessionChannel.PeerDisconnectListener mOnDisconnectedListener
            = new SessionChannel.PeerDisconnectListener() {
        @Override
        public void onPeerDisconnect(SessionChannel.Peer peer) {
            Log.d(TAG, "onPeerDisconnect");

            mRtcSession.stop();
            mPeerChannel = null;
        }
    };

    private final SessionChannel.MessageListener mOnMessageListener
            = new SessionChannel.MessageListener() {
        @Override
        public void onMessage(JSONObject data) {
            Log.d(TAG, "onMessage " + data);

            if (data.has(CANDIDATE)) {
                JSONObject candidate = data.optJSONObject(CANDIDATE);
                Log.v(TAG, "candidate: " + candidate);
                RtcCandidate rtcCandidate = RtcCandidates.fromJsep(candidate);
                if (rtcCandidate != null) {
                    mRtcSession.addRemoteCandidate(rtcCandidate);
                }
            }
            if (data.has(SDP)) {
                JSONObject sdp = data.optJSONObject(SDP);
                try {
                    SessionDescription sessionDescription = SessionDescriptions.fromJsep(sdp);
                    if (sessionDescription.getType() == SessionDescription.Type.OFFER) {
                        onInboundCall(sessionDescription);
                    } else {
                        onAnswer(sessionDescription);
                    }
                } catch (InvalidDescriptionException e) {
                    Log.d(TAG, "InvalidDescriptionException", e);
                }
            }
        }
    };

    private void onAnswer(SessionDescription sessionDescription) {
        Log.d(TAG, "onAnswer");

        if (mRtcSession == null) {
            return;
        }

        try {
            mRtcSession.setRemoteDescription(sessionDescription);
        } catch (InvalidDescriptionException e) {
            Log.e(TAG, e.getClass().getSimpleName(), e);
        }
    }

    private void onInboundCall(SessionDescription sessionDescription) {
        Log.d(TAG, "onInboundCall");

        try {
            mRtcSession.setRemoteDescription(sessionDescription);
            mRtcSession.start(mStreamSet);
        } catch (InvalidDescriptionException e) {
            Log.e(TAG, e.getClass().getSimpleName(), e);
        }
    }

    private final SessionChannel.JoinPeerListener mJoinListener
            = new SessionChannel.JoinPeerListener() {
        @Override
        public void onPeerJoin(SessionChannel.Peer peer) {
            Log.d(TAG, "onPeerJoin");

            mPeerChannel = peer;
            mPeerChannel.setDisconnectListener(mOnDisconnectedListener);
            mPeerChannel.setMessageListener(mOnMessageListener);

            mRtcSession = RtcSessions.create(RTC_CONFIG);
            mRtcSession.setOnLocalCandidateListener(mOnLocalCandidateListener);
            mRtcSession.setOnLocalDescriptionListener(mOnLocalDescriptionListener);

            updateUis();
        }
    };

    private void updateUis() {
        mJoinButton.setEnabled(mPeerChannel == null);
        mCallButton.setEnabled(mPeerChannel != null);
        if (mPeerChannel != null) {
            mSessionId.setText("Peer joined: " + mPeerChannel.getId());
        } else {
            mSessionId.setText(BuildConfig.SESSION_ID);
        }
    }

    private final SessionChannel.DisconnectListener mDisconnectListener
            = new SessionChannel.DisconnectListener() {
        @Override
        public void onDisconnect() {
            Log.d(TAG, "onDisconnect");

            Toast.makeText(MainActivity.this, "Disconnected from server", Toast.LENGTH_LONG).show();

            if (mRtcSession != null) {
                mRtcSession.stop();
                mRtcSession = null;
            }
            mStreamSet = null;
            mRtcSession = null;
            mSessionChannel = null;

            updateUis();
        }
    };

    private final SessionChannel.SessionFullListener mSessionFullListener
            = new SessionChannel.SessionFullListener() {
        @Override
        public void onSessionFull() {
            Log.d(TAG, "onSessionFull");
            Toast.makeText(MainActivity.this, "Session is full or busy.\n" +
                            "Please try other session Id.",
                    Toast.LENGTH_LONG).show();
        }
    };

    private SimpleStreamSet mStreamSet;
    private VideoView mRemoteView;

    @Bind(R.id.join)
    Button mJoinButton;

    @Bind(R.id.call)
    Button mCallButton;

    @OnClick(R.id.join)
    public void onJoinClick(View view) {
        Log.d(TAG, "onJoinClick");
        view.setEnabled(false);

        mSessionChannel = new SessionChannel(BuildConfig.SERVER, BuildConfig.SESSION_ID);
        mSessionChannel.setJoinListener(mJoinListener);
        mSessionChannel.setDisconnectListener(mDisconnectListener);
        mSessionChannel.setSessionFullListener(mSessionFullListener);

        mStreamSet = SimpleStreamSet.defaultConfig(BuildConfig.USE_AUDIO, BuildConfig.USE_VIDEO);
        mRemoteView = mStreamSet.createRemoteView();
        mRemoteView.setView(mVideoView);
    }

    @OnClick(R.id.call)
    public void onCallClick(View view) {
        Log.d(TAG, "onCallClick");
        view.setEnabled(false);

        mSessionId.setText("Connecting: " + mPeerChannel.getId());

        mRtcSession.start(mStreamSet);
    }

    private RtcSession mRtcSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        updateUis();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mRtcSession != null) {
            mRtcSession.stop();
        }
    }

}
