/*
 * HttpTangoUtil.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoPoseData;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.rajawali3d.math.vector.Vector3;

import net.gotev.uploadservice.MultipartUploadRequest;

public class HttpTangoUtil {
    private static final String TAG = HttpTangoUtil.class.getSimpleName();

    // HTTP Request URLs
    private static final String BASE_URL = "http://treehacks.kirmani.io";
    private static final String SESSION = "/session";
    private static final String UPLOAD = "/upload";

    // Intents
    private static final String INTENT_CLASSPACKAGE = "com.projecttango.tango";
    private static final String INTENT_IMPORTEXPORT_CLASSNAME = "com.google.atap.tango.RequestImportExportActivity";

    // Keys
    private static final String POSITION = "position";
    private static final String HOST = "host";
    private static final String JOIN_WAITING = "join_waiting";
    private static final String DEVICES = "devices";
    private static final String LOCALIZED = "localized";

    private static HttpTangoUtil mInstance;
    private Context mContext;
    private MainActivity mActivity;
    private Tango mTango;
    private AugmentedRealityRenderer mRenderer;

    private String mSessionId;
    private boolean mIsHost;
    private boolean mLocalized = false;
    private boolean mJoinWaiting = false;
    private TangoPoseData mPose;
    private Vector3 mTranslation;
    private JSONObject mAllDevices;

    private final Object mSharedLock = new Object();

    private HttpTangoUtil(Context context) {
        mContext = context;
    }

    public static synchronized HttpTangoUtil getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new HttpTangoUtil(context);
        }
        return mInstance;
    }

    public void attachTango(Tango tango) {
        mTango = tango;
    }

    public void attachActivity(MainActivity activity) {
        mActivity = activity;
    }

    public void attachRenderer(AugmentedRealityRenderer renderer) {
        mRenderer = renderer;
    }

    public void createSession(final String sessionId) {
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, getSessionUrl(sessionId), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, response.toString());
                    showToast(String.format("Session (%s) created! :)", sessionId));
                    joinSession(sessionId);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                }
            });
        HttpRequestUtil.getInstance(mContext).getRequestQueue().start();
        HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
    }

    public void joinSession(final String sessionId) {
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, getJoinUrl(sessionId), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, response.toString());
                    mSessionId = sessionId;
                    getUpdates();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                }
            });
        // Access the RequestQueue through singleton class
        HttpRequestUtil.getInstance(mContext).getRequestQueue().start();
        HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
    }

    public void updatePose(TangoPoseData pose) {
        synchronized (mSharedLock) {
            if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                    && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                mPose = pose;
            } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                    && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                if (pose.statusCode == TangoPoseData.POSE_VALID) {
                    if (!mLocalized) {
                        showToast("Localized! :)");
                    }
                    mLocalized = true;
                    mTranslation = new Vector3(pose.translation);
                } else {
                    if (mLocalized) {
                        showToast("Lost localization. :(");
                    }
                    mLocalized = false;
                }
            }
        }
        getUpdates();
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
        HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
    }

    private void sendUpdates() {
        try {
            JSONObject device = new JSONObject();
            if (mPose != null) {
                device.put(POSITION, new JSONArray(mPose.translation));
            }
            device.put(LOCALIZED, mLocalized);
            device.put(HOST, mIsHost);
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
            HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleUpdates(JSONObject response) {
        try {
            JSONObject currentDevice = getCurrentDeviceResponse(response);
            mIsHost = currentDevice.getBoolean(HOST);
            if (mIsHost) {
                mLocalized = true;
            }
            if (mJoinWaiting != response.getBoolean(JOIN_WAITING)) {
                mJoinWaiting = response.getBoolean(JOIN_WAITING);
                if (mJoinWaiting) {
                    if (mIsHost) {
                        uploadADF();
                    } else {
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

    public List<Vector3> getAllOtherDevices() {
        if ((mAllDevices == null) || (mTranslation == null)) {
            return new ArrayList<Vector3>();
        }
        ArrayList<Vector3> otherDevices = new ArrayList<Vector3>();
        Iterator<String> iter = mAllDevices.keys();
        while (iter.hasNext()) {
            String uuid = iter.next();
            if (!uuid.equals(getDeviceUuid())) {
                try {
                    if (mAllDevices.getJSONObject(uuid).getBoolean(LOCALIZED)) {
                        JSONArray position = mAllDevices.getJSONObject(uuid)
                            .getJSONArray("position");
                            Vector3 adfPosition = new Vector3(position.getDouble(0),
                                position.getDouble(1), position.getDouble(2));
                            otherDevices.add(adfPosition.subtract(mTranslation));
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
        String uuid = mTango.saveAreaDescription();
        mActivity.disconnect();
        mActivity.setAdfUuid(uuid);
        mActivity.connect();
        exportADF(uuid, "/sdcard/");

        try {
            Thread.sleep(5000);
            showToast("Saving ADF...");
            MultipartUploadRequest req =
                new MultipartUploadRequest(mContext, getUploadUrl())
                .addFileToUpload("/sdcard/" + uuid, "adf")
                .addParameter("session", mSessionId);
            req.startUpload();
        } catch (FileNotFoundException e) {
            showToast(e.getMessage());
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
                                // download the file
                                showToast("Downloading ADF...");
                                String outputFile = "/sdcard/" + mSessionId;
                                OutputStream output = new FileOutputStream(outputFile);

                                byte data[] = response.getString("data").getBytes();

                                output.write(data);
                                output.flush();
                                output.close();

                                // import ADF
                                showToast("Importing ADF...");
                                mActivity.disconnect();
                                mActivity.setLearning(false);
                                mActivity.connect();
                                mTango.experimentalLoadAreaDescriptionFromFile(outputFile);
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                                showToast(e.getMessage());
                            }
                        } else {
                            showToast("Waiting for new ADF...");
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
        HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
    }

    private JSONObject getCurrentDeviceResponse(JSONObject response) throws JSONException {
        return response.getJSONObject(DEVICES).getJSONObject(getDeviceUuid());
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
        return Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
    }

    private void showToast(final String message) {
        Handler h = new Handler(mContext.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}

