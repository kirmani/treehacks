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
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

public class HttpRequestUtil {
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
}

