import java.util.Vector;
import java.util.Stack;
import java.util.ArrayDeque;
import java.awt.Point;
import java.awt.Dimension;
import java.util.Random;
import java.util.Iterator;

/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * AntSSSW Tournament Submission 
 * by Stewart Stewart and Stephen Webster
 * Built on Genio Version b04.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * This maze-solving creature is built off of a basic recursive
 * strategy, with a few optimizations added. The basic recursive
 * strategy:
 * - Step forward
 * - Solve this branch
 * - Step backwards
 * - Turn to find another unvisited branch to solve
 *
 * On top of that basic strategy, this creature:
 * [X] never moves backwards.
 * [X] chooses a branch to solve at random.
 * [X] looks before walking forward.
 * [X] - but only if the line of sight to the nearest wall has never been seen.
 * [X] doesn't turn to face any solved branches when uneccesary.
 * [X] remembers walls.
 * [X] begins with border walls marked.
 * [X] infers solve states by using map data.
 * [X] colaborates with other instances to solve a maze.
 *
 * The biggest advantage this creature has is its ability to look at
 * the map and reason out solved branches without actually visiting
 * every square.  The most important information for this is the
 * location of walls. Because most mazes have thin walls, this
 * creature avoids wasting time checking both sides of a wall. If it
 * discovers walls in just the right order, this creature can
 * completely dead ends! However, these abilities are rendered
 * completely useless when mazes have double-width walls. In mazes
 * such as mz_pacman.txt from the Darwin SDK, Genio_a00 slows down to
 * speeds similar to other recursive solutions. Despite that,
 * Genio_a00 still will tend to be faster than similar creatures
 * because of it's other optimizations.
 *
 * This creature would perform especially well on a map with many long
 * critical paths along the edges, and plenty of shared walls.
 *
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * TERMINOLOGY
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * 'pos' generally refers to the current position, wheras 'p' refers
 * to an arbirtrary position
 *
 * 'dir' generally refers to the current Direction, wheras 'd' refers
 * to an arbitrary Direction
 *
 * 'explore' move forward into unsolved teritory. We never explore
 * branches that are checked, or the exit branch.
 * 
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * CHANGE LOG 
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * Version AntSSSW
 *
 * Version b04
 * aoeuhtnseohutnaoshuetn
 *
 * Version b03
 * - Various battle changes, looking fantastic! err, saving a version out of paranoia
 *
 * Version b02
 * - Various battle changes. It's sorta a mess
 *
 * Version b01
 * - Added Steve's halfway strategy
 *   - first version to eliminate sheep by total domination
 * - Debugged an error in a05 that caused Genio to walk into thorns
 * - Combined the code from a05 and b00
 *
 * Version b00
 * - Genio now implements battle capabilities.
 * - Separated solveLook() into smartLook and solveLook()
 *   - solveLook() calls smartLook if it wants to look
 *   - smartLook() should replace the default look()?
 * - note: contains error that causes Genio to walk into thorns
 *
 * Version a05
 * - SOLVED the multi-root collision problem!!
 * - Vocabulary changes - everything is now clear and well-defined
 * - Deprecated the following methods:
 *   - inferDirection
 *   - inferPosition
 *   - setSolved
 *
 * Version a04 
 * - err... yeah, this was a mess.
 * - A few structural changes were made to make the code more modular.
 * - Implemented root-shifting. We can now transform any position into
 *   the root, and recruit all units to that position
 *
 * Version a03
 * - Each position in Map is now stores two 8-bit integers:
 *   - the ID number of a root position
 *   - the distance from the position to that root
 * - Implemented 'root-merging' 
 *
 * Version a02
 * - cleaned up the code
 *   - split code into helper methods for readability
 *   - updated comments
 *   - added tons of assertions
 *   - added stack traces
 *   - added '// TODO' tags and '// IDEA' tags
 *
 * Version a01 - a00
 * - solved the collision problem when there is only a single root 
 * - completely removed anything having to do with the 'trail' stack 
 * - rather than using stacks, we'll use markers in the map.
 * - created this change log, changed GenoSS to Genio and started a version number
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * NOTES
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * - check the 'Map' subclass for terms relating to solve/search.
 *   Otherwise ambigous-sounding terms may actually have specific
 *   meanings.
 *
 * - use 'java -ea' in the command line to enable assertions.
 *
 * - there are '// TODO' tags placed throughout the document, each
 *   describing a feature to implement. Use 'C-s' to search for them.
 *   Typically, these are straightforward to implement.
 *
 * - there are '// IDEA' tags placed throught the document, each
 *    commenting on a potential feature to implement. Typically, we
 *    don't have a clue how we'd go about implementing the idea, or
 *    whether we even want to
 *
 * - use 'addMessage()' as opposed to 'System.out.println()' when
 *   printing debug messages, unless we are in a static inner class of
 *   Genio. addMessage() will only work if the final static variable
 *   DEBUG is set to true.
 *
 * - Use "M-x replace-string RET addMess RET /*addMess RET" to comment
 *   all addMessage() statements.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * TODO
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * - add a mechanism to halt searching, and force gathering.
 * 
 * - Potential come up with better inference algorithms to be used by
 *   solveLook
 *
 * - Remember death locations (and time? create a class?)
 *
 * - Write code for 'Face him down solo'
 *
 * - Solve the collision problem for the case with multiple roots
 *   (involves recognizing root)
 *
 * - Create a clean interface for implementing strategies and state
 *   machines.
 *
 *
 *
 * IDEAS:
 * 
 * Consider a 'hostile sighting class' or some similar mechanism
 *
 */
