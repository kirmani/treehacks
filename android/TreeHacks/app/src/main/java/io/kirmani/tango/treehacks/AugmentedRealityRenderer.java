/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kirmani.tango.treehacks;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Very simple example augmented reality renderer which displays a cube fixed in place.
 * Whenever the user clicks on the screen, the cube is placed flush with the surface detected
 * with the depth camera in the position clicked.
 *
 * This follows the same development model than any regular Rajawali application
 * with the following peculiarities:
 * - It extends <code>TangoRajawaliArRenderer</code>.
 * - It calls <code>super.initScene()</code> in the initialization.
 * - When an updated pose for the object is obtained after a user click, the object pose is updated
 *   in the render loop
 * - The associated AugmentedRealityActivity is taking care of updating the camera pose to match
 *   the displayed RGB camera texture and produce the AR effect through a Scene Frame Callback
 *   (@see AugmentedRealityActivity)
 */
public class AugmentedRealityRenderer extends TangoRajawaliRenderer {
    private static final int SPHERE_DIVISIONS = 20;
    private static final float SPHERE_RADIUS = 0.5f;

    private Object3D mObject;
    private Pose mObjectPose;
    private boolean mObjectPoseUpdated = false;

    private HashMap<String, Object3D> mDevices;

    public AugmentedRealityRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Remember to call super.initScene() to allow TangoRajawaliArRenderer
        // to be set-up.
        super.initScene();

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        mDevices = new HashMap<String, Object3D>();
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Save the updated plane fit pose to update the AR object on the next render pass.
     * This is synchronized against concurrent access in the render loop above.
     */
    public synchronized void updateObjectPose(TangoPoseData planeFitPose) {
        mObjectPose = ScenePoseCalculator.toOpenGLPose(planeFitPose);
        mObjectPoseUpdated = true;
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     *
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
    }

    public void updateScene(Map<String, MultiTangoDevice> devices) {
        for (String uuid : devices.keySet()) {
            if (!mDevices.containsKey(uuid)) {
                Object3D object = new Cube(SPHERE_RADIUS);
                Material material = new Material();
                material.setColor(Color.RED);
                material.setColorInfluence(0.1f);
                material.enableLighting(true);
                material.setDiffuseMethod(new DiffuseMethod.Lambert());
                object.setMaterial(material);
                object.setPosition(devices.get(uuid).getPosition());
                object.setOrientation(devices.get(uuid).getOrientation());
                mDevices.put(uuid, object);
                getCurrentScene().addChild(object);
            } else {
                mDevices.get(uuid).setPosition(devices.get(uuid).getPosition());
                mDevices.get(uuid).setOrientation(devices.get(uuid).getOrientation());
            }
        }
        for (String uuid : mDevices.keySet()) {
            if (!devices.containsKey(uuid)) {
                getCurrentScene().removeChild(mDevices.get(uuid));
                mDevices.remove(uuid);
            }
        }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}
