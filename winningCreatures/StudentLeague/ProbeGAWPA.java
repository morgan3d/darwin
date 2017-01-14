import java.awt.*;
import structure5.*;
import java.util.*;

/**
   This class is an implementation of a Creature for 
   natural selection type maps of the Darwin game. It
   pursues an exploration strategy and keeps some
   creatures playing defense. It was created by
   Greg White and Ben Athiwaratkun.
*/

public class ProbeGAWPA extends Creature {
    protected static int EMPTY = -1;
    protected static int TRAVELLED = -2;
    protected static int UNKNOWN = 0;
	
    protected final static int FRONT = 0;
    protected final static int LEFT = 1;
    protected final static int RIGHT = 3;
    protected final static int BACK = 2; 
	
    protected static HashSet<Point> occupiedDefensivePositions;
	
    protected static int[][] field;
    protected static int creatureCount = 0;
    protected static int defensiveCreatures = 0;
	
    protected boolean defender = false;
	
    protected static final int MAX_DEFENSE = 2;
	
    /**
       post: The run method will cause ProbeGAWPA to explore the map,
       killing enemies along the way.
    */
    public void run() {
	try{
	    creatureCount++;
			
	    if(field == null) {
		field = new int[getMapDimensions().width][getMapDimensions().height];
	    }
	    if(occupiedDefensivePositions == null) {
		occupiedDefensivePositions = new HashSet<Point>();
	    }
			
	    while(true) {
		for(int i=0; i<4; i++){
		    updateMap();
		    turnLeft();
		}
		goBestDirection();
	    }	
			
	} catch(ConvertedError e) {}
    }
    
    /**   
	  
     */
    protected void goBestDirection(){
	/* create a list for number of times ProbeGAWPA visited for all directions */
	// get values for adjacent positions in all four directions
	int valueFront = field[getFrontX()][getFrontY()];
	int valueLeft = field[getLeftX()][getLeftY()];
	int valueRight = field[getRightX()][getRightY()];
	int valueBack = field[getBackX()][getBackY()];
	int[] list = new int[4];
	list[FRONT] = valueFront; list[LEFT] = valueLeft;
	list[RIGHT] = valueRight; list[BACK] = valueBack;
		
	/* pass the list to helper method getBestDirection(list)
	   ,get the best direction, and move accordingly
	*/
	// executed indicates if we have executed the move in the respective direction
	boolean executed = false;
	int bestDirection = getBestDirection(list);
	if(bestDirection == FRONT){
	    if(incrementForward()){
		executed = true;
	    }
	} else if(bestDirection == LEFT){
	    turnLeft();
	    if(incrementForward()){
		executed = true;
	    }
	} else if(bestDirection == RIGHT){
	    turnRight();
	    if(incrementForward()){
		executed = true;
	    }
	} else {
	    turn180();
	    if(incrementForward()){
		executed = true;
	    }
	}
	
	// if we didn't go to the best direction, then just turn left and update map again
	if(!executed){
	    Observation obs = updateMap();
	    while(distance(obs.position) == 1) {
		turnLeft();
		obs = updateMap();
	    }
	    incrementForward();
	}
			
		
    }
	
    protected int getBestDirection(int[] list){
	//  bestDirection shoudld at least be the direction with no obstruction
	// (there exists at least one)
	int bestDirection = FRONT;
	for(int i = list.length - 1; i >= 0; i--){
	    if(list[i] != Creature.WALL_CLASS_ID && list[i] != Creature.THORN_CLASS_ID && !isDefensivePosition(i)){ 
		bestDirection = i;
	    }
	}
	// compare how many times travelled for each spot
	for(int i = 0; i < list.length; i++){
	    if(list[i] < 0 && list[i] > list[bestDirection]){
		bestDirection = i;
	    }
	}
	return bestDirection;
    }
	
    protected boolean isDefensivePosition(int direction){
	int xIncrement = xIncrement(direction);
	int yIncrement = yIncrement(direction);
	Point p = new Point(getPosition().x + xIncrement, getPosition().y + yIncrement);
	return occupiedDefensivePositions.contains(p);
    }
	
