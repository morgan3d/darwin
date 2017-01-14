import java.util.*;
import java.awt.Point;
import java.awt.Dimension;

/** A dumb creature.
    <p>Hai Zhou
    <br>hz1@cs.williams.edu
    <br>4/20/2011
*/
public class DonaldHZ extends Creature {

    public void run() {
	while (true) {
	    if (!moveForward()) {
		attack();
	    }
	    turnRight();
	}
    }


    /** Allows GUI browsers to display your name as author of this creature.*/
    public String getAuthorName() {
	return "Hai Zhou";
    }

    /** Allows GUI browsers to display information and credits about your creature.*/
    public String getDescription() {
	return "A dumb creature.";
    }
}
