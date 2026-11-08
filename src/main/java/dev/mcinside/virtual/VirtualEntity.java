package dev.mcinside.virtual;

import java.util.Random;

/** Criatura simples que vaga pelo terreno (caixa rosa). */
public final class VirtualEntity extends VirtualBody {
    private double timer;
    private boolean moving;
    private double heading;

    public VirtualEntity(double x, double y, double z) {
        super(0.4, 0.8);
        this.x = x; this.y = y; this.z = z;
    }

    public void update(double dt, VirtualChunkManager cm, Random rng) {
        timer -= dt;
        if (timer <= 0) {
            timer = 1 + rng.nextDouble() * 3;
            moving = rng.nextDouble() > 0.4;
            heading = rng.nextDouble() * Math.PI * 2;
        }
        vx = moving ? Math.sin(heading) * 1.0 : 0;
        vz = moving ? Math.cos(heading) * 1.0 : 0;
        if (moving && blockedH && onGround) vy = 7.5;
        step(dt, cm);
    }
}