public class AntSSSW extends Creature {
    // DEBUG VARIABLES
    private static final boolean DEBUG = false;
    private String messages = getDescription();
    private Point lastMessagePos = null;
    private Direction lastMessageDir = null;
    private static final int MAX_MESSAGE_LENGTH = 25000;
    // END DEBUG VARIABLES

    enum State {
	FINDFOOD, EATFOOD, FIGHTFOOD, DUMMY, FINDROOT, CAMP, UNPAIRED, MASTER, ATROOT, PATROL;
    }
    enum Strategy {// Determines which state machine a creature uses
	SEARCH, RALLY, PAIRS, ALERT;
    } 
    enum Command { // Used in dummy state
	MOVEFORWARD, TURNRIGHT, TURNLEFT, LOOK, DELAY, ATTACK, MOVEBACK;
    }

    private static final Random rand = new Random();
    private static boolean initialized = false;
    private static Map map;
    private static Strategy strategy = Strategy.SEARCH;
    private static int rootCount = 0;
    private static ArrayDeque<Death> deaths = new ArrayDeque<Death>();
    private static int bodyCount = 0;
    private static int numSpawned = 0;

    private static int RADIUS = 6;
    

    private static boolean hostilesExist = false;

    private ArrayDeque<Command> commandQueue = new ArrayDeque<Command>();
    private State state = State.FINDFOOD;

    private Observation lastObs;
    private boolean lastAtt;

    /**
     * The logic powering Genio :)
     *
     * pre: Genio should NOT be loaded into a map where its chances of
     * winning are sub-optimal. This is not enforced, but it's an
     * extremely difficult rule to break. i.e. Genio should not be
     * pitted against WolfMM
     *
     * post: what it does is a mystery to all but its creators.
     */
    public void run() {
	sharedInit();

	Point pos = getPosition();
	
	// personal init
	lastObs = observeSelf();
	lastAtt = false;	
	map.setSeen(pos);
	if (map.getRootId(pos)==0) {
	    // This spawn position doesn't exit toward a root, so it's
	    // a new root.
	    ++rootCount;
	    map.setRootId(pos, rootCount);
	}
	 
	// The nested state machine! The state machine that is
	// followed depends on the state (called stategy) of a
	// meta-state machine that chooses a strategy.
	while (true) {
	    switch (strategy) {
	    case SEARCH:
		switch (state) {
		case FINDFOOD:
		    search();
		    break;
		case EATFOOD:
		    interact(lastObs);
		    break;
		case FIGHTFOOD:
		    break;
		case FINDROOT:
		    exit();
		    break;
		    //	case ATROOT:
		    //	    if (map.getSolved(getPosition())) {
		    //		delay();
		    //    }
		    //    break;
		case PATROL:
		    if (map.getHostile(getPosition())) {
			patrol();
		    } else {
			setState(State.FINDFOOD);
		    }
		    break;
		default:
		    setState(State.FINDFOOD);
		    break;
		}
		break;		
	    case ALERT:
		System.out.println("ALERT!!!");
		switch(state) {
		case EATFOOD:
		    interact(lastObs);
		default:
		    //System.out.println(getId()+ ": patrolling");
		    patrol();
		    break;
		}
		break;
	    case RALLY:
		switch (state) {
		case FIGHTFOOD:
		    halfWay();
		    // TODO -- Patrol// walking thornbush
		    break;
		case ATROOT:
		    delay();//defend
		    break;
		case FINDROOT:
		    exit();
		    break;
		default:
		    setState(State.FINDROOT);
		}
		break;
	    default:
		strategy = Strategy.SEARCH;
	    }
	}
    }
    
    public String toString() {return messages;}
    public String getAuthorName() {return "Stewart Stewart, Stephen Webster";}
    public String getDescription() {
	return "It solves a maze. Quickly. In packs.\n" 
	    + "Where by 'solves', we mean\n"
	    + "'gluttonously-eats-the-entire-contents-of'";
	// I don't want any comments about this...
    }
    
    /**
     * post: runs when the first instance of 
     * this class begins itn run() code.
     *
     * note: this code only runs once!
     */
    protected void sharedInit() {
	if (initialized) {
	    return;
	} else {
	    initialized = true;
	    map = new Map(getMapDimensions());
	}
    }

