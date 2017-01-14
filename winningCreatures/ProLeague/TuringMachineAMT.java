import java.util.*;
import java.awt.Point;
import java.awt.Dimension;

/** A creature within the Darwin simulator API that supports the helper methods to win a significant majority of Natural Selection competitions against Pirate, Rover, SuperRover, and Flytrap.
    <p>Hai Zhou
    <br>hz1@cs.williams.edu
    <br>5/1/2011
*/
public class TuringMachineAMT extends Creature {

    static private ObservationArray2D map;

    static private Point target = new Point();

    static private Boolean targetExist = false;

    static private long targetSetTime;    

    static private int height;

    static private int width;

    static private Random r = new Random();

    static private final int MAX_LOCKEDCOUNT = Integer.MAX_VALUE;

    private Point lastChasePosition = new Point();

    private Boolean locked = false;

    private int lockedCount = 0;


    /** Updates direction */
    public void turnLeft() {
	update();
	super.turnLeft();
    }

    /** Updates direction */
    public void turnRight() {
	update();
	super.turnRight();
    }

    /** Turns to face in the opposite of the current direction.*/
    public void turn180() {
	turnRight();
	turnRight();
    }


    /** Turns to face in direction d.*/
    public void turn(Direction d) {
	Direction current = getDirection();

	if (d == current.opposite()) {
	    turn180();
	} else if (d == current.left()) {
	    turnLeft();
	} else if (d == current.right()) {
	    turnRight();
	}
    }


    /** Turns left or right with equal probability.*/
    public void turn90Random() {
	int i = r.nextInt(2);

	switch (i) {
	case 0:
	    turnLeft();
	    break;

	case 1:
	    turnRight();
	    break;
	}
    }


    /** Returns the direction to face to move towards p along the axis with the largest difference.*/
    public Direction directionTo(Point p) {
	Point current = getPosition();

	assert ((current.x == p.x) || (current.y == p.y))
	    && ((current.x != p.x) && (current.y != p.y)) 
	    : "Point invalid.";

	if (current.x == p.x) {
	    if (p.y > current.y) {
		return Direction.SOUTH;
	    } else if (p.y < current.y) {
		return Direction.NORTH;
	    } else {
		return null;
	    }

	} else if (current.y == p.y) {
	    if (p.x > current.x) {
		return Direction.EAST;
	    } else if (p.x < current.x) {
		return Direction.WEST;
	    } else {
		return null;
	    }

	} else {
	    return null;
	}

    }


    /** Turns to face the direction needed to walk towards p.*/
    public void turn(Point p) {
	turn(directionTo(p));
    }


    /** Moves forward n spaces. If obstructed, immediately stops trying to move and returns false. Returns true if successfully moved this distance.*/
    public boolean moveForward(int n) {
	for (int i = 0; i < n; ++i) {
	    if (!fastMoveForward()) {
		attack();
		return false;
	    }
	}

	return true;
    }


    /** Moves forward without looking */
    public boolean fastMoveForward() {
	Point p = getPosition();

	boolean f = super.moveForward();

	long t = getGameTime();
	// empty square
	synchronized (map) {	    
	    map.set(new Observation(p, t));
	}
	// self position
	update();

	return f;
    }


    /** Moves forward 1 square. If the creature is blocked, it will not move and return false.*/
    public boolean moveForward() {
	// check enemy
	check();

	Point p = getPosition();

	boolean f = super.moveForward();

	long t = getGameTime();
	// empty square
	synchronized (map) {	    
	    map.set(new Observation(p, t));
	}
	// self position
	update();

	return f;
    }


    /** Moves backward 1 square. If the creature is blocked, it will not move and return false.*/
    public boolean moveBackward() {
	Point p = getPosition();

	boolean f = super.moveBackward();

	long t = getGameTime();
	synchronized (map) {
	    map.set(new Observation(p, t));
	}
	update();

	return f;
    }


    /** Updates the states of the creature.*/
    public void update() {
	synchronized (map) {
	    map.set(observeSelf());
	}
    }


    /** Set target.*/
    public void setTarget(Point p) {
	synchronized (targetExist) {
	    synchronized (target) {
		if (!targetExist) {
		    targetExist = true;
		    target = p;
		    targetSetTime = getGameTime();
		}
	    }
	}
    }


