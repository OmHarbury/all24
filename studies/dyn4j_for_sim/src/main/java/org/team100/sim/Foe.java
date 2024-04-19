package org.team100.sim;

import java.util.List;

import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Geometry;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.DetectFilter;
import org.dyn4j.world.World;

/**
 * Foes try to pick from the source and score in the amp corner.
 * 
 * TODO: add defensive behavior
 * TODO: add two kinds of scoring
 */
public class Foe extends RobotBody {
    private static final int kForce = 200;
    private static final int kTolerance = 2;
    private static final Vector2 kSource = new Vector2(0, 0);
    private static final Vector2 kAmpCorner = new Vector2(16, 8);

    private enum Goal {
        PICK, SCORE
    }

    private final World<Body100> m_world;
    private Goal m_goal;

    public Foe(World<Body100> world) {
        m_world = world;
        // initial goal
        m_goal = Goal.PICK;
    }

    @Override
    public void act() {
        Vector2 position = getWorldCenter();
        switch (m_goal) {
            case PICK:
                Vector2 toPick = position.to(kSource);
                if (toPick.getMagnitude() < kTolerance) {
                    // successful pick, now go score
                    m_goal = Goal.SCORE;
                } else {
                    // keep trying
                    applyForce(toPick.setMagnitude(kForce));
                }
                break;
            case SCORE:
                Vector2 toScore = position.to(kAmpCorner);
                if (toScore.getMagnitude() < kTolerance) {
                    // successful score, now go pick
                    m_goal = Goal.PICK;
                } else {
                    // keep trying
                    applyForce(toScore.setMagnitude(kForce));
                }
                break;
        }

        // look for nearby notes, brute force
        for (Body100 body : m_world.getBodies()) {
            if (body instanceof Note) {
                double distance = getTransform().getTranslation().distance(
                        body.getTransform().getTranslation());
                if (distance > 0.3)
                    continue;
                System.out.printf("%s %5.3f\n",
                        body.getClass().getSimpleName(), distance);
                // TODO: pick up?
            }
        }
    }
}
