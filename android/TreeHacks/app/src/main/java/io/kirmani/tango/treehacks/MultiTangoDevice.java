/*
 * MultiTangoDevice.java
 * Copyright (C) 2016 kirmani <sean@kirmani.io>
 *
 * Distributed under terms of the MIT license.
 */

package io.kirmani.tango.treehacks;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

public class MultiTangoDevice {
    private String mUuid;
    private Vector3 mPosition;
    private Quaternion mOrientation;

    public MultiTangoDevice(String uuid) {
        mUuid = uuid;
    }

    public String getUuid() {
        return mUuid;
    }

    public void setPosition(Vector3 position) {
        mPosition = new Vector3(position);
    }

    public Vector3 getPosition() {
        return mPosition;
    }

    public void setOrientation(Quaternion orientation) {
        mOrientation = orientation;
    }

    public Quaternion getOrientation() {
        return mOrientation;
    }
}