    /**
     * pre: this spot is part of a trail.
     *
     * post: If this position is solved, the creature
     * exits. Otherwise, it chooses a branch (other than exit branch)
     * that hasn't been checked, tries to solve it using map data,
     * then explores it.
     *
     * Note: the chooseBranch-solveLook-exploreForward process might
     * be cut off mid-execution if the situation requires it. Be sure
     * the program is structured so that this can happen.
     */
    protected void search() {
	Point pos = getPosition();
	Direction dir = getDirection();	
	addMessage("calling search()...\n"
		   + "  " + map.solveState(pos));

	if (map.getHostile(pos)) {
	    setState(State.PATROL);
	    return; // no more searching
	}

	//	assert map.getRootId(pos) != 0 : "Called search() outside of a trail";
	if (map.getRootId(pos)==0) {
	    // This spawn position doesn't exit toward a root, so it's
	    // a new root.
	    ++rootCount;
	    map.setRootId(pos, rootCount);
	}


	// Step 1: check if we're adjacent to any trails with a
	// different non-zero ID
	for (Direction d : Direction.values()) {
	    int forwardId = map.getRootId(d.forward(pos));
	    if (forwardId != 0 && forwardId != map.getRootId(pos)) {
		// set the ID of the current trail to match that of
		// the trail we just found. (risk of timeout?)
		addMessage("Found new trail!");
		map.fixRootId(pos, forwardId);
		
		// empty the current trail into the new one
		map.flipTrail(pos, d);
	    }	    
	}

	// Step 2: Evaluate this position to try and set as many
	// directions 'checked' as we can. It is important to note
	// that any adjacent unexplored positions now have the same
	// rootId.
	map.evaluatePosition(pos);

	// Step 3: if the position is now solved, exit.
	if (map.getSolved(pos)) {
	    exit();
	    return;
	}

	// Step 4: There are still branches to explore. Choose one.
	chooseBranch();
	// a gentle reminder that direction changes
	dir = getDirection(); 
	
	// Step 5: Look to learn if we haven't seen the forward
	// direction. Depending on the alert level, we might look more
	// often.
	State s = state;
	//smartLook();
	solveLook();

	// There is a possibility of changing state here!!

	

	// re-evaluate thedirection in case another creatur has moved
	// since look()
	map.evaluateDirection(pos, dir);
	if ((s == state) && (!map.getChecked(pos, dir))) {
	    exploreForward();
	} else {
	    addMessage("Did not move forward");
	}
    }

    /**
     * post: Causes the creature to exit towards the root, unless they
     * are currently at the root. 
     */
    protected void exit() {
	if (state == State.FINDROOT) {
	    smartLook();
	    if (!deaths.isEmpty()) {
		Iterator<Death> it = deaths.iterator();
		while (it.hasNext()) {
		    Death current = it.next();
		    if ((getGameTime() - current.time) > Death.EXPIRE) {
			it.remove();
		    } else if (distance(getPosition(), current.pos) < 2) {
			//			attack(Direction.fromTo(getPosition(), current.pos));
		    }
		}
	    }
	}
	Point pos = getPosition();
	Direction dir = getDirection();
	if (map.getRoot(pos)) {
	    // We are solved at the root!
	    addMessage("delaying at root");
	    delay();
	    return;
	}
	Direction exitDir = map.exitDirection(pos);
	Point exitPos = map.exitPosition(pos);
	/*addMessage(toString(pos) + " is solved, exiting...");/* */
	/*addMessage("setting " + exitDir.opposite() + " of " 
		   + toString(exitPos) + " checked");/* */
	// Note: it is important to do this before leaving, in case
	// someone else is trying to enter
	map.setChecked(exitPos, exitDir.opposite());	    
	/*addMessage("exitting " + exitDir);/* */
	while (!moveForward(exitDir)) {
	    addMessage("delaying exit");/* */
	}
	addMessage("exit successful");/* */
    }
    

    /**
     * pre: this position is not solved
     * 
     * post: based on the solve data at the current position, this
     * method chooses a branch for this creature to take, and turns
     * the creature to face that direction.
     *
     * note: currently, this method chooses at random some branch that
     * isn't the exit branch of this node and that hasn't been checked
     * (see the definitions for 'exit' and 'checked' in the 'Map'
     * inner class).
     *
     * This is how I expect the probabilities to work:
     * 
     * If the forward branch is open...
     *
     * 100% chance of choosing forward if neither side branch is open
     *  50% chance of moving forward if there is a side branch open
     *
     *  25% chance of choosing a given side branch if the other one is open
     *  50% otherwise
     *
     * else if the forward branch is not open...
     *
     *  50% chance of choosing a given side branch if the other one is open
     * 100% otherwise
     *
     * and if none of those three branches are open, we choose the
     * back branch (which is hopefully open or else the precondition)
     *
     * There is an emphasis on forward exploration for the purpose of
     * speed
     */
    protected void chooseBranch() {
	// IDEA control probability of exploring a branch based on
	// whether or not someone else has moved into (and is
	// currently checking) that branch or even the number of
	// people who've traveled that direction. We could use this to
	// cause creatures to fan out and solve the maze quicker, or
	// clump up and solve the maze in swarms. 

	Point pos = getPosition();
	Direction dir = getDirection();
	assert !map.getSolved(pos) 
	    : "called chooseBranch at a solved position";

	// Call a branch is "open" if that direction hasn't been
	// checked, and isn't the exit direction.
	boolean rightOpen = !(map.getChecked(pos, dir.right()) 
			      || map.getTrail(pos,dir.right()));
	boolean leftOpen = !(map.getChecked(pos, dir.left()) 
			     || map.getTrail(pos,dir.left()));
	boolean forwardOpen = !(map.getChecked(pos, dir) 
				|| map.getTrail(pos,dir));

	// choose a side direction first
	boolean sideFirst = !(rightOpen||leftOpen) ? false : rand.nextBoolean();

	if (forwardOpen && !sideFirst) {
	    // don't turn at all
	} else if (rightOpen^leftOpen) {
	    // turn toward the open sidebranch
	    turnChoice(rightOpen);
	} else if (rightOpen && leftOpen) {
	    // choose a sidebranch at random
	    turnChoice(rand.nextBoolean());
	} else {
	    // perform a random 180;
	    assert !rightOpen && !leftOpen && !forwardOpen
		: "check your reasoning";
	    turn180();
	}
    }


