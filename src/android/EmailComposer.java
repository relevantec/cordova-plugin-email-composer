/*
    Copyright 2013-2016 appPlant UG
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/

package de.appplant.cordova.emailcomposer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("Convert2Diamond")
public class EmailComposer extends CordovaPlugin {

    /**
     * The log tag for this plugin
     */
    static final String LOG_TAG = "EmailComposer";

    static final String neededPermission = android.Manifest.permission.GET_ACCOUNTS;

    private JSONArray args;

    public static final int REQUEST_CODE_ISAVAILABLE = 0;
    public static final int REQUEST_CODE_OPEN = 1;
    public static final int REQUEST_CODE = 123;

    // Implementation of the plugin.
    private final EmailComposerImpl impl = new EmailComposerImpl();

    // The callback context used when calling back into JavaScript
    private CallbackContext command;

    /**
     * Delete externalCacheDirectory on appstart
     *
     * @param cordova Cordova-instance
     * @param webView CordovaWebView-instance
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        impl.cleanupAttachmentFolder(getContext());
    }

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread.
     * To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments in JSON form.
     * @param callback The callback context used when calling
     *                 back into JavaScript.
     * @return         Whether the action was valid.
     */
    @Override
    public boolean execute (String action, JSONArray args,
                            CallbackContext callback) throws JSONException {

        this.args = args;
        this.command = callback;

        if ("open".equalsIgnoreCase(action)) {
            if (cordova.hasPermission(neededPermission)) {
                open(args);
            } else {
                command.success();
            }
            return true;
        }


        if ("isAvailable".equalsIgnoreCase(action)) {
            if(cordova.hasPermission(neededPermission)) {
                isAvailable(args.getString(0));
            } else {
                cordova.requestPermission(this, REQUEST_CODE_ISAVAILABLE, neededPermission);
                command.success();
            }
            return true;
        }

//        if ("isCallable".equalsIgnoreCase(action)) {
//            isCallable(args.getString(0));
//            return true;
//        }



        if("hasPermission".equalsIgnoreCase(action)) {
            hasPermission(neededPermission);
            return true;
        }


        if("requestPermission".equalsIgnoreCase(action)) {
            requestPermissions(REQUEST_CODE);
            return true;
        }

        return false;
    }

    /**
     * Returns the application context.
     */
    private Context getContext() { return cordova.getActivity(); }


    private void isAvailable(String uri) throws JSONException {
        JSONObject jsObject = new JSONObject();
        PackageManager packageManager = this.cordova.getActivity().getPackageManager();
        PackageManager pm = packageManager;
        boolean app_installed;
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);

            List<PluginResult> messages = new ArrayList<PluginResult>();
            messages.add(new PluginResult(PluginResult.Status.OK, true));
            messages.add(new PluginResult(PluginResult.Status.OK, true));

            PluginResult result = new PluginResult(
                    PluginResult.Status.OK, messages);
            try {
                Field field = result.getClass().getDeclaredField("encodedMessage");
                field.setAccessible(true);
                field.set(result, String.format("%b,%b", true, true));
            } catch (Exception e) {
                e.printStackTrace();
            }
            command.sendPluginResult(result);

        }catch (PackageManager.NameNotFoundException e) {

            List<PluginResult> messages = new ArrayList<PluginResult>();
            messages.add(new PluginResult(PluginResult.Status.OK, true));
            messages.add(new PluginResult(PluginResult.Status.OK, false));

            PluginResult result = new PluginResult(
                    PluginResult.Status.OK, messages);
            try {
                Field field = result.getClass().getDeclaredField("encodedMessage");
                field.setAccessible(true);
                field.set(result, String.format("%b,%b", true, false));
            } catch (Exception c) {
                e.printStackTrace();
            }
            command.sendPluginResult(result);
        }
    }




    /**
     * Sends an intent to the email app.
     *
     * @param args
     * The email properties like subject or body
     * @throws JSONException
     */
    private void open (JSONArray args) throws JSONException {
        JSONObject props = args.getJSONObject(0);
        String appId     = props.getString("app");

        if (!(impl.canSendMail(appId, getContext()))[0]) {
            LOG.i(LOG_TAG, "No client or account found for.");
            return;
        }

        Intent draft  = impl.getDraftWithProperties(props, getContext());
        String header = props.optString("chooserHeader", "Open with");

        final Intent chooser = Intent.createChooser(draft, header);
        final EmailComposer plugin = this;

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                cordova.startActivityForResult(plugin, chooser, 0);
            }
        });
    }

    public boolean hasPermission (String neededPermission) {
        Boolean hasPermission = cordova.hasPermission(neededPermission);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, hasPermission);
        command.sendPluginResult(result);
        return hasPermission;
    }

    public void requestPermissions (int requestCode) {

        cordova.requestPermission(this, REQUEST_CODE, neededPermission);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK);

        command.sendPluginResult(result);
    }


    /**
     * Called when an activity you launched exits, giving you the reqCode you
     * started it with, the resCode it returned, and any additional data from it.
     *
     * @param reqCode     The request code originally supplied to startActivityForResult(),
     *                    allowing you to identify who this result came from.
     * @param resCode     The integer result code returned by the child activity
     *                    through its setResult().
     * @param intent      An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     */
    @Override
    public void onActivityResult(int reqCode, int resCode, Intent intent) {
        if (command != null) {
            command.success();
        }
    }

    @Override
    public void onRequestPermissionResult (int requestCode, String[] permissions, int[] grantResults) throws JSONException {

        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED) {
                Log.d(LOG_TAG, "Permission denied");
                return;
                }
        }

        switch (requestCode) {
            case REQUEST_CODE_OPEN:
                open(this.args);
                break;
            case REQUEST_CODE_ISAVAILABLE:
                isAvailable(this.args.getString(0));
                break;

        }

     }


}