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
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoPoseData;

import org.json.JSONObject;
import org.json.JSONException;

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
    private static final String SESSION_NAME = "name";
    private static final String SESSION_DATA = "data";
    private static final String POSITION = "position";
    private static final String HOST = "host";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String JOIN_WAITING = "join_waiting";
    private static final String ADF_METADATA = "adf_metadata";
    private static final String DEVICES = "devices";
    private static final String LOCALIZED = "localized";

    private static HttpTangoUtil mInstance;
    private Context mContext;
    private Activity mActivity;
    private Tango mTango;
    private String mSessionId;
    private boolean mIsHost;
    private boolean dummy = true;

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

    public void attachActivity(Activity activity) {
        mActivity = activity;
    }

    public void createSession(final String sessionId) {
        String url = BASE_URL + SESSION;
        try {
            JSONObject device = new JSONObject();
            device.put(LOCALIZED, true);
            mIsHost = true;
            device.put(HOST, mIsHost);
            JSONObject devices = new JSONObject();
            devices.put(getDeviceUuid(), device);
            JSONObject adfMetadata = new JSONObject();
            JSONObject requestData = new JSONObject();
            requestData.put(DEVICES, devices);
            requestData.put(ADF_METADATA, adfMetadata);
            requestData.put(JOIN_WAITING, false);
            JSONObject requestBody = new JSONObject();
            requestBody.put(SESSION_NAME, sessionId);
            requestBody.put(SESSION_DATA, requestData);
            JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.POST, url, requestBody, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                        mSessionId = sessionId;
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // handle error
                    }
                });
            // Access the RequestQueue through singleton class
            HttpRequestUtil.getInstance(mContext).getRequestQueue().start();
            HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void joinSession(final String sessionId) {
        String url = BASE_URL + SESSION;
        try {
            JSONObject device = new JSONObject();
            device.put(LOCALIZED, false);
            mIsHost = false;
            device.put(HOST, mIsHost);
            JSONObject devices = new JSONObject();
            devices.put(getDeviceUuid(), device);
            JSONObject requestData = new JSONObject();
            requestData.put(DEVICES, devices);
            requestData.put(JOIN_WAITING, true);
            JSONObject requestBody = new JSONObject();
            requestBody.put(SESSION_NAME, sessionId);
            requestBody.put(SESSION_DATA, requestData);
            JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.POST, url, requestBody, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                        mSessionId = sessionId;
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // handle error
                    }
                });
            // Access the RequestQueue through singleton class
            HttpRequestUtil.getInstance(mContext).getRequestQueue().start();
            HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void updatePose(TangoPoseData pose) {
        if (mSessionId == null)
            return;
        String url = BASE_URL + SESSION + "/" + mSessionId;
        double[] translation = pose.translation;
        double[] rotation = pose.rotation;
        try {
            // send updates
            JSONObject position = new JSONObject();
            position.put(X, translation[0]);
            position.put(Y, translation[1]);
            position.put(Z, translation[2]);
            JSONObject device = new JSONObject();
            device.put(POSITION, position);
            JSONObject devices = new JSONObject();
            devices.put(getDeviceUuid(), device);
            JSONObject requestBody = new JSONObject();
            requestBody.put(DEVICES, devices);
            JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.PUT, url, requestBody, new Response.Listener<JSONObject>() {
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

            // check for updates
            checkForUpdates();

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkForUpdates() {
        String url = BASE_URL + SESSION + "/" + mSessionId;
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.GET, url, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    handleUpdates(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // handle error
                }
            });
        HttpRequestUtil.getInstance(mContext).addToRequestQueue(request);
    }

    private void handleUpdates(JSONObject response) {
        try {
            if (response.getBoolean(JOIN_WAITING)) {
                if (mIsHost && dummy) {
                    // save and upload ADF
                    saveADF();
                    dummy = false;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveADF() {
        String url = BASE_URL + UPLOAD;
        String uuid = mTango.saveAreaDescription();
        //String uuid = "d7412f96-cba7-4d76-963f-2cc9ed7289d8";
        exportADF(uuid, "/sdcard/");// + uuid + ".adf");
        TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, url, new Response.Listener<JSONObject>() {
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
    }

    private void exportADF(String uuid, String destinationFile) {
        Log.d(TAG, destinationFile);
        mTango.exportAreaDescriptionFile(uuid, destinationFile);
    }

    private String getDeviceUuid() {
        return Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
    }
}