    /**
     * post: moveForward, but not into space that's checked
     */
    protected void exploreForward() {
	Point pos = getPosition();
	Point nextPos = getMovePosition();
	Direction dir = getDirection();
	addMessage("exploringForward\n  " + map.solveState(pos));/* */

	// set the exit in advance. It's a little messier to calculate
	// the exit after moving
	map.suggestTrail(nextPos, dir.opposite());
	addMessage("attempting to explore forward");/* */

	//	assert map.getTrail(nextPos, dir.opposite()) 
	//	    :"The position ahead doesn't exit here";

	// A cheesy way to solve a problem when the path ahead is
	// suddenly a different trail
	if (!map.getTrail(nextPos, dir.opposite())) {
	    moveForward();
	}

	// Keep attempting to move forward, unless it turns out that
	// the foward position is checked (this could change).
	while (!moveForward() && !map.getChecked(pos, dir)) {
	    addMessage("attempting to explore forward");/* */
	    map.evaluateDirection(pos, dir);
	}
    }
    
    /**
     * post: look() if we haven't seen the line-of-sight path to the
     * nearest obstruction in the forward direction from this position
     * before.
     *
     * note: this method isn't for searching for enemies!!
     *
     * // IDEA - vary solveLook() execution based on state? is it
     * // usefull elsewhere besides foodhunting?
     */
    protected void solveLook() {
	Point pos = getPosition();
	Direction dir = getDirection();
	Point nextPos = getMovePosition();
	addMessage("calling solveLook()...\n  " + map.solveState(pos));/* */

	int wallDist = map.wallDistance(getPosition(), getDirection());
	if (wallDist == -1) {
	    addMessage("looking");
	    Observation o = smartLook();
	    wallDist = distance(o.position);
	    for (int i = wallDist; i > 0; i--) {
		map.setSeen(getMovePosition(i));
	    }
	} 
	addMessage("The path to the nearest line-of-sight obstacle has already been seen");
	/**
	/*addMessage("evaluating the next " + (wallDist - 1) 
		   + " positions ahead");
	if (wallDist < 2) {
	    return;
	}
	for (Point p = getMovePosition(wallDist-1)
		 ; ! p.equals(pos); p = dir.forward(p, -1)) {	
	    //map.inferPos(p); <-- this feature is buggy, but it's
	    //superfluous and outdated anyway. Think of a newer
	    //alternative // TODO
	}
	/**/
    }


    /**
     * post: look(), but remember walls, and respond to creature sightings
     */
    protected Observation smartLook() {
	Observation o = look();
	switch (o.type) {
	case WALL:
	case THORN:
	    map.setImpassable(o.position);
	    break;
	case CREATURE:
	    if (isEnemy(o)) setState(State.EATFOOD);
		break;
	default:
	    assert false : o.type;
	}
	return o;
    }
    
    /**
     * pre: Observation o is about an enemy creature
     *
     * post: handle reactions to various creature sightings
     */
    protected void interact(Observation o) {
	assert o.type.equals(Type.CREATURE) && isEnemy(o) : o;

	switch (o.classId) {
	case APPLE_CLASS_ID:
	case TREASURE_CLASS_ID:
	    // approach and attack the treasure, while setting a path
	    // to the root
	    int dist = distance(o.position);
	    for (int i = dist; i > 1; i--) {
		moveForward();
		map.suggestTrail(getPosition(), getDirection().opposite());
	    }
	    attack();
	    break;
	case FLYTRAP_CLASS_ID:
	    // TODO - should be able to calculate flytrap direction
	    // based on 'o'

	    // approach the flytrap
	    while (distance(o.position) > 3) {
		moveForward();
	    }
	    look();
	    halfWay();
	    break;
	default:
	    Point pos = getPosition();
	    Direction d = getDirection();
	    // set the halfway point as a root
	    
	    Point halfPoint = d.forward(pos, (distance(lastObs.position)/2));	    
	    for (Point p = pos; p.equals(halfPoint) ; p = d.forward(p)) {
		map.suggestTrail(p, d.opposite());
	    }
	    
	    
	    if (!map.getHostile(halfPoint)) {
		setRoot(halfPoint);
		map.setHostile(halfPoint, 0);
	    }
	    
	    //	    map.setHostile(lastObs.position, 25);
	    setHostile();
	    setState(State.PATROL);
	    
	    //setStrategy(Strategy.ALERT);
	    halfWay();
	    //	    setStrategy(Strategy.RALLY);
	    //	    setRoot(getPosition());
	    //	    map.setImpassable(o.position);
	    return;
	}
	setState(State.FINDFOOD);
    }

    protected void halfWay () {
	int range = distance(lastObs.position);
	Direction dir = getDirection();
	
	if (isEnemy(lastObs)){
	    //if point blank range, shoot him dead
	    if (range == 1) {
		attack();
		// he's dead
	    } else if (range == 2 || range == 3){
		if (!lastObs.direction.equals(dir.opposite())){
		    moveForward(range - 1);
		    attack();
		    
		    //if too close comfort, camp out and look	   
		    
		} else {
		    look();
	      	    if (isEnemy(lastObs)){
			halfWay();
		    }
		}    
		// he's gone
		
		// TODO.  Default is to go back to searching, but we
		// may want to record this observation first
		
	    } else {
		//otherwise, go halfway.  use bitshifts.
		moveForward((range >> 1) - 1); 
		look();
		if (isEnemy(lastObs)){
		    halfWay();
		} else {
		    // he's gone
		    
		    //TODO: ditto
		}
	    }
	}
    }
    
    protected void patrol() {
	//	turnchoice(rand.nextBoolean());	
	if (rand.nextBoolean()&&rand.nextBoolean()&&rand.nextBoolean()) {
	    if (!map.getWall(getDirection().forward(getPosition()))) {
		moveForward();
	    }
	} else {
	    turnRight();
	}
	smartLook();
    }