    /** Returns the most recent observation of point p by any instance of the creature class. If this point has never been observed, returns null.*/
    public Observation recall(Point p) {
	return map.get(p);
    }


    /** Displays the shared understanding of the current map.*/
    public String toString() {
	if (map == null) {
	    return "targetExist: " + targetExist + "\n" + target;
	} else {
	    synchronized (map) {
		return map.toString() + "\ntargetExist: " + targetExist + "\n" + target;
	    }
	}
    }


    static private int sign(int x) { 
	if (x > 0) { 
	    return 1; 
	} else if (x == 0) { 
	    return 0; 
	} else {
	    return -1; 
	} 
    } 


    /** Updates the shared map. Using the code from the lab handout.*/
    public Observation look() {
	Observation obs = super.look();

	Point me = getPosition(); 
        Point it = obs.position; 

        int dist = distance(it) - 1; 

        // Only one of these is non-zero
	int dx = sign(it.x - me.x); 
        int dy = sign(it.y - me.y); 

	int x = me.x + dx; 
        int y = me.y + dy; 

        long t = getGameTime(); 

	synchronized (map) {
	    // Lots of empty squares
	    for (int i = 0; i < dist; ++i) { 
		map.set(new Observation(x, y, t));
		x += dx; y += dy; 
	    } 
	    
	    // And one non-empty
	    map.set(obs);
	}

	return obs;
    }


    /** Check whether the result of look is enemy.*/
    private void check() {
	if (recall(getMovePosition()) == null ||
	    recall(getMovePosition()).classId != myClassId()) {
	    Observation obs = look();
	    int d = distance(obs.position);

	    // if enemy is found then set target and chase
	    if (isEnemy(obs)) {
		if (d == 1) {
		    attack();
		} else if (obs.classId != APPLE_CLASS_ID &&
			   obs.direction == getDirection().opposite() &&
			   d < 3) {
		    setTarget(obs.position);
		    Observation myBack = recall(getDirection().opposite().forward(getPosition()));
		    if (myBack != null &&
			myBack.classId == myClassId() &&
			myBack.direction == getDirection()) {
			moveForward(1);
			attack();
		    } else {
			delay();
			check();
		    }
		} else if (obs.direction == getDirection()) {
		    setTarget(obs.position);
		    moveForward(d);
		} else {
		    setTarget(obs.position);
		}
	    }
	}
    }
	

    /** Wander and search for enemy.*/
    private void wander() {
	Observation ahead = recall(getMovePosition());
	Observation left = recall(getDirection().left().forward(getPosition()));
	Observation right = recall(getDirection().right().forward(getPosition()));

	// the forward square not in memory
	if (ahead == null) {
	    check();
	} else if (ahead.type == Type.WALL ||
		   ahead.type == Type.THORN) {
	    // turn when wall or thorn is forward
	    turn90Random();
	} else if (ahead.classId == myClassId()) {
	    if (ahead.direction == getDirection()) {
		delay();
	    } else if (r.nextInt(2) > 0) {
		turn90Random();
	    } else {
		delay();
	    }
	} else if (left == null) {
	    turnLeft();
	    check();
	    turnRight();
	} else if (right == null) {
	    turnRight();
	    check();
	    turnLeft();
	} else if (right == null) {
	    turnRight();
	    check();
	}else if (!moveForward()) {
	    check();
	}
    }


