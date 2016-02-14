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
    private static final String SESSION_NAME = "name";
    private static final String SESSION_DATA = "data";
    private static final String POSITION = "position";
    private static final String HOST = "host";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String JOIN_WAITING = "join_waiting";
    private static final String ADF = "adf";
    private static final String DEVICES = "devices";
    private static final String LOCALIZED = "localized";

    private static HttpTangoUtil mInstance;
    private Context mContext;
    private MainActivity mActivity;
    private Tango mTango;
    private AugmentedRealityRenderer mRenderer;
    private String mSessionId;
    private boolean mIsHost;
    private boolean mIsLocalized;
    private boolean dummy = true;
    private boolean mReadyToResume = false;

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

    public boolean isReadyToResume() {
        return mReadyToResume;
    }

    public void createSession(final String sessionId) {
        String url = BASE_URL + SESSION;
        try {
            mActivity.resume();
            mReadyToResume = true;
            mActivity.startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
            JSONObject device = new JSONObject();
            mIsHost = true;
            mIsLocalized = true;
            device.put(HOST, mIsHost);
            device.put(LOCALIZED, mIsLocalized);
            JSONObject devices = new JSONObject();
            devices.put(getDeviceUuid(), device);
            JSONObject adfMetadata = new JSONObject();
            JSONObject requestData = new JSONObject();
            requestData.put(DEVICES, devices);
            requestData.put(ADF, adfMetadata);
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
                        showToast("Connected, hosting! :)");
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
        mActivity.disableLearning();
        mActivity.resume();
        mReadyToResume = true;
        mActivity.startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                Tango.TANGO_INTENT_ACTIVITYCODE);
        String url = BASE_URL + SESSION;
        try {
            JSONObject device = new JSONObject();
            mIsHost = false;
            mIsLocalized = false;
            device.put(HOST, mIsHost);
            device.put(LOCALIZED, mIsLocalized);
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
        synchronized (mSharedLock) {
            try {
                JSONObject device = new JSONObject();
                // send updates
                if (!mIsHost && pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                    && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                    if (mIsLocalized) {
                        JSONObject position = new JSONObject();
                        position.put(X, translation[0]);
                        position.put(Y, translation[1]);
                        position.put(Z, translation[2]);
                        device.put(POSITION, position);
                    }
                } else if (mIsHost && pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                    JSONObject position = new JSONObject();
                    position.put(X, translation[0]);
                    position.put(Y, translation[1]);
                    position.put(Z, translation[2]);
                    device.put(POSITION, position);
                } else if (!mIsHost
                        && pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                    if (pose.statusCode == TangoPoseData.POSE_VALID) {
                        mIsLocalized = true;
                    } else {
                        mIsLocalized = false;
                    }
                    device.put(LOCALIZED, mIsLocalized);
                }
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
    }

    public boolean isLocalized() {
        return mIsLocalized;
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
            if (mIsHost && response.getBoolean(JOIN_WAITING) && dummy) {
                // save and upload ADF
                saveADF();
                dummy = false;
            } else if (!mIsHost && response.has(ADF) && dummy) {
                downloadADF(response);
                dummy = false;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveADF() {
        Thread t = new Thread() {
            @Override
            public void run() {
                super.run();
                String url = BASE_URL + UPLOAD;
                String uuid = mTango.saveAreaDescription();
                exportADF(uuid, "/sdcard/");

                try {
                    Thread.sleep(5000);
                    MultipartUploadRequest req = new MultipartUploadRequest(mContext, url)
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
        };
        t.start();
    }

    public void downloadADF(final JSONObject response) {
        if (response.has(ADF)) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(BASE_URL + response.getString(ADF));
                        URLConnection connection = url.openConnection();
                        connection.connect();
                        int fileLength = connection.getContentLength();

                        // download the file
                        String outputFile = "/sdcard/" + response.getString(ADF);
                        InputStream input = new BufferedInputStream(url.openStream());
                        OutputStream output = new FileOutputStream(outputFile);

                        byte data[] = new byte[1024];
                        long total = 0;
                        int count;
                        while ((count = input.read(data)) != -1) {
                            total += count;
                            output.write(data, 0, count);
                        }

                        output.flush();
                        output.close();
                        input.close();

                        // import ADF
                        mTango.experimentalLoadAreaDescriptionFromFile(outputFile);
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            };
            showToast("Connected, Downloading ADF and localizing...");
            t.start();
        }
    }

    private void exportADF(String uuid, String destinationFile) {
        Log.d(TAG, destinationFile);
        mTango.exportAreaDescriptionFile(uuid, destinationFile);
    }

    private String getDeviceUuid() {
        return Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
    }

    public void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

}

