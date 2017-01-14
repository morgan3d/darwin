import java.awt.*;
import java.util.*;


public class Maximus extends Creature {
    // Node class for the points in the maze for pathfinding.
    class Node implements Comparable<Node> {
	
	// The point this node represents, the distance to the node, the
	// previous ndoe that was before it, and the direction taken to get to
	// this node.
	Point location;
	int distance;
	Node previous;
	Direction dir;
	
	public Node(Point pos, int distanceTwo, Node previousNode, Direction previousdir) {
	    location = pos;
	    distance = distanceTwo;
	    previous = previousNode;
	    dir =  previousdir;
	}
	
	//Checks which path is closer to a node.
	public int compareTo(Node other){
	    return distance - other.distance;
	}
    }
    
    protected String s = "";
    protected int exploreCount = 0;
    public static int creatureCount;

    protected Random generator = new Random();
    public static int SELF;
    public static int[][] maze;
    public static int action = 0;
    public static int height;
    public static int width;

    public static final int UNKNOWN = 0;
    public static final int SPACE = 1;
    public static final int WALL = 2;
    public static final int ENEMY = 3;
    public static final int US = 4;
    public static final int APPLE = 5;
    public static final int THORN = 6;
    public static final int FLYTRAP = 7;
    public static final int CLUMP = 8; 

    public static final int EXPLORE = 0;
    public static final int PURSUE = 1;
    public static final int HIDE = 2;

    public void run() {

	// Creates the maze. Only does it if hasn't been created yet
	if (maze == null) {
	    SELF = getClassId();
	    creatureCount = 0;
	    Dimension temp = getMapDimensions();

	    // Stores the values for teh height and width of the map.
	    height = temp.height;
	    width = temp.width;
	    
	    // Sets the maze to all unknowns.
	    maze = new int[width][height];
	    for (int i = 0; i < width; i++) {
		for (int j = 0; j < height; j++) {
		    if ((i == 0 || i == width - 1) ||
			(j == 0 || j == height - 1)) {
			maze[i][j] = WALL;
		    } else {
			maze[i][j] = UNKNOWN;
		    }
		}
	    }
	}
	
	// Sets the initial position to a space.
	maze[getPosition().x][getPosition().y] = US;
	creatureCount++;

	// Rotates around in the beginning, checking its surroundings.
	Observation o;
	for (int i = 0; i < 4; i++) {
	    o = addLook();
	    checkObs(o);
	    turnRight();
	}
	// Starts off by checking the checkstate.
	while (true) {
	    
	    checkState();
	    
	    
	}

    }
    
    // Checks if there is somewhere near the creature that is calling for a clump.
    public void pursue() {
	Point p = getPosition();
	
	// Checks the spaces within a 20 by 20 square for any possible clumps.
	for (int i = Math.max(0, p.x-10); i < Math.min(p.x + 10, width); i++) {
	    for (int j = Math.max(0, p.y-10); j < Math.min(p.y + 10, height); j++) {
		// If a spot wants to be clumped at, then it pathfinds its way
		// there.
		if (maze[i][j] == CLUMP) {
		    followPath(new Point(i, j));
		    Observation o = addLook();
		    face(o.direction);
		    clump();
		    return;
		}
	    }
	}
    }

    // Clumps at a spot when an enemy is seen.
    public void clump() {
	Direction d = getDirection().right();
	
	// Goes through the spaces around the creature, calling
	// for other creatures to join it there.
	for (int i = 0; i < 3; i++){
	    Point p = d.forward(getPosition());
	    if (maze[p.x][p.y] == SPACE) {
		maze[p.x][p.y] = CLUMP;
	    }
	    d = d.right();
	}
	Observation o = addLook();
	
	// While it still sees an enemy, it continues to clump.
	while (isEnemy(o)) {
	    o = addLook();
	    // If it sees an enemy one away, it attacks.
	    if (isEnemy(o)) {
		if (distance(o.position) == 1) {
		    attack();
		}		
	    } else {
		//  Otherwise it changes the spaces near it to spaces, 
		// and starts exploring.
		d = getDirection().right();
		for (int i = 0; i < 4; i++){
		    Point p = d.forward(getPosition());
		    if (maze[p.x][p.y] == CLUMP) {
			maze[p.x][p.y] = SPACE;
		    }
		    
		    d = d.right();
		}
		
		action = EXPLORE;

		return;
	    }
	}
    }
  
    
    public void earlyExplore() {
	
	Observation o = addLook();
	// Goes up to the thorns or wall, and examines left and right of it constantly, checking if 
	// there is anything interesting nearby.
	if ((o.classId == WALL_CLASS_ID) || (o.classId == THORN_CLASS_ID)) {
	    
	    int dist = distance(o.position) - 1;
	    // Moves forward, checks left and right, and then continues on.
	    for (int i = 0; i < dist; i++) {
		stepForward();
		turnLeft();
		o = addLook();
		checkObs(o);
		turnRight();
		turnRight();
		o = addLook();
		checkObs(o);
		turnLeft();
	    }
	    checkSides();
	  
	} else {
	    checkObs(o);
	}
	exploreCount++;
		
    }

