//Josh Geller and Cotton Engleby
import java.util.*;
import java.awt.*;

public class TheRealBamf extends Creature {
    private Random rand;
    private boolean clumped;
    private boolean[] friends;
    private String str = "";
    //    static boolean[][] aTest;
    static int classId = UNINITIALIZED_CLASS_ID;;

    public void run() {
	if (classId == UNINITIALIZED_CLASS_ID) {
	    classId = myClassId();
	    //  aTest = new boolean[getMapDimensions().width][getMapDimensions().height];
	}
	Point p = getPosition();
	//	aTest[p.x][p.y] = true;
	rand = new Random();
	friends = new boolean[4];
	for (int i = 0; i < 3; i++) {
	    friends[i] = false;
	}
	clumped = false;
	while (true) {
	    Observation o = look();
	    int d = distance(o.position);
	    if (!clumped) {
		if (o.classId == classId) {
		    moveForward(d - 1);
		    clumped = true;
		    str += "clumped";
		}
		else if (o.classId == APPLE_CLASS_ID) {
		    moveForward(d - 1);
		    attack();
		}
		else if (o.classId == FLYTRAP_CLASS_ID) {
		    moveForward(d - 2);
		    Observation o2 = look();
		    Direction dir = getDirection();
		    while (!o2.direction.equals(dir)) {
			delay();
		    }
		    moveForward();
		    attack();
		}
		else if (isEnemy(o)) {
		    if (d > 2) {
			moveForward();
		    }
		    else if (d == 2) {
			delay();
		    }
		    else {
			attack();
		    }
		}
		else if (d == 1) {
		    int r = rand.nextInt(2);
		    if (r == 1) {
			turnLeft();
		    }
		    else {
			turnRight();
		    }
		}
		else {
		    moveForward();
		}
	    }
	    else {
		//		update();
		int dir = getDirection().toInt();
		//if (d == 1 && o.classId == classId) {
		    //		    aTest[o.position.x][o.position.y] = true;
		//  friends[dir] = true;
		//  int r = rand.nextInt(2);
		//  if (r == 0) {
		//turnLeft();
		//  }
		//  else {
		//turnRight();
		//  }
		// }
		
		if (d == 1 && (o.classId == classId || o.classId == THORN_CLASS_ID || o.classId == WALL_CLASS_ID) && !friends[dir]) {
		    friends[dir] = true;
		    // aTest[o.position.x][o.position.y] = true;
		}
		else if (d == 1 && isEnemy(o)) {
		    attack();
		}
		else if (isEnemy(o)) {
		    moveForward((d - 2) / 2);
		    delay();
		    attack();
		    moveBackward((d - 2) / 2);
		}
		else {
		    if (!friends[(dir + 1) % 4]) {
			turnLeft();
		    }
		    else if (!friends[(dir + 3) % 4]) {
			turnRight();
		    }
		    else if (!friends[(dir + 2) % 4]) {
			turnRight();
			turnRight();
		    }
		}
	    }
	}
    }

    /**
    private void update() {
	Point p = getPosition();
      	friends[0] = aTest[p.x + 1][p.y];
	friends[1] = aTest[p.x][p.y - 1];
	friends[2] = aTest[p.x - 1][p.y];
	friends[3] = aTest[p.x][p.y + 1];
    }
    */
    public void onDeath() {
      	setEmpty();
    }
    
    private void setFull() {
	Point p = getPosition();
	//	aTest[p.x][p.y] = true;
    }

    private void setEmpty() {
	Point p = getPosition();
	//	aTest[p.x][p.y] = false;
    }

    public boolean moveForward() {
	setEmpty();
	boolean moved = super.moveForward();
	setFull();
	return moved;
    }

    public boolean moveBackward() {
	setEmpty();
	boolean moved = super.moveBackward();
	setFull();
	return moved;
    }

    public boolean moveForward(int i) {
	for (int j = 0; j < i; j++) {
	    if (!moveForward()) {
		attack();
		return false;
	    }
	}
	return true;
    }

    public boolean moveBackward(int i) {
	for (int j = 0; j < i; j++) {
	    if (!moveForward()) {
		attack();
		return false;
	    }
	}
	return true;
    }

    public String toString() {
	return str;
    }

    public String getDescription() {
	return "A simple test creature";
    }

    public String getAuthorName() {
	return "Josh Geller and Cotton Engleby";
    } 
}