    /** Called in goTo method to besiege the enemy.*/
    private void standby() {
	Observation obs = look();
	int d = distance(obs.position);

	Boolean friendAhead =
	    d == 1 &&
	    (obs.classId == myClassId() ||
	     obs.type == Type.WALL ||
	     obs.type == Type.THORN);

	Observation myLeft = recall(getDirection().left().forward(getPosition()));
	Boolean friendLeft =
	    myLeft != null &&
	    (myLeft.classId == myClassId() ||
	     myLeft.type == Type.WALL ||
	     myLeft.type == Type.THORN);

	Observation myRight = recall(getDirection().right().forward(getPosition()));
	Boolean friendRight =
	    myRight != null &&
	    (myRight.classId == myClassId() ||
	     myRight.type == Type.WALL ||
	     myRight.type == Type.THORN);

	if (isEnemy(obs)) {
	    if (d == 1) {
		attack();
	    } else if (obs.direction == getDirection().opposite()) {
		delay();
	    }
	    setTarget(obs.position);

	} else if ((friendAhead && friendLeft && friendRight) ||
		   (!friendAhead && !friendLeft && !friendRight)) {
	    if (r.nextInt(3) > 0) {
		turn90Random();
	    } else {
		delay();
	    }

	} else if (!friendAhead &&
		   friendLeft &&
		   friendRight) {
	    delay();
	    
	} else if (friendAhead &&
		   !friendLeft &&
		   friendRight) {
	    turnLeft();

	} else if (friendAhead &&
		   friendLeft &&
		   !friendRight) {
	    turnRight();

	} else if (friendAhead &&
		   !friendLeft &&
		   !friendRight) {
	    turn90Random();

	} else if (!friendAhead &&
		   friendLeft &&
		   !friendRight) {
	    if (r.nextInt(2) > 0) {
		turnRight();
	    } else {
		delay();
	    }

	} else if (!friendAhead &&
		   !friendLeft &&
		   friendRight) {
	    if (r.nextInt(2) > 0) {
		turnLeft();
	    } else {
		delay();
	    }	    

	} else {
	    turn90Random();
	}
    }


    /** Using a linkedlist to record the path from current position to the
	destination, and move to the point.*/
    private void goTo(Point p) {
	LinkedList<Direction> steps;
	synchronized (map) {
	    steps = map.findPath(getPosition(), p, height * width / 2, locked);
	}

	// path found
	if (steps != null) {
	    if (!steps.isEmpty()) {
		turn(steps.getFirst());
		steps.removeFirst();
	    }

	    // following the path
	    while ((!steps.isEmpty()) && moveForward()) {
		turn(steps.getFirst());
		steps.removeFirst();
	    }
	    
	    // check whether target is still there
	    wander();

	    if (!lastChasePosition.equals(getPosition())) {
		locked = false;
		lastChasePosition = getPosition();
	    } else if (!steps.isEmpty()) {
		++lockedCount;
	    }

	    if (lockedCount > MAX_LOCKEDCOUNT) {
		locked = true;
		lockedCount = 0;
	    }

	    long t0 = getGameTime();
	    while (!targetExist) {
		standby();
		if (getGameTime() - t0 > 2 * Simulator.SECONDS) {
		    break;
		}
	    }

	} else {
	    // path not found
	    wander();
	}
    }


    public void run() {
	// initialize the map
        synchronized (ObservationArray2D.ring) {
            if (map == null) {
                map = new ObservationArray2D(getMapDimensions(), myClassId());
		height = getMapDimensions().height;
		width = getMapDimensions().width;
            }
        }

	update();
	if (recall(getMovePosition()) != null &&
	    recall(getMovePosition()).classId == myClassId() &&
	    recall(getMovePosition()).direction.opposite() == getDirection()) {
	    turn90Random();
	}

	while (true) {
	    update();
	    check();

	    // the first to arrive should clear the target
	    synchronized (targetExist) {
		synchronized (target) {
		    if (getPosition().equals(target) ||
			getGameTime() - targetSetTime > 2 * Simulator.SECONDS) {
			targetExist = false;
			target = new Point();
		    }
		}
	    }

	    // go to the target
	    if (targetExist) {
		goTo(target);
		    
	    } else {
		// no target
		wander();
	    }
	}

    }


    public void onDeath() {
	// convert the creature in the map
	Observation o = new Observation(getPosition(), getGameTime());
	synchronized (map) {
	    map.set(o);
	}

	// set converted creature to be the target if not existed
	setTarget(o.position);

	super.onDeath();
    }


    /** Allows GUI browsers to display your name as author of this creature.*/
    public String getAuthorName() {
	return "Alan Turing";
    }

    /** Allows GUI browsers to display information and credits about your creature.*/
    public String getDescription() {
	return "A creature within the Darwin simulator API that supports the helper methods to win a significant majority of Natural Selection competitions against Pirate, Rover, SuperRover, and Flytrap.";
    }


    /** 2D array useful for making maps of the game board */ 
    protected static class Array2D<T> { 
	private T[][] array;
	private int width, height;

	@SuppressWarnings("unchecked")
	private T[][] allocateArray(int x, int y) {
	    return (T[][]) new Object[x][y];
	}

	public Array2D(Dimension d) {
	    width = d.width;
	    height = d.height;
	    array = allocateArray(width, height);
	}