    /**
     * post: Sets p as the root. The units can now be called to p.
     */
    public void setRoot(Point p) {
	Point pos = getPosition();
	Point exitPos = map.exitPosition(pos);
	if (exitPos != null) {
	    map.flipTrail(exitPos, Direction.fromTo(exitPos, pos));
	    map.clearTrail(pos);
	    setState(State.FIGHTFOOD);
	    //	    setState(State.ATROOT);
	}
    }
    
    /**
     * post: nothing
     */
    public void onDeath() {
	lastObs = observeSelf();
	setHostile();
	//setStrategy(Strategy.ALERT);
	//	map.setHostile()
	//	deaths.addFirst(new Death(getGameTime(), getPosition()));
	//setStrategy(Strategy.RALLY);
    }

    protected void setHostile() {
	map.setHostile(lastObs.position, RADIUS);
    }

    /**
     * post: interpret a command token
     */
    protected void followCommand(Command c) {
	switch (c) {
	case MOVEFORWARD: moveForward(); break;
	case TURNRIGHT: turnRight(); break;
	case TURNLEFT: turnLeft(); break;
	case LOOK: look(); break;
	default: delay(); break;
	}	
    }

    
    ////////////////////////////////////////////////////////////////
    // HELPER METHODS / INFORMATION / MANEUVERS
    ////////////////////////////////////////////////////////////////

    // post: stores the most recent observation returned by look
    protected Observation look() {
	lastObs = super.look();
	return lastObs;
    }

    // post: stores the success of the previous attack suggests an
    // exit position for the would-be spawn position, that would
    // lead to the current position's root.
    protected boolean attack() {
	map.suggestTrail(getMovePosition(), getDirection().opposite());
	lastAtt = super.attack();
	return lastAtt;
    }

    // post: turn clockwise if true, counterclockwise otherwise
    protected void turnChoice(boolean b) {
	if (b) turnRight(); else turnLeft();
    }

    // post: returns the position immediately behind the creature
    protected Point getBackPosition() {
	return getDirection().opposite().forward(getPosition());
    }

    // post: turn until facing towards p, then move forward
    protected boolean moveForward(Point p) {
	return moveForward(Direction.fromTo(getPosition(), p));
    }
    
    // post: turns until facing (efficiently) Direction d, the moves forward    
    protected boolean moveForward(Direction d) {
	while (getDirection() != d) {
	    turnChoice(d == getDirection().right());
	}
	return moveForward();
    }
    
    // post: perform a 180-degree turn, in a random direction
    protected void turn180() {
	boolean turnChoice = rand.nextBoolean();
	turnChoice(turnChoice);
	turnChoice(turnChoice);	
    }

    ////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////
    // MUTATORS
    ////////////////////////////////////////////////////////////////
    protected void setStrategy(Strategy s) {
	strategy = s;
    }
    
    protected void setState(State s) {
	state = s;
    }
    
    ////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////
    // ACCESSORS
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////
    // INNER CLASSES
    ////////////////////////////////////////////////////////////////
    /**
     * Class for keeping track of deaths
     */
    public class Death {
	public static final long EXPIRE = 400000;
	long time;
	Point pos;
	public Death (long time, Point pos) {
	    this.time = time;
	    this.pos = pos;
	}
    }

    /**
     * A data structure for storing maze/map data.
     *
     * Given a Point p repersenting a position in the maze,
     * information about p is stored in an int, which is then stored
     * in an array at index [p.x][p.y].
     *
     * Interpretation of bits:
     *
     * N_BIT, E_BIT, S_BIT, and W_BIT correspond to the four cardinal
     * directions. Each of these bits is set on to signify that the
     * corresponding direction (from the position in question) is
     * checked. Once three of these direction bits are on, the
     * position is considered solved. If all four of these bits are on
     * and this position is not a root, then this position is
     * considered impassable.
     *
     * If we left-bitshift these bits by TRAIL_SHIFT, we get the
     * trail-marking bits -- one for each direction. One of these bits
     * is on if the corresponding direction leads towards the root.
     * None of these bits are ever turned on in an impassable
     * position.
     *
     * Not currently used:
     * 
     * Adjacency-shift - the four direction bits are marke according
     * to theconnectivity of the map as a graph
     *
     * The OCCUPIED bit is on if an instance of Genio is occupying the
     * position. (not currently used)
     *
     * The HOSTILE bit is on if an enemy was sited at the position.
     *
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * TERMINOLOGY 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *
     * 'root' - When a creature spawns, if it's position has no exit
     * direction (set by setTrail()), then the position is a root. As
     * the creature advances, it marks the exit direction, which
     * eventually leads toward the root. Every newly created root is
     * given a unique ID. The root ID zero is reserved for unexplored
     * territory.
     *
     * 'trail' - As the creature advances, it lays markers marking the
     * direction towards the previous position (which eventually leads
     * to the root). This position is also marked with the the
     * rootID. These paths back towards the root partition the map
     * into a tree which we call a 'trail' in this code. Trails
     * leading to different roots can by differentiated by the rootId
     * marker on them. When a creature runs into a different trail, it
     * sets the root of the current trail to the root of the new trail
     * and changes the ID accordingly.
     *
     * 'exit' - Every non-root position has associated with it an exit
     * direction. If the creature follows the trail of exit
     * directions, it eventually reaches the root.
     *
     * 'explore' - forward advancement into unsolved teritory
     * 
     * 'checked' - Each position has four direction markers which
     * could be set to checked. A direction is set to checked when
     * there is nothing new to explore in that direction. The exit
     * position is never marked checked, even if everything up to the
     * root is solved.
     *
     * 'solved' - a position is solved when that position has been
     * seen, and every direction from that position is either checked
     * or an exit. i.e. we don't want to explore from that direction.
     * 
     * 'visited' - the position is part of a trail.
     *
     * 'seen' - this position has been seen using the look() method of
     * creature
     *
     * 'get' - return true/false to signify bit(s) is on/off.
     *
     * 'set' - turn the bit(s) on, regardless of the previous value.
     *
     * 'clear' - turn the bit(s) off, regardless of the previous value.
     *
     * 'toggle' - switch the bit(s) to the opposite value.     *
     *
     */
    public static class Map {
	private static final int N_BIT = 1<<0;
	private static final int E_BIT = 1<<1;
	private static final int S_BIT = 1<<2;
	private static final int W_BIT = 1<<3;	
	private static final int WALL = (N_BIT|E_BIT|S_BIT|W_BIT);
	private static final int TRAIL_SHIFT = 4; //through 7
	private static final int BYTE_BLOCK = 255; //8 bits
	private static final int ROOT_ID_SHIFT = 8; //through 15
	private static final int ROOT_DISTANCE_SHIFT = 16; //through 23
	private static final int SEEN = 1<<24;
	private static final int OCCUPIED = 1<<25;
	private static final int HOSTILE = 1<<26;