    // incrementForward incremenet TRAVELLED  if we moved successfully
    protected boolean incrementForward(){
	if(moveForward()){
	    // if we sucessfully move forward, then we increment TRAVELLED
	    field[getPosition().x][getPosition().y] = field[getPosition().x][getPosition().y] + TRAVELLED;
	    return true;
	} else {
	    return false;
	}
    }
	
    /*******************************************/	
    private Observation updateMap() {
		
	// Store all the information
	Observation obs = look();
		
	Direction dir = getDirection();
	int curX = getPosition().x;
	int curY = getPosition().y;
	int obsX = obs.position.x;
	int obsY = obs.position.y;
		
	if(dir == Direction.NORTH) {
	    for(int y = curY - 1; y > obsY; y--) {
		if(field[curX][y] == UNKNOWN) field[curX][y] = EMPTY;
	    }
	} else if(dir == Direction.SOUTH) {
	    for(int y = curY + 1; y < obsY; y++) {
		if(field[curX][y] == UNKNOWN) field[curX][y] = EMPTY;
	    }
	} else if(dir == Direction.EAST) {
	    for(int x = curX + 1; x < obsX; x++) {
		if(field[x][curY] == UNKNOWN) field[x][curY] = EMPTY;
	    }
	} else {
	    for(int x = curX - 1; x > obsX; x--) {
		if(field[x][curY] == UNKNOWN) field[x][curY] = EMPTY;
	    }
	}
		
		
	
		
	// if its an apple, go get it
	if(obs.classId == Creature.APPLE_CLASS_ID) {
	    while(distance(obs.position) > 1) {
		if(!moveForward()) {
		    attack();
		}
	    }
	    attack();
	}
		
	if(obs.classId == Creature.WALL_CLASS_ID || obs.classId == Creature.THORN_CLASS_ID) {
	    field[obsX][obsY] = obs.classId;
	}
		
	// if we're looking at a good defensive setup and we want more defenders
	// go play defense
	if(wallOrThorn(obsX,obsY) && defensiveCreatures < MAX_DEFENSE) {
	    defensivePosition(obs.position);
	}
		
	if(isEnemy(obs)) {
	    killEnemy(obs);
	}		

	return obs;
    }
	
    private void turn180() {
	turnLeft();
	turnLeft();
    }
	
    private void setUpDefense(Point seen, int typeOfSetup, Point defensivePosition) {
		
	occupiedDefensivePositions.add(defensivePosition);
		
	if(typeOfSetup == 1) {
	    while(distance(seen) > 1) {
		if(!moveForward()) attack();
	    }
	    turn180();
	    playDefense();
	} else if(typeOfSetup == 2) {
	    while(distance(seen) > 1) {
		if(!moveForward()) attack();
	    }
	    turnRight();
	    moveForward();
	    turnRight();
	    moveBackward();
	    playDefense();
	} else if(typeOfSetup == 3) {
	    while(distance(seen) > 1) {
		if(!moveForward()) attack();
	    }
	    turnLeft();
	    moveForward();
	    turnLeft();
	    moveBackward();
	    playDefense();
	}
    }
	