	public T get(Point p) {
	    return array[p.x][p.y];
	}

	public T get(int x, int y) {
	    return array[x][y];
	}

	public void set(int x, int y, T v) {
	    array[x][y] = v;
	}

	public void set(Point p, T v) {
	    array[p.x][p.y] = v;
	}

	public int getWidth() {
	    return width;
	}

	public int getHeight() {
	    return height;
	}

	public boolean inBounds(Point p) {
	    return (p.x >= 0) && (p.x < width) && (p.y >= 0) && (p.y < height);
	}

	public boolean inBounds(int x, int y) {
	    return (x >= 0) && (x <= width) && (y >= 0) && (y <= height);
	}
	
	/** Generates a picture of the contents, using toString(T) to generate the value for an  
	    individual square */ 
	public String toString() {
	    String result = "";

	    for (int j = 0; j < height; ++j) {
		for (int i = 0; i < width; ++i) {
		    result = result + toString(array[i][j]);
		}
		result = result + "\n";
	    }

	    return result;
	}
	
	/** Override this to change how the map is rendered. */ 
	protected String toString(T t) {
	    return toString(t);
	}
    }  


    /** Array of what has been seen */ 
    protected static class ObservationArray2D extends Array2D<Observation> { 
	private int myId;
        final public static Integer ring = new Integer(-1);
	
	public ObservationArray2D(Dimension d, int myClassId) {
	    super(d);
	    myId = myClassId;
	}
	
	/** Record the observation at the location where it applies only.*/ 
	public void set(Observation obs) {
	    Point p = obs.position;

	    super.set(p, obs);
	}
	
	/** Uses the map symbols to display the observation. E.g., EMPTY = " ", WALL = "X", etc. 
	    shows my class as "m" and other creatures as "c" unless they are Apples or Flytraps.*/ 
	@Override
	protected String toString(Observation obs) {
	    if (obs == null) {
		return "?";
	    } else {
		Type t = obs.type;
		
		if (t == Type.CREATURE) {
		    int id = obs.classId;
		    
		    if (id == APPLE_CLASS_ID) {
			return "a";
		    } else if (id == FLYTRAP_CLASS_ID) {
			return "f";
		    } else if (id == TREASURE_CLASS_ID) {
			return "*";
		    } else if (id == myId) {
			return "m";
		    } else {
			return "c";
		    }
		} else if (t == Type.EMPTY) {
		    return " ";
		} else if (t == Type.WALL) {
		    return "X";
		} else if (t == Type.THORN) {
		    return "+";
		} else {
		    return "?";
		}
	    }
	}


	/** Using the Breadth-First search algorithm to find the path from p0
	    to p1.*/
	public LinkedList<Direction> findPath(Point p0, Point p1, int n, Boolean locked) {
	    // record the visited points
	    HashSet<Point> visited = new HashSet<Point>();

	    // using the queue to perform breadth-first search
	    Queue<Point> queue = new LinkedList<Point>();

	    // using linkedlist to save the path of direction
	    LinkedList<Direction> path = new LinkedList<Direction>();

	    // using hashmap to save how a point is visited
	    HashMap<Point, Direction> track = new HashMap<Point, Direction>();
	    
	    queue.offer(p0);
	    track.put(p0, null);
	    
	    int times = 0;
	    
	    // search for n steps
	    while (!queue.isEmpty() && times < n) {
		Point current = queue.remove();
		if (current.equals(p1)) {
		    // track back the route
		    Direction back = track.get(current);
		    
		    while (back != null) {
			// reverse the direction for the path
			path.addFirst(back);
			
			// backtrack
			current = back.opposite().forward(current);
			back = track.get(current);
		    }
		    return path;
		}
		
		visited.add(current);
		// search all directions
		Direction[] directions = Direction.values();
		Collections.shuffle(Arrays.asList(directions));
		for (Direction d : directions) {
		    Point next = d.forward(current);

		    if ((get(next) != null) &&
			// the next square is visitable
			(get(next).type == Type.EMPTY ||
			 (!locked && get(next).type == Type.CREATURE)) &&

			// the next square is not in the visited set
			(!visited.contains(next))) {

			queue.offer(next);
			track.put(next, d);
		    }
		}
		
		++times;
	    }

	    // path not found
	    return null;
	}

    }

}