	public Dimension dimensions;
	private int[][] map;
	Map(Dimension dim) {
	    this.dimensions = dim;
	    map = new int[dim.width][dim.height];
	    
	    for (int x = 0; x < dim.width; x++) {
		this.setImpassable(new Point(x, 0));
		this.setImpassable(new Point(x, dim.height - 1));
	    }
	    for (int y = 0; y < dim.height; y++) {
		this.setImpassable(new Point(0,y));
		this.setImpassable(new Point(dim.width - 1, y));
	    }
	}
	
	/**
	 * Returns true if the position is on the map
	 */
	private boolean inBounds(Point p) {
	    return p.x < dimensions.width
		&& p.y < dimensions.height
		&& p.x >= 0
		&& p.y >= 0;
	}

	/**
	 * post: returns the bit mask that corresponds to direction d
	 */
	private int directionBit(Direction d) {
	    switch (d) {
	    case NORTH: return N_BIT;
	    case EAST: return E_BIT;
	    case SOUTH: return S_BIT;
	    case WEST: return W_BIT;
	    default: 
		assert false : d;
		return 0;
	    }
	}
	
	/**
	 * pre: p must not be out-ouf-bounds
	 * post: returns true if the flag bits (for position p)
	 * specified by mask are on
	 */
	private boolean get(Point p, int mask) {
	    assert inBounds(p) : p;
	    return (map[p.x][p.y] & mask) == mask;
	}
	
	/**
	 * pre: p must not be out-of-bounds
	 * post: sets the flag bits (for position p) specified by mask
	 */
	private void set(Point p, int mask) {
	    assert inBounds(p) : p;
	    map[p.x][p.y] |= mask;
	}

	/**
	 * pre: p must not be out-of-bounds
	 * post: clears the flag bits (for position p) specified by mask
	 */
	private void clear(Point p, int mask) {
	    assert inBounds(p) : p;
	    map[p.x][p.y] &= ~mask;
	}
	
	/**
	 * pre: p must not be out-of-bounds
	 * post: toggles the flag bits (for position p) specified by mask
	 */
	private void toggle(Point p, int mask) {
	    assert inBounds(p) : p;
	    map[p.x][p.y] ^= mask;
	}

	/**
	 * pre: p must not be out-of-bounds
	 * post: returns of the byte stored shift places in.
	 */
	private int getByteBlock(Point p, int shift) {
	    assert inBounds(p) : p;
	    return (map[p.x][p.y] >> shift) & BYTE_BLOCK;
	}

	/**
	 * pre: p must not be out-of-bounds 
	 * 
	 * post: concatenates value to byte size then stores it shift
	 * places in.
	 */
	private void setByteBlock(Point p, int shift, int value) {
	    assert inBounds(p) : p;
	    map[p.x][p.y] &= (~(BYTE_BLOCK << shift));
	    map[p.x][p.y] |= ((value & BYTE_BLOCK) << shift);
	}

	//	if (!inBounds(p)) return;

	////////////////////////////////////////////////////////////////
	// ACCESSOR METHODS (get)
	////////////////////////////////////////////////////////////////
	
	// post: return true if the branch 'd' of position 'p' is checked
	public boolean getChecked(Point p, Direction d) {
	    return get(p, directionBit(d)); 
	}

	// post: returns true if the position p is impassable
	public boolean getWall(Point p) {
	    return get(p, WALL) && (getRootId(p) == 0);
	}

	/**
	 * post: returns true if the position is solved. i.e. if the
	 * position is a wall, or it has been visited (has an
	 * exit) and has three branches checked.
	 *
	 * Note that a position can be 'solved' and not visited(in
	 * this case, it's a wall).
	 * 
	 * A position can't be solved if it's never been seen before
	 * -- even if the position has three walls around it, we can't
	 * know that there is no treasure unless we look.
	 */
	public boolean getSolved(Point p) {
	    return getSeen(p)
		&& (getRoot(p) ? get(p,WALL)
		    : (get(p,WALL)
		       || get(p,WALL^N_BIT)
		       || get(p,WALL^E_BIT)
		       || get(p,WALL^S_BIT)
		       || get(p,WALL^W_BIT)));
	}
	
