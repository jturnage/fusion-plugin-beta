package com.fusionetics.plugins.bodymap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;


public class FragmentConfirmationDialog
{
    private static final String FRAGMENT_DIALOG = "dialog";

    public static void Show(FragmentManager _fragmentManager, String _message, Runnable _okFunc, Runnable _noFunc) {

        final String _messageInner = _message;
        final Runnable _okFuncInner = _okFunc;
        final Runnable _noFuncInner = _noFunc;

        DialogFragment dlg = new DialogFragment() {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return new AlertDialog.Builder(getActivity())
                        .setMessage(_messageInner)
                        .setPositiveButton(
                            "OK" /*android.R.string.ok*/, 
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if(_okFuncInner != null) {
                                        _okFuncInner.run();
                                    }
                                }
                        })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if(_noFuncInner != null) {
                                            _noFuncInner.run();
                                        }
                                    }
                                })
                        .create();
            }

        };
        dlg.show(_fragmentManager, FRAGMENT_DIALOG);
    }
}

