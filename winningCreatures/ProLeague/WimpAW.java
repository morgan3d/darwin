import java.awt.Point;

public class WimpAW extends Creature { 

    private static final int NEWBORN = 0;
    private static final int FINDING_WALL_E = 2;
    private static final int FINDING_WALL_S = 3;
    private static final int FINDING_CORNER_E = 4;
    private static final int FINDING_CORNER_S = 5;
    private static final int FLYTRAP_L = 6;
    private static final int FLYTRAP_R = 7;
    private static final int STATIONARY = 8;

    private int state;

    public void run() {
	while (true) {
	    switch (state) {
	    case NEWBORN: 
		newborn();
		break;
	    case FINDING_WALL_E:
		findingWallE();
		break;
	    case FINDING_WALL_S:
		findingWallS();
		break;
	    case FINDING_CORNER_E:
		findingCornerE();
		break;
	    case FINDING_CORNER_S:
		findingCornerS();
		break;
	    case FLYTRAP_L: 
		flytrapL();
		break;
	    case FLYTRAP_R:
		flytrapR();
		break;
	    case STATIONARY:
		stationary();
		break;
	    }
	}
    }

    public void newborn() {
	while (getDirection() != Direction.EAST) {
	    Observation obs = look();
	    int d = distance(obs.position) - 1;

	    if (obs.classId == APPLE_CLASS_ID) {
		if (moveForward(d)) {
		    attack();
		}
	    } else if (isEnemy(obs)) {
		if (d == 0) {
		    attack();
		}
	    }					       
	    turnRight();
	}
	state = FINDING_WALL_E;
    }

    public void findingWallE() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	moveForward(d);
	if (isEnemy(obs)) {
	    attack();
	} else if (obs.type == Type.CREATURE) {
	    turnRight();
	    state = FINDING_WALL_S;
	} else if (obs.type != Type.CREATURE) {
	    turnRight();
	    state = FINDING_CORNER_S;
	}
    }

    public void findingWallS() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	moveForward(d);
	if (isEnemy(obs)) {
	    attack();
	} else if (obs.type == Type.CREATURE) {
	    turnRight();
	    state = FLYTRAP_R;
	} else {
	    turnLeft();
	    state = FINDING_CORNER_E;
	}
    }

    public void findingCornerE() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	moveForward(d);
	if (isEnemy(obs)) {
	    attack();
	} else {
	    turnLeft();
	    state = FLYTRAP_L;
	}
    }

    public void findingCornerS() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	moveForward(d);
	if (isEnemy(obs)) {
	    attack();
	} else {
	    turnRight();
	    state = FLYTRAP_R;
	}
    }

    public void flytrapL() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	if (isEnemy(obs)) {
	    if (d == 0) {
		attack();
	    } else if (d <= 5) {
		return;
	    }
	} else if (d == 0 && obs.type != Type.CREATURE) {
	    turnLeft();
	    state = STATIONARY;
	} else {
	    turnLeft();
	    state = FLYTRAP_R;
	}
    }

    public void flytrapR() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	if (isEnemy(obs)) {
	    if (d == 0) {
		attack();
	    } else if (d <= 5) {
		return;
	    }
	} else if (d == 0 && obs.type != Type.CREATURE) {
	    turnRight();
	    state = STATIONARY;
	} else {
	    turnRight();
	    state = FLYTRAP_L;
	}
    }	

    public void stationary() {
	Observation obs = look();
	int d = distance(obs.position) - 1;
	if (isEnemy(obs) && d == 0) {
	    attack();
	}
    }

    public boolean moveForward(int n) {
	for (int i = 0; i < n; i++) {
	    if (!moveForward()) {
		attack();
		return false;
	    }
	}
	return true;
    }

    public String getDescription() {
	return "A kind of smart Flytrap.";
    }

    public String getAuthorName() {
	return "Alex Wheelock";
    }

} 