    // checks the sides
    private void checkSides() {

	boolean r = checkRight(getDirection());
	boolean l = checkLeft(getDirection());
	if (r && l) {
	    randomTurn();
	} else if (r) {
	    turnRight();
	} else if (l) {
	    turnLeft();
	} else {
	    // can't turn either left or right
	    turnLeft();
	    turnLeft();
	    stepForward();
	    checkSides();
	}
    }
    
    // return if can travel right
    private boolean checkRight(Direction d) {
	Direction right = d.right();
	int x = getPosition().x;
	int y = getPosition().y;

	if (right == Direction.EAST) {
	    if (maze[x+1][y] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else if (right == Direction.WEST) {
	    if (maze[x-1][y] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else if (right == Direction.NORTH) {
	    if (maze[x][y-1] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else {
	    // south
	    if (maze[x][y+1] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	}
	return false;
    }

    // return if can travel left
    private boolean checkLeft(Direction d) {
	Direction left = d.left();
	int x = getPosition().x;
	int y = getPosition().y;

	if (left == Direction.EAST) {
	    if (maze[x+1][y] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else if (left == Direction.WEST) {
	    if (maze[x-1][y] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else if (left == Direction.NORTH) {
	    if (maze[x][y-1] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	} else {
	    // south
	    if (maze[x][y+1] == SPACE || maze[x+1][y] == UNKNOWN) {
		return true;
	    }
	}
	return false;
    }

    // Checks for the unknown spots in a maze with a space next to them,
    // and then explores it.
    public void lateExplore() {
	
	Point goTo = findUnknown(getPosition());
	followPath(goTo);
	int x = getPosition().x;
	int y = getPosition().y;
	Direction d;
	if (maze[x+1][y] == UNKNOWN) {
	    d = Direction.EAST;
	} else if (maze[x-1][y] == UNKNOWN) {
	    d = Direction.WEST;
	} else if (maze[x][y+1] == UNKNOWN) {
	    d = Direction.SOUTH;
	} else {
	    d = Direction.NORTH;
	}
	if (d == getDirection()) {
	    stepForward();
	} else if (d == getDirection().right()) {
	    turnRight();
	    stepForward();
	} else if (d == getDirection().left()) {
	    turnLeft();
	    stepForward();
	}
    }

    // Looks for a an unknown with an empty space next to it.
    private Point findUnknown(Point current) {
	int x = current.x;
	int y = current.y;
	for (int i = 1; i < height; i++) {
	    for (int j = -i; j <= i; j++) {
		for(int k = -i; k <= i; k++) {
		    if (!((x + j < 0) || (y + k < 0) || (x + j > width -1) || (y + k > height - 1))) {
			if (maze[x + j][y + k] == UNKNOWN) {
			    Point p = new Point(x+j, y+k);
			    Point side = emptySide(p);
			    if (side != null) {
				return side;
			    }
			}
		    }
		}
	    }
	}
	return null;
    }

    // Checks if there is an empty side next to an unknown space.
    private Point emptySide(Point p) {
	int x = p.x;
	int y = p.y;
	if (maze[x+1][y] == SPACE) {
	    return new Point(x+1, y);
	} else if (maze[x-1][y] == SPACE) {
	    return new Point(x-1, y);
	} else if (maze[x][y+1] == SPACE) {
	    return new Point(x, y+1);
	} else if (maze[x][y-1] == SPACE) {
	    return new Point(x, y-1);
	}
	return null;
    }
    
    // Moves forward, updating the map on the way.
    private boolean stepForward() {
	Point p = getPosition();
	boolean b = moveForward();
	if (b) {
	    updatePosition(p);
	}
	return b;
    }

    // Moves forward, updating the map on the way. Does multiple steps.
    private boolean stepForward(int i) {
	boolean b = false;
	for (int j = 0; j < i; j++) {
	    Point p = getPosition();
	    b = moveForward();
	    if (b) {
		updatePosition(p);
	    }
	}
	return b;
    }

    
    // Hides by finding a hollow space to hang out in.
    public void hide() {
	Point goTo = findHollow(getPosition());
	if (goTo == null) {
	    goTo = findCorner();
	}
	followPath(goTo);
	turnLeft();
	turnLeft();
	lieInWait();
	
    }

    // On death reduces the creature count and updates the position of the creature to 
    // be an enemy.
    public void onDeath() {
	Point p = getPosition();
	maze[p.x][p.y] = ENEMY;
	creatureCount--;
	/*if (creatureCount < 5) {
	    System.out.println(creatureCount);
	    action = HIDE;
	    }*/
    }

    // Finds a corner to hide in if no  hollow is available.
    private Point findCorner() {
	int x = 1;
	int y = 1;
	if (maze[x][y] == SPACE) {
	    return new Point(x, y);
	}
	for (int i = 1; i < height; i++) {
	    if (maze[x + i][y] == SPACE) {
		return new Point(x+i, y);
	    } else if (maze[x][y + i] == SPACE) {
		return new Point(x, y+i);
	    } else if (maze[x + i][y + i] == SPACE) {
		return new Point(x+i, y+i);
	    }
	}
	return null;
    }

    // Waits in a hollow, looking and attacking if anyone is near.
    private void lieInWait() {
	Observation o;
	while (true) {
	    o = addLook();
	    if (isEnemy(o)) {
		int dist = distance(o.position);
		if (dist == 1) {
		    attack();
		}
	    }
	}
    }

    // Finds a hollow to hide in, a square with 3 walls on the sides.
    private Point findHollow(Point current) {
	int x = current.x;
	int y = current.y;
	for (int i = 1; i < height; i++) {
	    for (int j = -i; j <= i; j++) {
		for(int k = -i; k <= i; k++) {
		    if (!(x + j < 0 || y + k < 0 || x + j > width || y + k > height)) {
			if (maze[x + j][y + k] == SPACE) {
			    int newX = x + j;
			    int newY = y + k;
			    int wallCount = 0;
			    if (maze[newX + 1][newY] == WALL) {
				wallCount++;
			    }
			    if (maze[newX - 1][newY] == WALL) {
				wallCount++;
			    }
			    if (maze[newX][newY + 1] == WALL) {
				wallCount++;
			    }
			    if (maze[newX][newY - 1] == WALL) {
				wallCount++;
			    }
			    if (wallCount == 3) {
				return new Point(newX, newY);
			    }
			
			}
		    }
		}
	    }
	}
	return null;
    }

    // Checks what state the creatures should turn to.
    private void checkState() {
	if (action == EXPLORE) {
	    //if (exploreCount < 10) {
		earlyExplore();
		//} else {
		//lateExplore();
		//}
	} else if (action == PURSUE) {
	    pursue();
	} else if (action == HIDE) {
	    hide();
	}
    }

    // Changes the past position to a space, and the current to a friendly.
    private void updatePosition(Point p) {
	// updates creature location on map
	maze[p.x][p.y] = SPACE;
	maze[getPosition().x][getPosition().y] = US;

    }

    // Checks if there is an enemy next to the current square. Attacks if so.

    public void checkAdjacent() {
	int x = getPosition().x;
	int y = getPosition().y;

	if (maze[x + 1][y] == ENEMY) {
	    face(Direction.EAST);
	    attack();
	} else if (maze[x - 1][y] == ENEMY) {
	    face(Direction.WEST);
	    attack();
	} else if (maze[x][y + 1] == ENEMY) {
	    face(Direction.SOUTH);
	    attack();
	} else if (maze[x][y - 1] == ENEMY) {
	    face(Direction.NORTH);
	    attack();
	} 
    }

    // Checks to see if the current exploration goal should be changed.
    // Prioritizes apples, flytraps and enemies over exploring.
    public void checkObs(Observation o) {

	// Attacks an apple whenever it is seen.
	if (o.classId == APPLE_CLASS_ID) {
	    stepForward(distance(o.position) - 1);
	    attack();
	} else if (o.classId == FLYTRAP_CLASS_ID) {
	    stepForward(distance(o.position) - 2);
	    o = addLook();
	    if (o.classId == FLYTRAP_CLASS_ID) {
		while (o.direction == (getDirection().right().right())) {
		    delay();
		    o = addLook();
		} 
	    }else {
		checkObs(o);
		stepForward();
		attack();
		
	    }
	} else if (o.classId == SELF) {
	    turnRight();
	    exploreCount = 11;
	} else if (isEnemy(o)) {
	    if (distance(o.position) == 1) {
		attack();
	    }
	    action = PURSUE;
	    clump();
	}
    }

    // turns Maximus randomly
    private char randomTurn() {
	int r = generator.nextInt(2);
	if (r == 0) {
	    turnLeft();
	    return 'l';
	} else {
	    turnRight();
	    return 'r';
	}
    }

    // Takes a point, calls pathfinding in order to get directions.
    private void followPath(Point x) {
	LinkedList<Direction> directions = pathFind(x);
	followPath(directions);
    }
	
    // Interprets the directions given, turning and moving based on the list.
    private void followPath(LinkedList<Direction> directions) {
	int current = 0;
	Observation o;

	if (directions == null) {
	    return;
	} 
	while(current < directions.size()) {
	    
	    Direction a = directions.get(current);
	    if (getDirection().equals(a)) {
		stepForward();
		o = addLook();
		if (o.classId == APPLE_CLASS_ID  || isEnemy(o)) {
		    checkObs(o);
		    return;
		}
	    } else if (getDirection().right().equals(a)) {
		turnRight();
		stepForward();
		o = addLook();
		if (o.classId == APPLE_CLASS_ID || isEnemy(o)) {
		    checkObs(o);
		    return;
		}
	    } else if (getDirection().left().equals(a)) {
		turnLeft();
		stepForward();
		o = addLook();
		if (o.classId == APPLE_CLASS_ID || isEnemy(o)) {
		    checkObs(o);
		    return;
		}
	    } else {
		turnRight();
		turnRight();
		o = addLook();
		if (o.classId == APPLE_CLASS_ID || isEnemy(o)) {
		    checkObs(o);
		    return;
		}
	    }
	    ++current;
	}
    }

    // Finds a path to a point from the current locations/
    public LinkedList<Direction> pathFind(Point x) {
	
	// Priority Queue for the algorithm.
	PriorityQueue<Node> Q = new PriorityQueue<Node>();
	LinkedList<Direction> dirs = checkEasyPath(x);

	// Checks if it can go straight east and west and then north and south;
	if (dirs.size() != 0) {
	    return dirs;
	} else  {
	    
	    // Hash map keeps track of the current distance to a point, as we
	    // cannot easily search the Queue for a certain point.
	    HashMap<Point, Node> theMap = new HashMap<Point, Node>();
			
	    // Adds the root to the queue.
	    Node root = new Node(getPosition(), 0, null, null);
	    Q.add(root);
	    theMap.put(root.location, root);
	    // Sets the initial direction, as the root doesn't have one.
	    Direction P = getDirection();
	    //long time = System.nanoTime();
	    
	    //Runs while it isn't taking too long and while the Queue isn't empty.
	    while(!Q.isEmpty()) {
		
		
		Node U = Q.poll();
		if (U.dir != null) {
		    P = U.dir;
		}

		// Gets the next point in the direction of the node. 
		Point current = P.forward(U.location); 
		Node next = theMap.get(current);
		// If it is unknown or space or a clump spot, then the pathfinding will try to go through it.
		if ((maze[current.x][current.y] == SPACE) || (maze[current.x][current.y] == UNKNOWN)  || 
		    (maze[current.x][current.y] == CLUMP)) {
					
		    // If the point is already in the map, checks if the current path is shorter.
		    if (next != null) {
			if (next.distance > U.distance + 40) { 
			    // If it's the goal, returns that it is finished.
			    if (current.equals(x)){
				return thePath(U, U.dir);
			    }
			    next.previous = U;
			    next.distance = U.distance + 40;
			    next.dir = U.dir;
			    next.location = current;
			    //theMap.put(current, U.distance + 40);
			} 
		    } else {
			// The point is not in the map, so it is added to both.
			if (current.equals(x)) {
			    return thePath(U, P);
			}
			next = new Node(current, U.distance + 40, U, P);
			Q.add(next);
			theMap.put(current, next);
						
		    }
		}
		// The rest does the same thign for the other directions.

		current = P.right().forward(U.location);
		if ((maze[current.x][current.y] == SPACE) || (maze[current.x][current.y] == UNKNOWN)
		    || (maze[current.x][current.y] == CLUMP)) {
		    next = theMap.get(current);
		    // If the point is already in the map, checks if the current path is shorter.
		    if (next != null) {
			if (next.distance > U.distance + 100) {
			    if (current.equals(x)){
				return thePath(U, U.dir.right());
			    }
			    next.previous = U;
			    next.distance = U.distance + 40;
			    next.dir = U.dir.right();
			    next.location = current;
							
			    // Q.add(new Node(current, U.distance + 100, U, U.dir.right()));
			    //theMap.put(current, U.distance + 100);
			}
		    } else {
			if (current.equals(x)) {
			    return thePath(U, P.right());
			}
			next = new Node(current, U.distance + 100, U, P.right());
			Q.add(next);
			theMap.put(current, next);
		    }	
		}
		current = P.left().forward(U.location);
		if ((maze[current.x][current.y] == SPACE) || (maze[current.x][current.y] == UNKNOWN)
		    || (maze[current.x][current.y] == CLUMP)) {
		    next = theMap.get(current);
		    // If the point is already in the map, checks if the current path is shorter.
		    if (next != null) {
			if (next.distance > U.distance + 100) {
			    if (current.equals(x)){
				return thePath(U, P.left());
			    }
			    next.previous = U;
			    next.distance = U.distance + 40;
			    next.dir = U.dir.left();
			    next.location = current;
							
							
			    //Q.add(new Node(current, U.distance + 100, U, U.dir.left()));
			    //theMap.put(current, U.distance + 100);
			}
		    } else {
			if (current.equals(x)) {
			    return thePath(U, U.dir.left());
			}
			next = new Node(current, U.distance + 100, U, P.left());
			Q.add(next);
			theMap.put(current, next);
						
		    }
		}
	    }
	}
	return null;
    }

    // Gets teh actual directions from the nodes.
    public LinkedList<Direction> thePath(Node point, Direction last){
	Node theNode = point;
	LinkedList<Direction> dirs = new LinkedList<Direction>();
	dirs.addFirst(last);
	while (theNode.dir!= null) {
	    dirs.addFirst(theNode.dir);
	    theNode = theNode.previous;
	}
	return dirs;
    }
	
    // Checks if there is a straight path from a point to a path.
    public LinkedList<Direction> checkEasyPath(Point point) {
	Point current = getPosition();
	Point p = point;
	int startx = current.x;
	int starty = current.y;
	LinkedList<Direction> dirs = new LinkedList<Direction>();
		
	// Reverses the variables so the for loop stays the same.
	if ( p.x < startx) {
	    startx = p.x;
	    p =current;
	}
	// Checks the east west route for obstructions.
	for (int i = startx; i < p.x; i++) {
	    if (maze[i][current.y] == WALL) {
		return dirs;
	    }
	}
	p = point;
	// Reverses the variables so the for loop stays the same.
	if (p.y < starty) {
	    starty = p.y;
	    p =current;
	}
	// Checks if there are obstructions in the north south route.
	for (int j = starty; j < p.y; j++) {
	    if (maze[current.x][j] == WALL) {
		return dirs;
	    }
	}
	Direction currentDir; 
	if (point.x >= current.x) {
	    currentDir = Direction.EAST;
	} else {
	    currentDir = Direction.WEST;
	}
	// adds the east west route directions
	for(int i = 0; i < Math.abs(point.x-current.x); i++) {
	    dirs.addFirst(currentDir);
	}
		
	if (point.y >= current.y) {
	    currentDir = Direction.SOUTH;
	} else {
	    currentDir = Direction.NORTH;
	} 
	// adds the north south route directions.
	for(int i = 0; i < Math.abs(point.y - current.y); i++) {
	    dirs.addFirst(currentDir);
	}	
	return dirs;
    }
	
    public void face(Direction a) {
	if (getDirection().equals(a)) {
	    return;
	} else if (getDirection().right().equals(a)) {
	    turnRight();
	    return;
	} else if (getDirection().left().equals(a)) {
	    turnLeft();
	    return;
	} else {
	    turnRight();
	    turnRight();
	}
    }
    
    

    public Observation addLook() {
	//The current status of the creature.
	Observation currentLook = look();
	
	
	//Accounts for looking.
	// Falcon sees the treasure and attacks it.
	// Falcon sees a wall and thus knows everything before it is a space.
	
	Direction facing = getDirection();
	    
	// Each of the following if statements set the maze array to spaces up until the wall.
	if (facing.equals(Direction.SOUTH)) {
	    int yCoord = currentLook.position.y;
	    for(int i =  getPosition().y + 1; i < currentLook.position.y; i++) {
		maze[getPosition().x][i] = SPACE;
	    }
	    setSpot(currentLook);
			

	    // Fills array for north.
	} else if (facing.equals(Direction.NORTH)) {
	    int yCoord = currentLook.position.y;
	    for(int i = getPosition().y - 1; i > currentLook.position.y; i--) {
		maze[getPosition().x][i] = SPACE; 
	    }
	    setSpot(currentLook);
		
	    // Fills array for east.
	} else if (facing.equals(Direction.EAST)) {
	    int xCoord = currentLook.position.x;
	    for(int i = getPosition().x + 1; i < currentLook.position.x; i++) {
		maze[i][getPosition().y] = SPACE;
	    }
	    setSpot(currentLook);
		
	    // Fills array for West.
	} else {
	    int xCoord = currentLook.position.x;
	    for(int i = getPosition().x - 1; i > currentLook.position.x; i--) {
		maze[i][getPosition().y] = SPACE;
	    }
	    setSpot(currentLook);
	}


	
	return currentLook;
	  
    }

    // Sets the observation spot to what it ends up being.
    private void setSpot(Observation currentLook) {
	if (currentLook.classId == APPLE_CLASS_ID) {
	    maze[currentLook.position.x][currentLook.position.y] = APPLE;
	} else if (currentLook.classId == FLYTRAP_CLASS_ID) {
	    maze[currentLook.position.x][currentLook.position.y] = FLYTRAP;
	} else if (currentLook.classId == SELF) {
	    maze[currentLook.position.x][currentLook.position.y] = US;
	} else if (currentLook.classId == THORN_CLASS_ID){
	    maze[currentLook.position.x][currentLook.position.y] = THORN;
	} else if (isEnemy(currentLook)) {
	    maze[currentLook.position.x][currentLook.position.y] = ENEMY;
	} else if (currentLook.classId == WALL_CLASS_ID) {
	    maze[currentLook.position.x][currentLook.position.y] = WALL;
	} else {
	    
	    
	}
    }

    // Prints description.
    public String getDescription() {
	return "Maximus Decimus will have his revenge.";
    }
    
    // Prints the author name.
    public String getAuthorName() {
	return "Sean and Greg";
    }

    // Goes through the maze, printing the value of each spot.
    public String toString() {
	String theMaze = "";

	Dimension temp = getMapDimensions();
	int height = (int)temp.getHeight();
	int width = (int)temp.getWidth();
	
	
	for (int j = 0; j < height; j++) {
	    for (int i = 0; i < width; i++) {
		switch  (maze[i][j]) {
		case 0:
		    theMaze += "?";
		    break;
		case 1:
		    theMaze += " ";
		    break;
		case 2:
		    theMaze += "x";
		    break;
		case 3:
		    theMaze += "E";
		    break;
		case 4:
		    theMaze += "C";
		    break;
		case 5:
		    theMaze += "A";
		    break;
		case 6:
		    theMaze += "T";
		    break;
		case 7:
		    theMaze += "F";
		    break;
		case 8:
		    theMaze += "o";
		    break;
		}
		
	    }
	    theMaze += " \n";
	}

	theMaze += " \n";
	return s + theMaze + "\nApple: " + APPLE_CLASS_ID +
	    "\nThorn: " + THORN_CLASS_ID +
	    "\nFlytrap: " + FLYTRAP_CLASS_ID + 
	    "\nWall: " + WALL_CLASS_ID;
    }
}
