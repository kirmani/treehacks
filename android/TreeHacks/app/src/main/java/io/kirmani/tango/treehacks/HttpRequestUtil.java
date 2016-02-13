/*
 * HttpRequestUtil.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

public class HttpRequestUtil {
    private static final String BASE_URL = "http://treehacks.kirmani.io/";

    private static HttpRequestUtil mInstance;
    private RequestQueue mRequestQueue;
    private static Context mContext;

    private HttpRequestUtil(Context context) {
        mContext = context;
    }

    public static synchronized HttpRequestUtil getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new HttpRequestUtil(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> request) {
        getRequestQueue().add(request);
    }

    public static void createSession(String sessionId) {
        String url = BASE_URL + "session/" + sessionId;
        // JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null,
        //         new Response.Listener<JSONObject>() {
        //             @Override
        //             public void onResponse(JSONObject response) {
        //                 // do something with response
        //             }
        // }, new Response.ErrorListener() {
        //     @Override
        //     public void onErrorResponse(VolleyError error) {
        //         // handle error
        //     }
        // });
    }

    public HttpRequestUtil() {

    }
}

