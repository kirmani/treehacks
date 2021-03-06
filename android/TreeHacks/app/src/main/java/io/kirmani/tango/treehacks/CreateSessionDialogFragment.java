/*
 * CreateSessionDialogFragment.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class CreateSessionDialogFragment extends DialogFragment {
    private static final String TAG = CreateSessionDialogFragment.class
        .getSimpleName();

    public interface CreateSessionDialogListener {
        public void onCreateSessionDialogPositiveClick(DialogFragment dialog);
    }

    // Uset this instance of the interface to deliver action events
    CreateSessionDialogListener mListener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View view = inflater.inflate(R.layout.dialog_create, null);
        builder.setView(view)
            // Add action buttons
            .setPositiveButton(R.string.session_create_submit,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    EditText editText = (EditText)
                        view.findViewById(R.id.session_create);
                    String sessionId = editText.getText().toString();
                    Log.d(TAG, String.format(
                                "Attempting to create session with ID: %s",
                                sessionId));
                    HttpTangoUtil.getInstance(getActivity()).createSession(sessionId);
                }
            });
        return builder.create();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiates the CreateSessionDialogListener so we can send events to the host
            mListener = (CreateSessionDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement CreateSessionDialogListener");
        }
    }
}
