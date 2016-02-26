/*
 * HttpTangoUtil.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.app.Activity;
import android.content.Context;
import android.provider.Settings.Secure;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

import net.gotev.uploadservice.MultipartUploadRequest;

public class HttpTangoUtil {
    private static final String TAG = HttpTangoUtil.class.getSimpleName();

    // HTTP Request URLs
    private static final String BASE_URL = "http://treehacks.kirmani.io";
    private static final String SESSION = "/session";
    private static final String UPLOAD = "/upload";

    // Keys
    private static final String TRANSLATION = "translation";
    private static final String ROTATION = "rotation";
    private static final String HOST = "host";
    private static final String JOIN_WAITING = "join_waiting";
    private static final String DEVICES = "devices";
    private static final String LOCALIZED = "localized";

    private static HttpTangoUtil mInstance;
    private Context mApplicationContext;
    private MainActivity mActivity;
    private Tango mTango;

    private String mSessionId= null;
    private boolean mIsHost = false;
    private boolean mLocalized = false;
    private boolean mJoinWaiting = false;
    private boolean mConnected = false;
    private TangoPoseData mPose = null;

    private JSONObject mAllDevices;

    private final Object mSharedLock = new Object();

    private HttpTangoUtil(MainActivity activity) {
        mActivity = activity;
        mApplicationContext = activity.getApplicationContext();
    }

    public static synchronized HttpTangoUtil getInstance(Activity activity) {
        if (mInstance == null) {
            mInstance = new HttpTangoUtil((MainActivity) activity);
        }
        return mInstance;
    }

    public void disconnect() {
        mConnected = false;
        mSessionId = null;
        mPose = null;
        mIsHost = false;
        mLocalized = false;
        mJoinWaiting = false;
        mActivity.setStatus("Disconnected");
        mActivity.setDisconnectEnabled(false);
    }

    public void attachTango(Tango tango) {
        mTango = tango;
    }

    public void createSession(final String sessionId) {
        mActivity.setStatus("Creating session: " + sessionId);
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, getSessionUrl(sessionId), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, response.toString());
                    showToast(String.format("Session created: %s", sessionId));
                    joinSession(sessionId);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                    mActivity.setStatus("Failed to create session: " + sessionId);
                }
            });
        HttpRequestUtil.getInstance(mApplicationContext).getRequestQueue().start();
        HttpRequestUtil.getInstance(mApplicationContext).addToRequestQueue(request);
    }

    public void joinSession(final String sessionId) {
        mActivity.setStatus("Joining session: " + sessionId);
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, getJoinUrl(sessionId), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, response.toString());
                    mConnected = true;
                    mSessionId = sessionId;
                    mActivity.setDisconnectEnabled(true);
                    getUpdates();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                }
            });
        // Access the RequestQueue through singleton class
        HttpRequestUtil.getInstance(mApplicationContext).getRequestQueue().start();
        HttpRequestUtil.getInstance(mApplicationContext).addToRequestQueue(request);
    }

    public boolean isConnected() {
        return mConnected;
    }

    public void updatePose(TangoPoseData pose) {
        synchronized (mSharedLock) {
            if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                    && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                mPose = pose;
            }
            if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                    && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                if (pose.statusCode == TangoPoseData.POSE_VALID) {
                    if (!mLocalized)
                        showToast("Localized!");
                    mLocalized = true;
                } else {
                    if (mLocalized)
                        showToast("Lost localization.");
                    mLocalized = false;
                }
            }
        }
        getUpdates();
    }

    public boolean isLocalized() {
        return mLocalized;
    }

    private void getUpdates() {
        if (mSessionId == null) {
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.GET, getSessionUrl(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    handleUpdates(response);
                    sendUpdates();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // handle error
                }
            });
        HttpRequestUtil.getInstance(mApplicationContext).addToRequestQueue(request);
    }

    private void sendUpdates() {
        try {
            JSONObject device = new JSONObject();
            if (mPose == null)
                return;

            // Send right-handed pose to server.
            device.put(TRANSLATION, new JSONArray(mPose.getTranslationAsFloats()));
            device.put(ROTATION, new JSONArray(mPose.getRotationAsFloats()));

            // Send localization and host status to server.
            device.put(LOCALIZED, mLocalized);
            device.put(HOST, mIsHost);

            mActivity.setStatus(String.format("Connected to session: %s (%s, %s)", mSessionId,
                        (mIsHost) ? "Host" : "Client",
                        (mLocalized) ? "Localized" : "Not Localized" ));
            JSONObject devices = new JSONObject();
            devices.put(getDeviceUuid(), device);
            JSONObject requestBody = new JSONObject();
            requestBody.put(DEVICES, devices);
            JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.PUT, getSessionUrl(), requestBody,
                 new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // handle error
                    }
                });
            // Access the RequestQueue through singleton class
            HttpRequestUtil.getInstance(mApplicationContext).addToRequestQueue(request);
        } catch (JSONException e) {
            Log.d(TAG, e.toString());
            showToast(e.getMessage());
        }
    }

    private void handleUpdates(JSONObject response) {
        try {
            JSONObject currentDevice = getCurrentDeviceResponse(response);
            if (currentDevice == null) {
                return;
            }
            if (mIsHost != currentDevice.getBoolean(HOST)) {
                mIsHost = currentDevice.getBoolean(HOST);
                if (mIsHost) {
                    mActivity.disconnect();
                    mActivity.setLearning(true);
                    mActivity.connect();
                    mLocalized = true;
                }
            }
            if (mJoinWaiting != response.getBoolean(JOIN_WAITING)) {
                mJoinWaiting = response.getBoolean(JOIN_WAITING);
                if (mJoinWaiting) {
                    if (mIsHost) {
                        uploadADF();
                    } else {
                        mActivity.setStatus("Waiting For ADF: " + mSessionId);
                        showToast("Waiting for new ADF...");
                        downloadADF();
                    }
                }
            }
            mAllDevices = response.getJSONObject(DEVICES);
        } catch (JSONException e) {
            Log.d(TAG, e.toString());
            showToast(e.getMessage());
        }
    }

    public Map<String, MultiTangoDevice> getAllOtherDevices() {
        Map<String, MultiTangoDevice> otherDevices = new HashMap<String, MultiTangoDevice>();
        if (mAllDevices == null)
            return otherDevices;
        Iterator<String> iter = mAllDevices.keys();
        while (iter.hasNext()) {
            String uuid = iter.next();
            if (!uuid.equals(getDeviceUuid())) {
                try {
                    if (mAllDevices.getJSONObject(uuid).getBoolean(LOCALIZED)) {
                        JSONArray positionArray = mAllDevices.getJSONObject(uuid)
                            .getJSONArray(TRANSLATION);
                        JSONArray rotationArray = mAllDevices.getJSONObject(uuid)
                            .getJSONArray(ROTATION);

                        // Convert from right-handed to left-handed, because Rajawali uses
                        // left-handed coordinate system. Rajawali quaternions use a left-hand
                        // rotation around the axis convention.
                        Vector3 position = new Vector3(
                                positionArray.getDouble(TangoPoseData.INDEX_TRANSLATION_X),
                                positionArray.getDouble(TangoPoseData.INDEX_TRANSLATION_Y),
                                positionArray.getDouble(TangoPoseData.INDEX_TRANSLATION_Z));
                        Quaternion orientation = new Quaternion(
                                rotationArray.getDouble(TangoPoseData.INDEX_ROTATION_W),
                                rotationArray.getDouble(TangoPoseData.INDEX_ROTATION_X),
                                rotationArray.getDouble(TangoPoseData.INDEX_ROTATION_Y),
                                rotationArray.getDouble(TangoPoseData.INDEX_ROTATION_Z));
                        orientation.conjugate();
                        MultiTangoDevice device = new MultiTangoDevice(uuid);
                        device.setOrientation(orientation);
                        device.setPosition(position);
                        otherDevices.put(uuid, device);
                    }
                } catch (JSONException e) {
                    Log.d(TAG, e.toString());
                    showToast(e.getMessage());
                }
            }
        }
        return otherDevices;
    }

    private void uploadADF() {
        showToast("Saving ADF...");
        mActivity.setStatus("Saving ADF: " + mSessionId);
        String uuid = mTango.saveAreaDescription();
        exportADF(uuid, "/sdcard/");

        try {
            Thread.sleep(5000);
            showToast("Uploading ADF...");
            mActivity.setStatus("Uploading ADF: " + mSessionId);
            MultipartUploadRequest req =
                new MultipartUploadRequest(mApplicationContext, getUploadUrl())
                .addFileToUpload("/sdcard/" + uuid, "adf")
                .addParameter("session", mSessionId);
            req.startUpload();
        } catch (FileNotFoundException e) {
            uploadADF();
        } catch (IllegalArgumentException e) {
            showToast("Missing some arguments. " + e.getMessage());
        } catch (MalformedURLException e) {
            showToast(e.getMessage());
        } catch (InterruptedException e) {
            showToast(e.getMessage());
        }
    }

    public void downloadADF() {
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.GET, getDownloadUrl(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if (response.getBoolean("download_ready")) {
                            try {
                                // Download the ADF when new file is available.
                                showToast("Downloading ADF...");
                                mActivity.setStatus("Downloading ADF: " + mSessionId);
                                String outputFile = "/sdcard/" + mSessionId;
                                OutputStream output = new FileOutputStream(outputFile);

                                byte data[] = Base64.decode(response.getString("data"),
                                        Base64.DEFAULT);

                                output.write(data);
                                output.flush();
                                output.close();

                                // import ADF
                                showToast("Importing ADF...");
                                mActivity.setStatus("Importing ADF: " + mSessionId);
                                mActivity.disconnect();
                                mActivity.setLearning(false);
                                mActivity.connect();
                                mTango.experimentalLoadAreaDescriptionFromFile(outputFile);
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                                showToast(e.getMessage());
                            }
                        } else {
                            downloadADF();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        showToast(e.getMessage());
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                }
            });
        HttpRequestUtil.getInstance(mApplicationContext).addToRequestQueue(request);
    }

    private JSONObject getCurrentDeviceResponse(JSONObject response) {
        try {
            return response.getJSONObject(DEVICES).getJSONObject(getDeviceUuid());
        } catch (JSONException e) {
            return null;
        }
    }

    private String getDownloadUrl() {
        return getSessionUrl() + "/download";
    }

    private String getUploadUrl() {
        return getSessionUrl() + "/upload";
    }

    private String getJoinUrl(String sessionId) {
        return getSessionUrl(sessionId) + "/join";
    }

    private String getSessionUrl() {
        if (mSessionId == null) {
            throw new IllegalStateException("mSessionId cannot be null.");
        }
        return BASE_URL + SESSION + "/" + mSessionId;
    }

    private String getSessionUrl(String sessionId) {
        return BASE_URL + SESSION + "/" + sessionId;
    }

    private void exportADF(String uuid, String destinationFile) {
        Log.d(TAG, destinationFile);
        mTango.exportAreaDescriptionFile(uuid, destinationFile);
    }

    private String getDeviceUuid() {
        return Secure.getString(mApplicationContext.getContentResolver(), Secure.ANDROID_ID);
    }

    private void showToast(final String message) {
        Handler h = new Handler(mApplicationContext.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mApplicationContext, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}