    // pre: looking at wall or thorn, and less than MAX_DEFENSE defenders
    private void defensivePosition(Point seen) {
	Direction dir = getDirection();
		
	int seenX = seen.x;
	int seenY = seen.y;
	
	if(dir == Direction.NORTH) {
	    // Case 1: looking right at defensive position
	    if(wallOrThorn(seenX - 1,seenY + 1) && wallOrThorn(seenX + 1,seenY + 1) && 
	       !occupiedDefensivePositions.contains(new Point(seenX, seenY + 1))) {
		setUpDefense(seen,1, new Point(seenX, seenY + 1));
	    } else if(wallOrThorn(seenX + 1, seenY - 1) && wallOrThorn(seenX + 2, seenY) &&
		      field[seenX + 1][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX + 1][seenY] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX + 1, seenY))) {
		// Case 2: defensive position to the right
		setUpDefense(seen,2, new Point(seenX + 1, seenY));
	    } else if(wallOrThorn(seenX - 1, seenY - 1) && wallOrThorn(seenX - 2, seenY) &&
		      field[seenX - 1][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX - 1][seenY] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX - 1, seenY))) {
		// Case 3: defensive position to the left
		setUpDefense(seen,3, new Point(seenX - 1, seenY));
	    }
	} else if(dir == Direction.SOUTH) {
	    // Case 1: looking right at defensive position                      
	    if(wallOrThorn(seenX - 1,seenY - 1) && wallOrThorn(seenX + 1,seenY - 1) &&
	       !occupiedDefensivePositions.contains(new Point(seenX, seenY - 1))) {
		setUpDefense(seen,1,new Point(seenX, seenY - 1));
	    } else if(wallOrThorn(seenX - 1, seenY + 1) && wallOrThorn(seenX - 2, seenY) &&
		      field[seenX - 1][seenY - 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX - 1][seenY] == Creature.EMPTY_CLASS_ID && 
		      !occupiedDefensivePositions.contains(new Point(seenX - 1, seenY))) {
		// Case 2: defensive position to the right
		setUpDefense(seen,2, new Point(seenX - 1, seenY));
	    } else if(wallOrThorn(seenX + 1, seenY + 1) && wallOrThorn(seenX + 2, seenY) &&
                      field[seenX + 1][seenY - 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX + 1][seenY] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX + 1, seenY))) {
		// Case 3: defensive position to the left                                
		setUpDefense(seen,3, new Point(seenX + 1, seenY));
	    }
	} else if(dir == Direction.WEST) {
	    // Case 1: looking straight at defensive position
	    if(wallOrThorn(seenX + 1, seenY - 1) && wallOrThorn(seenX + 1, seenY + 1) &&
	       !occupiedDefensivePositions.contains(new Point(seenX + 1, seenY))) {
		setUpDefense(seen,1, new Point(seenX + 1, seenY));
	    } else if(wallOrThorn(seenX - 1, seenY - 1) && wallOrThorn(seenX, seenY - 2) &&
		      field[seenX][seenY - 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX + 1][seenY - 1] == Creature.EMPTY_CLASS_ID && 
		      !occupiedDefensivePositions.contains(new Point(seenX, seenY - 1))) {
		// Case 2: defensive position on the right
		setUpDefense(seen,2, new Point(seenX, seenY - 1));
	    } else if(wallOrThorn(seenX - 1, seenY + 1) && wallOrThorn(seenX, seenY + 2) &&
		      field[seenX][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX + 1][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX, seenY + 1))) {
		// Case 3: defensive position on the left
		setUpDefense(seen,3, new Point(seenX, seenY + 1));
	    }
	} else {
	    // Case 1: looking straight at defensive position
	    if(wallOrThorn(seenX - 1, seenY - 1) && wallOrThorn(seenX - 1, seenY + 1) && 
	       !occupiedDefensivePositions.contains(new Point(seenX - 1, seenY))) {
		setUpDefense(seen, 1, new Point(seenX - 1, seenY));
	    } else if(wallOrThorn(seenX + 1, seenY + 1) && wallOrThorn(seenX, seenY + 2) &&
		      field[seenX][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX - 1][seenY + 1] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX, seenY + 1))) {
		// Case 2: defensive position on the right
		setUpDefense(seen,2, new Point(seenX, seenY - 1));
	    } else if(wallOrThorn(seenX, seenY - 2) && wallOrThorn(seenX + 1, seenY - 1) &&
		      field[seenX - 1][seenY - 1] == Creature.EMPTY_CLASS_ID &&
		      field[seenX][seenY - 1] == Creature.EMPTY_CLASS_ID &&
		      !occupiedDefensivePositions.contains(new Point(seenX, seenY - 1))) {
		// Case 3: defensive position on the left
		setUpDefense(seen,3, new Point(seenX, seenY - 1));
	    }
	}
    }
	
    private boolean wallOrThorn(int x, int y) {
	try {
	    return field[x][y] == Creature.WALL_CLASS_ID ||
		field[x][y] == Creature.THORN_CLASS_ID;
	} catch(Exception e) {
	    return false;
	}
    }
    
    private void playDefense() {
	defensiveCreatures++;
	defender = true;
	while(true) {
	    Observation obs = look();
	    if(distance(obs.position) == 1) {
		attack();
	    }
	}
    }
	
    public void onDeath() {
	if(defender) {
	    defensiveCreatures--;
	    occupiedDefensivePositions.remove(getPosition());
	}
	creatureCount--;
    }
	
    private void killEnemy(Observation obs) {
	int enemyId = obs.id;
	if(distance(obs.position) == 1) {
	    attack();
	} else {
	    while(obs.id == enemyId && distance(obs.position) > 2) {
		moveForward();
		obs = look();
	    }
	    
	    if(distance(obs.position) == 1) attack();
	    else {
		// attack strategy
		while(obs.id == enemyId && distance(obs.position) == 2 &&
		      !isVulnerable(obs)) {
		    // wait
		    obs = look();
		}
		// if the loop broke because the enemy became vulnerable,
		// kill it
		if(distance(obs.position) == 1) attack();
		else if(isVulnerable(obs)) {
		    moveForward();
		    attack();
		} 				
	    }
	}
    }
	
    private boolean isVulnerable(Observation o) {
	return (isEnemy(o) && distance(o.position) == 2 && !getDirection().equals(o.direction.opposite())); 
    }
	
    
    /**
       post: ProbeGAWPA turns to face
       @param d.
    */
    private void turnTo(Direction d) {
	int rightTurns = (getDirection().toInt() - d.toInt());
	if(rightTurns < 0) rightTurns += 4;
	switch (rightTurns) {	
	case 0:
	    break;
	case 1:
	    turnRight();
	    break;
	case 2:
	    turnRight();
	    turnRight();
	    break;
	case 3:
	    turnLeft();
	    break;
	}
    }
	
    /**
       post: returns the point that is one unit away from
       @param cur in Direction
       @param d.
    */
    private Point point(Point cur, Direction d) {
	if(d.equals(Direction.NORTH)) {
	    return new Point(cur.x, cur.y - 1);
	} else if (d.equals(Direction.EAST)) {
	    return new Point(cur.x + 1, cur.y);
	} else if(d.equals(Direction.SOUTH)) {
	    return new Point(cur.x, cur.y + 1);
	} else {
	    return new Point(cur.x - 1, cur.y);
	}
    }


    public String getAuthorName() {
	return "Greg White and Ben Athiwaratkun";
    }
	
    public String getDescription() {
	return "A natural selection creature that is based on Starcraft's Probe.";
    }
	
    /* directional helper methods */ 
    protected int xIncrement(int forwardDirection){
	if(forwardDirection == 3){
	    return 1;
	} else if(forwardDirection	== 1){
	    return -1;
	} else {
	    return 0;
	}
    }
	
    protected int yIncrement(int forwardDirection){
	if(forwardDirection== 0){
	    return -1;
	} else if(forwardDirection == 2){
	    return 1;
	} else {
	    return 0;
	}
    }    
	
    protected int getRightDirection(){
	// return the right direction(int) relative to the direction facing
	return (getDirection().toInt() + 4 - 1)%4;
    }
	
    protected int getLeftDirection(){
	// return the left direction(int) relative to the direction facing
	return (getDirection().toInt() + 1)%4;
    }
	
    protected int getLeftX(){
	// x coordinate of of the left (relative to the direction facing)
	return getPosition().x + xIncrement(getLeftDirection());
    }
	
    protected int getLeftY(){
	// y coordinate of the left
	return getPosition().y + yIncrement(getLeftDirection());
    }
	
    protected int getRightX(){
	return getPosition().x + xIncrement(getRightDirection());
    }
	
    protected int getRightY(){
	return getPosition().y + yIncrement(getRightDirection());
    }
	
    protected int getFrontX(){
	return getPosition().x + xIncrement(getDirection().toInt());
    }
	
    protected int getFrontY(){
	return getPosition().y + yIncrement(getDirection().toInt());
    }
	
    protected int getBackX(){
	return getPosition().x + xIncrement((getDirection().toInt() + 2)%4);
    }
	
    protected int getBackY(){
	return getPosition().y + yIncrement((getDirection().toInt() + 2)%4);
    }
    /* directional helper methods */	
	
	
    /*******************  ************************/
	
	
}