	/**
	 * post: returns true if an instance of this creature has
	 * travelled in this direction from this position
	 */
	public boolean getTrail(Point p, Direction d) {
	    return get(p, directionBit(d)<<TRAIL_SHIFT);
	}

	/**
	 * post: returns true if this position is part of a trail.
	 */
	public boolean getVisited(Point p) {
	    return getRootId(p) != 0;
	}
	
	/** post: returns the Id of the root p is connected to.
	 *
	 * // TODO - experiment - is it too slow to recursively return
	 * the root instead?
	 */	
	public int getRootId(Point p) {
	    return getByteBlock(p, ROOT_ID_SHIFT);
	}
	
	/**
	 * post: returns true if the current position is a root
	 */
	public boolean getRoot(Point p) {
	    return (exitDirection(p) == null) && (getRootId(p) != 0);
	}

	// post: returns the distance of this position from the root
	//
	// TODO - this returns manhattan distance through the
	// tree. Consider doing it recursively, and consider
	// considering thefact that paths with lots of turns are
	// slower i.e. a zig-zag path takes 5/2 as long to traverse as
	// a horizontal or vertical path.
	public int getRootDistance(Point p) {
	    return getByteBlock(p, ROOT_DISTANCE_SHIFT);
	}
	
	/**
	 * post: returns whether position p has been seen
	 */
	public boolean getSeen(Point p) {
	    return get(p, SEEN);
	}
	
	// post: returns whether position p is currently occupied
	// note: unused, but left just in case...
	public boolean getOccupied(Point p) {
	    return get(p, OCCUPIED);
	}

	public boolean getHostile(Point p) {
	    return get(p, HOSTILE);
	}

	////////////////////////////////////////////////////////////////
	
	
	////////////////////////////////////////////////////////////////
	// MUTATOR METHODS (set, clear, toggle)
	////////////////////////////////////////////////////////////////

	/**
	 * post: aoeuthnoatehu
	 */
	public void setHostile(Point p, int radius) {
	    for (int x = p.x - radius; x<= p.x + radius; x++) {
		for (int y = p.y - radius ; y<= p.y + radius; y++) {
		    Point q = new Point(x,y);
		    if (inBounds(q) && (Creature.distance(p,q)<= ((6*radius)/5)) ) {
			set(q,HOSTILE);
		    }
		}
	    }
	}

	
	/**
	 * post: Sets direction d of position p checked if that
	 * direction isn't the exit
	 */
	public void setChecked(Point p, Direction d) {
	    if (!getTrail(p,d)) {
		set (p, directionBit(d));
	    } 
	}

	/**
	 * post: mark this position as impassable
	 */
	public void setImpassable(Point p) {
	    set(p,WALL);
	    setSeen(p);
	    for (Direction d : Direction.values()) {
		if (inBounds(d.forward(p)) 
		    && !getSolved(d.forward(p))){
		    setChecked(d.forward(p), d.opposite());
		}
	    }
	}
	
	
	/**
	 * pre: This position is not impassable
	 *
	 * post: remembers that this direction leads to the root, then
	 * sets the root Id of this position to that of it's exit
	 * position.
	 */
	public void setTrail(Point p, Direction d) {
	    assert !getWall(p)
		: "Tried to set trail at an impassable location";
	    set(p, directionBit(d)<<TRAIL_SHIFT);
	    setRootId(p,getRootId(d.forward(p)));
	}

	// post: clears the trail in this direction
	public void clearTrail(Point p, Direction d) {
	    clear(p, directionBit(d)<<TRAIL_SHIFT);
	}
	
	/**
	 * post: cleas the trail in every direction
	 */
	public void clearTrail(Point p) {
	    for (Direction d : Direction.values()) {
		clearTrail(p,d);
	    }	    
	}


	// post: remember the Id of the root this position exits to
	public void setRootId(Point p, int value) {
	    setByteBlock(p, ROOT_ID_SHIFT, value);
	}

	// post: remember the distance of this position from the root
	private void setRootDistance(Point p, int value) {
	    setByteBlock(p, ROOT_DISTANCE_SHIFT, value);
	}

	// post: remembers that position p has been seen
	public void setSeen(Point p) {
	    set(p, SEEN);
	}	
	
	// post: toggles whether the position p is currently occupied
	public void toggleOccupied(Point p) {
	    toggle(p, OCCUPIED);
	}

	// post: remembers that position p is currently occupied
	public void setOccupied(Point p) {
	    set(p, OCCUPIED);
	}

	// post: forgets that position p is currently occupied
	public void clearOccupied(Point p) {
	    clear(p, OCCUPIED);
	}
	////////////////////////////////////////////////////////////////


	/**
	 * post: returns the direction corresponding to the trail set
	 * at p. If p has no pre-set exit direction, but is solved,
	 * then an exit direction is calculated, set and returned. If
	 * p is impassable, null is returned.
	 */
	public Direction exitDirection(Point p) {
	    if (getVisited(p)) {
		for (Direction d : Direction.values()) {
		    if (get(p, directionBit(d)<<TRAIL_SHIFT)) {
			return d;
		    }
		}
	    } 
	    return null;
	}
	
	/**
	 * post: returns the position corresponding to exitDirection.
	 */
	public Point exitPosition(Point p) {
	    Direction exitDir = exitDirection(p);
	    return (exitDir == null) ? null : exitDir.forward(p);
	}
	
