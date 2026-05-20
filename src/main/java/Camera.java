package org.example;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    public Vector3f position;
    private float yaw   = -90.0f;
    private float pitch =   0.0f;

    public float velocityY  = 0;
    public boolean onGround = false;

    private static final float SPEED       = 8.0f;
    private static final float SENSITIVITY = 0.1f;
    private static final float GRAVITY     = -25.0f;
    private static final float JUMP_FORCE  = 12.0f;

    public Camera(float x, float y, float z) {
        position = new Vector3f(x, y, z);
    }

    public void moveForward(float dt) {
        position.add(getForwardFlat().mul(SPEED * dt));
    }
    public void moveBackward(float dt) {
        position.sub(getForwardFlat().mul(SPEED * dt));
    }
    public void moveLeft(float dt) {
        position.sub(getRight().mul(SPEED * dt));
    }
    public void moveRight(float dt) {
        position.add(getRight().mul(SPEED * dt));
    }

    public void jump() {
        if (onGround) {
            velocityY = JUMP_FORCE;
            onGround  = false;
        }
    }

    public void applyGravity(float dt) {
        velocityY  += GRAVITY * dt;
        position.y += velocityY * dt;
    }

    public void rotate(float dx, float dy) {
        yaw   += dx * SENSITIVITY;
        pitch -= dy * SENSITIVITY;
        if (pitch >  89) pitch =  89;
        if (pitch < -89) pitch = -89;
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(
                position,
                new Vector3f(position).add(getForward()),
                new Vector3f(0, 1, 0)
        );
    }

    public Vector3f getForward() {
        float yr = (float)Math.toRadians(yaw);
        float pr = (float)Math.toRadians(pitch);
        return new Vector3f(
                (float)(Math.cos(yr) * Math.cos(pr)),
                (float)(Math.sin(pr)),
                (float)(Math.sin(yr) * Math.cos(pr))
        ).normalize();
    }

    private Vector3f getForwardFlat() {
        float yr = (float)Math.toRadians(yaw);
        return new Vector3f(
                (float)Math.cos(yr), 0, (float)Math.sin(yr)
        ).normalize();
    }

    private Vector3f getRight() {
        return getForwardFlat().cross(new Vector3f(0, 1, 0)).normalize();
    }

    public float getYaw() { return yaw; }
}