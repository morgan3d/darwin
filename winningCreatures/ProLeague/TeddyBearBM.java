import java.util.*;
import java.awt.Point;
import java.awt.Dimension;

/** A creature evolved from Flytrap with advanced abilities such as vision and memory.
    <p>Hai Zhou
    <br>hz1@cs.williams.edu
    <br>5/1/2011
*/
public class TeddyBearBM extends Creature {

    static private Random r = new Random();


    /** Turns left or right with equal probability.*/
    public void turn90Random() {
	switch (r.nextInt(2)) {
	case 0:
	    turnLeft();
	    break;

	case 1:
	    turnRight();
	    break;
	}
    }


    public void run() {
	while (true) {
	    Observation obs = look();
	    if (isEnemy(obs)) {
		int d = distance(obs.position);
		if (obs != null &&
		    obs.classId == APPLE_CLASS_ID) {
		    moveForward(d - 1);
		    attack();
		} else {
		    if (d == 1) {
			attack();
		    } else if (obs.direction == getDirection().opposite()) {
			delay();
		    }
		}
	    } else {
		turn90Random();
	    }
	}
    }


    public String toString() {
	return "Miawoo~";
    }


    /** Allows GUI browsers to display your name as author of this creature.*/
    public String getAuthorName() {
	return "Bunny Miaw";
    }

    /** Allows GUI browsers to display information and credits about your creature.*/
    public String getDescription() {
	return "A creature evolved from Flytrap with advanced abilities such as vision and memory.";
    }
}