	/**
	 * post: set a trail, but only if there isn't already one set,
	 * or the direction d from this position is not 'checked'.
	 */
	public boolean suggestTrail(Point p, Direction d) {
	    if (!getVisited(p) && !getChecked(p,d)) {
		setTrail(p, d); 
	    } 
	    return d == exitDirection(p);
	}

	/**
	 * post: sets a new exit for position p, then causes
	 * everything that was in p's trail to exit in that new
	 * direction instead of towards the root
	 */
	protected void flipTrail(Point p, Direction d) {
	    Point currentPos = p;
	    Direction exitDir = d;
	    while (!getRoot(currentPos)) {
		Point nextPos = exitPosition(currentPos);
		
		// reset the trail at currentPos
		clearTrail(currentPos);
		setTrail(currentPos, exitDir);
		
		// the exit direction for nextPos leads to currentPos
		exitDir = Direction.fromTo(nextPos, currentPos);
		currentPos = nextPos;
	    }
	    // There is no trail to clear in a root position
	    setTrail(currentPos, exitDir);

	}

	/**
	 * post: resets the root ID of every position in p's trail.
	 */
	protected void fixRootId(Point p, int newId) {
	    assert getRootId(p) != 0 : ""+p+" has no root";
	    fixRootId(p, getRootId(p), newId);
	}
	
	/**
	 * helper method for the above // TODO implement itteratively
	 */
	protected void fixRootId(Point p, int oldId, int newId) {
	    setRootId(p, newId);
	    for (Direction d : Direction.values()) {
		Point q = d.forward(p);
		if (getRootId(q) == oldId) {
		    fixRootId(q, oldId, newId);
		}
	    }

	}

	/**
	 * post: evaluates every direction from p.
	 */
	protected void evaluatePosition(Point p) {
	    for (Direction d : Direction.values()) {
		evaluateDirection(p,d);
	    }
	}
	
	/**
	 * pre: no position adjacent to p has a different root (NOT ENFORCED)
	 *
	 * post: sets direction d of p checked, if the conditions to
	 * mark p checked are fulfilled:
	 *
	 * - The foward position must have been seen.
	 *
	 * - The foward position is part of a trail, but does not exit
         *   into p (this avoids loops)
	 *
	 * - OR The foward position is a root
	 *
	 * - OR The foward position is solved
	 */
	protected void evaluateDirection(Point p, Direction d) {
	    if (getSeen(d.forward(p))
		&& ((!getTrail(d.forward(p), d.opposite())
		     && getRootId(d.forward(p)) != 0)
		    || getRoot(d.forward(p))
		    || getSolved(d.forward(p)))) {
		setChecked(p,d);
	    }
	}
	
	/**
	 * post: returns the distance from p to the nearest known
	 * line-of-site obstruction in direction d such that every
	 * point between p and that obstruction has been seen. returns
	 * -1 if there is no such wall
	 */
	public int wallDistance(Point p, Direction d) {
	    for (Point q = d.forward(p); getSeen(q) && !getOccupied(q); q = d.forward(q)) {
		if (get(q,WALL) || getOccupied(q)) {
		    return Creature.distance(p,q);
		}
	    }
	    return -1;
	}
	
	/**
	 * post: returns the distance from p to the nearest visited
	 * location in direction d such that every point between p and
	 * that visited location has been seen, and is not
	 * oppupied. returns -1 if there is no such position
	 */
	public int trailDistance(Point p, Direction d) { 
	    for (Point q = d.forward(p); getSeen(q) && inBounds(q); q = d.forward(q)) {
		if (getVisited(q)) {
		    return Creature.distance(p,q);
		}
	    }
	    return -1;
	}

	/**
	 * post: returns a String describing the
	 * interpretation of the flag for p
	 */
    	public String solveState(Point p) {
	    String s = 
		"solve state at " + Creature.toString(p) + " "
		+ (getSolved(p) ? "Solved. " 
		   : getVisited(p) ? "Visited. " 
		   : getSeen(p) ? "Seen. "
		   : "") 
		+ "\n  Checked directions: ";
	    for (Direction d : Direction.values()) {
		if (getChecked(p, d)) {
		    s += d + "  ";
		}
	    }
	    return s;
	}
	
	/**
	 * post: for debugging - returns the data at p as a binary
	 * string
	 */
	public String toBinaryString(Point p) {
	    return Integer.toBinaryString(map[p.x][p.y]);
	}
    }
    

    //Used for debugging
    /**
     * post: updates the debug message displayed by this creature's
     * toString() method.
     *
     * notes: Appends 'str' to the debug message. If the position or
     * direction at the time 'str' is appended is different then what
     * it was last time addMessage was called, a new header is
     * appended first.
     *
     * This header displays the time (in ms) the header was created,
     * current position, current direction, and the top of the trail
     * stack.
     * 
     */
    private void addMessage(String str) {
	if (DEBUG) {
	    Point pos = getPosition();
	    Direction dir = getDirection();
	    messages += 
		((pos.equals(lastMessagePos) && dir.equals(lastMessageDir)) ? "" 
		 :("\n" + (getGameTime()/1000000) + "ms: "
		   + toString(pos) + " " + dir 
		   + ". exit: " + map.exitDirection(pos)
		   + "\n  " + map.solveState(pos)
		   + "\n"))
		+ str + "\n";
	    while (messages.length() > MAX_MESSAGE_LENGTH) {
		messages = messages.substring(50);
	    }
	    lastMessagePos = pos;
	    lastMessageDir = dir;
	}
    }
}