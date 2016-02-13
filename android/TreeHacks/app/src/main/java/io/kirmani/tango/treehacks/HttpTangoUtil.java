/*
 * HttpTangoUtil.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.content.Context;
import android.provider.Settings.Secure;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoPoseData;

import org.json.JSONObject;
import org.json.JSONException;

public class HttpTangoUtil {
    private static final String TAG = HttpTangoUtil.class.getSimpleName();

    // HTTP Request URLs
    private static final String BASE_URL = "http://treehacks.kirmani.io";
    private static final String SESSION = "/session";

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

    private static HttpTangoUtil mInstance;
    private Context mContext;
    private Tango mTango;
    private String mSessionId;

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

    public void createSession(final String sessionId) {
        String url = BASE_URL + SESSION;
        try {
            JSONObject devices = new JSONObject();
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

    public void updatePose(TangoPoseData pose) {
        if (mSessionId == null)
            return;
        String url = BASE_URL + SESSION + "/" + mSessionId;
        String uuid = Secure.getString(mContext.getContentResolver(),
                                                        Secure.ANDROID_ID);
        double[] translation = pose.translation;
        double[] rotation = pose.rotation;
        try {
            JSONObject position = new JSONObject();
            position.put(X, translation[0]);
            position.put(Y, translation[1]);
            position.put(Z, translation[2]);
            JSONObject device = new JSONObject();
            device.put(POSITION, position);
            device.put(HOST, true);
            JSONObject deviceUuid = new JSONObject();
            deviceUuid.put(uuid, device);
            JSONObject requestBody = new JSONObject();
            requestBody.put(DEVICES, deviceUuid);
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }
}

