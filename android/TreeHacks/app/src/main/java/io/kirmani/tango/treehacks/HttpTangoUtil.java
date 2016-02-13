/*
 * HttpTangoUtil.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.content.Context;

import com.android.volley.Response;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

public class HttpTangoUtil {
    private static final String BASE_URL = "http://treehacks.kirmani.io/";

    private Context mContext;

    public HttpTangoUtil(Context context) {
        mContext = context;
    }

    public void createSession(String sessionId) {
        String url = BASE_URL + "session/" + sessionId;
        JsonObjectRequest request = new JsonObjectRequest
            (Request.Method.POST, url, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    // do something with response
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

}

