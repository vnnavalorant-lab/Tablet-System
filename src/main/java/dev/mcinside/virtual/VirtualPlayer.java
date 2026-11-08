package dev.mcinside.virtual;

public final class VirtualPlayer extends VirtualBody {
    public static final double EYE = 1.62;
    private static final double SPEED = 4.3;

    public double yaw, pitch;

    public VirtualPlayer() { super(0.3, 1.8); }

    public void update(double dt, double forward, double strafe, boolean jump, VirtualChunkManager cm) {
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        double wx = sy * forward - cy * strafe;
        double wz = cy * forward + sy * strafe;
        double len = Math.hypot(wx, wz);
        if (len > 1) { wx /= len; wz /= len; }
        vx = wx * SPEED;
        vz = wz * SPEED;
        if (jump && onGround) vy = 9.4;
        step(dt, cm);
        if (y < -20) { y = cm.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 2; vy = 0; }
    }
}
