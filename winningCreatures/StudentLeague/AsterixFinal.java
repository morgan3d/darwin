/*
Shuai and Rumi
May 9, 2011  

Asterix, a creature that solves a maze, you can create multiple instances and they will help each other by sharing their maps
*/
  
import java.util.*;
import java.awt.Point;

public class AsterixFinal extends Creature {
    
    // Array "board" stores the number of times we've been to a space.
    // Array "radar" stores the positions of our creatures.
    public static int[][] board;    
    public static int[][] radar;
    
    public static int CreatureCount = 0;

   
    //instance variables that keeps track of how big the map is and where the creature is
    protected java.awt.Dimension MapDimensions;
    protected java.awt.Point CurrentPosition;
    
    // Constants that keeps track of the directions
    protected final int NORTH = 0;
    protected final int SOUTH = 2;
    protected final int EAST = 3;
    protected final int WEST = 1;
    
    protected boolean SeenApple = false;
    
    protected String status = "";
    
    
    public void run() {
	
	
	// make first move, which basically constructs the board array and turns randomly
	firstMove();
	
	// makes an observation and keeps track of what type that he sees
	Observation obs =look();
	Type frontType =obs.type;
	
	Point p = getPosition();
	radar[p.x][p.y] = myId();
	
	// Main loop that checks conditions and how we react
	while (true) {
       	    if (isEnemy(obs)){
		//If we see an enemy and it's right in front of us, attack!
		if (distance(p, obs.position) <= 1){
		    status="attacking";
		    attack();
		    obs=look();
		} else {
		    // If it's an apple, initiate getting an apple
		    if (obs.classId==APPLE_CLASS_ID|| obs.classId==FLYTRAP_CLASS_ID){
			SeenApple=true;
			status="getting apple";
			getApple(obs);
			obs=look();
			p = getPosition();
			// When enemy is one space away, we lunge for it and attack. It's really not a feint attack.
		    }else if (distance(p, obs.position) == 2){
			status="jedi mindtrick";
			feintAttack();
			obs=look();
			p= getPosition();
		    }else{
			// don't want to engage an enemy when we're alone...so we run away!
			if (nearAllyCount()<1){
			    status="explore";
			    escape();
			obs=look();
			p= getPosition();
			} else {
			    // When we are surrounded by comrades, we stare the enemies down. They die.
			    status="staring";
			    battleCry();
			    obs=look();
			    p = getPosition();
			}
		    }
		}
	    }else {
		// When we're by ourselves, we might as well make a reconassaince mission.
		if (nearAllyCount() <1  ){
		    // status="exploring";
		    explore(false);
		    obs=look();
		    p = getPosition();
		   
		} else {
		    // When we're not by ourselves, we'll mostly guard, but every once in a while, we explore. The boolean indicates that we break off from the group.
		    if (Math.random()<0.2){
			explore(true);
			obs=look();
			p=getPosition();
		    }
		    
		    else{
			//status="guarding";
			guard();
			obs=look();
			p = getPosition();
			}
		}
	    }
	}
    }

    // The misnomer, "feintAttack" actually attacks the enemy. It is quick and does not getPosition because keeping our time costs low is the key to success.
    protected void feintAttack(){
	moveForward();
	attack();
    }

    // This calculates the number of walls surrounding a creature.
    protected int numNearWall(Point p){
	int count =0;
	if (board[p.x][p.y+1]==-1){count++;}
	if (board[p.x][p.y-1]==-1){count++;}
	if (board[p.x+1][p.y]==-1){count++;}
	if (board[p.x-1][p.y]==-1){count++;}
	return count;
    }
    
    // Guard state that, when a creature sees an enemy, stares it down. Otherwise, it keeps spinning and winning. It rarely randomly breaks off just to keep from being predictable.
    protected void guard(){
	Observation obs= look();
	while (!isEnemy(obs)){
	    if (Math.random()<0.001 ){break;}
	    turnLeft();
	    obs=look();
	}
    }
 
    // Explore is a large method: it calculates the best direction to turn and moves the creature around the space.
    protected void explore(boolean breakoff){
	Point point=getPosition();
	// this while loop ensures that we do not execute "explore" unless we're alone. It also allows some booleans that are passed to make the creatures explore randomly.
	while ( nearAllyCount()<1 ||(breakoff&& Math.random()<0.9)){
	   
	    int CurrentX = (int)point.getX();
	    int CurrentY = (int)point.getY();
	    Observation obs = look();
	    Type frontType = obs.type;
	    board[CurrentX][CurrentY]++;		
	    
	    // Find the best direction it should turn (the neighboring space with the lowest value
	    int bestDirection = findBestDirection(CurrentX, CurrentY);
	    int currentDirection = getDirection().toInt();
	
	    // If the direction we should be in is different from the direction we are currently facing, turn.
	    // Whenever we turn, we look again
	    if (bestDirection != currentDirection){
		turnTo(currentDirection, bestDirection);
		obs =look();
		
		frontType = obs.type;
		// break out of explore because we see an enemy and want to become defensive
		if (isEnemy(obs)){break;}
	    }
	    
	    // if we see a wall or a thorn, set the observed position as -1 so we won't run into it
	    if (frontType == Type.WALL || frontType == Type.THORN) {
		board[(int)obs.position.getX()][(int)obs.position.getY()]= -1;
	    }	    
	    int count=0;
	    // In the case that we see a wall, thorn or creature that's right by us, we can't go anywhere
	    // keep finding the best direction and then turn until the front is clear
	    while (distance(getPosition(), obs.position) == 1){
		
		// set wall and thorn as -1
		if( frontType==Type.WALL || frontType==Type.THORN){
		    board[(int)obs.position.getX()][(int)obs.position.getY()]=-1;
		}
	
		// we'll increment the observation's space in order to avoid the situation 
		// when multiple creatures heading opposite ways in a narrow corridor will become stuck
		else if((frontType==Type.CREATURE) && (obs.classId==myClassId())){
		    board[(int)obs.position.getX()][(int)obs.position.getY()] = board[(int)obs.position.getX()][(int)obs.position.getY()]+1;
		}
	    
		// Everytime through this while loop, meaning that until we get out of facing an obstable, keep turning
		bestDirection= findBestDirection(CurrentX, CurrentY);
		currentDirection = getDirection().toInt();
		// If we turn, look
		if (bestDirection!= currentDirection){
		    turnTo(currentDirection, bestDirection);
		    obs=look();
		
		    frontType=obs.type;
		    if (isEnemy(obs)){break;}
		}
		//Count just prevents program from going into an infinte loop
		if (count>10){
		    break;
		}
		count++;
	    }
	    //moveCreature moves the creature and updates radar, as well
	    moveCreature();
	    point=getPosition();
	}

    }
       
    //While we have an enemy in sight, and the distance between us is greater than 2, we stare them down.
    //If an enemy is one space away, we move forward and attack (feintAttack)
    //If the enemy is right in front of us....we definitely attack.
    protected void battleCry(){
	Observation obs = look();
	Point p = getPosition();
	while (isEnemy(obs) ){
	    if (distance (obs.position,p) >2){
		obs= look();
	    }else if(distance (obs.position,p)==2) {
		feintAttack();
		obs=look();
	    }
	    else {
		attack();
		obs=look();
	    }
		
	}
	
    }

    // Eat the apple. An apple a day keeps the enemies away. 
    protected void getApple(Observation obs){
	
	int d = distance(obs.position) - 1;
	// Move until the far edge
	for (int i = 0; i < d; ++i) {
	    if (! moveCreature()) {
		// We hit something unexpected
		attack();
		break;
	    }
	}
    }

    // Turn 180 degrees, and run away!
    protected void escape(){
	turnLeft();
	turnLeft();
	while(moveCreature()){
	}
    }

    // The number of allies in the 8 spaces around us
    protected int nearAllyCount (){
	Point p = getPosition();
	int CurrentX= (int)p.getX();
	int CurrentY= (int)p.getY();
	int allycount= 0;
	for (int x = CurrentX-1; x <= CurrentX+1; x++){
	    for (int y = CurrentY-1; y<= CurrentY+1; y++){
		if((radar[x][y] != myId())&& (radar[x][y] != 0)) {
		    allycount++;
		}
	    }
	}
	return allycount;
    }
    
    //This is a debugging method that helped us keep track of our positions
    protected void printRadar(){
	String s = "";
	for (int y = 0; y < (int)MapDimensions.getHeight(); y++){
	    for (int x=0; x<(int)MapDimensions.getWidth(); x++){
		if(radar[x][y]!=-1||radar[x][y]!=10){
		    s=s+"  "+radar[x][y];
		        
		}else{
		    s=s+radar[x][y];
		}
	    }
	    s=s+"\n";
	}
	System.out.println(s);
    }

    //Actually moves Asterix forward and updates the radar
    protected boolean moveCreature(){
	Point old = getPosition();    
	boolean b = moveForward();
	    	    
	if(b) {
	    Point p = getPosition();
	    if(radar[old.x][old.y] == myId()) {
		//Nobody else moved into my old spot, so clear it
		radar[old.x][old.y] = 0;
	    }
	    //Record my new spot in radar
	    radar[p.x][p.y] = myId();
	    board[p.x][p.y]++;
	    // If we are surrounded on 3 sides by wall, then we're in a little crevice that no one can enter and 
	    // that we can guard. We just keep attacking from this protected position. Method is "guardCorner."
	    if (numNearWall(p)>=3){guardCorner();}
	}
	return b;
    }

    // guardCorner executes when our creature is protected on three sides. We don't want to leave this shielded position
    // because everytime an enemy tries to attack us, we have the advantage and kill them.
    protected void guardCorner(){
	turnLeft();
	turnLeft();
	Observation obs = look();
	//While we are running and the enemy is in front of us, attack it.
	while (true){
	    if (isEnemy(obs)&& distance(obs.position, getPosition())<2) {
		attack();
	    }
	    obs=look();
	}
    }

    //This causes our creatures to create flanks and band together.
    protected void herdForward(){
	int numAlly = nearAllyCount();
	CurrentPosition = getPosition();
	//If there's at most 2 of us, we want to move around more.
	if (numAlly <= 1){
    	    moveCreature();
	}
	// If there's more than 2 of us, then we move in the best possible direction.
	else {
	    Double randomNum = Math.random();
	    if (randomNum < 1){
		Point point = getPosition();
		int bestDirection = findBestDirection((int)point.getX(), (int)point.getY());
		int currentDirection = getDirection().toInt();
		if(bestDirection != currentDirection) {
		    turnTo(currentDirection, bestDirection);
		} else { 
		    moveCreature();
		
		}
	    }
	}
    }

    //pre: board is constructed
    //post: returns a string representation of the array board, will slow the creature dramatically if called
    protected void printBoard(){
	String s = "";
	for (int y = 0; y < (int)MapDimensions.getHeight(); y++){
	    for (int x=0; x<(int)MapDimensions.getWidth(); x++){
		if(board[x][y]!=-1||board[x][y]!=10){
		    s=s+"  "+board[x][y];
	    
		}else{
		    s=s+board[x][y];
		}
	    }
	    s=s+"\n";
	}
	System.out.println(s);
    }
    
    // Pre: current and goal are different  directions that are represented by ints between 0 and 3
    // Post: turn the creature
    protected void turnTo (int current, int goal){
	if (Math.abs(current-goal)==2){
	    Observation obs= look();	    
	    turnLeft();
	    if(!isEnemy(obs));
	    {turnLeft();}
	} else if (current-goal==1 || (current == 0 && goal == 3) ){
	    turnRight();
	} else {
	    turnLeft();
	}
    }   

    // Pre: Helper method of findBestDirection that returns the initial direction.
    // Post: gets the current direction and randomly changes the direction. This is implemented
    // to introduce randomness into the program as findBestDirection is determinstic.
    protected int initialDirection(){
	int DirectionInt = getDirection().toInt();
	Double randomNum=Math.random();
	if (randomNum<0){
	} else if (randomNum>=0.5 && randomNum<1) {
	    DirectionInt=(DirectionInt+3)%4;
	}else{
	    DirectionInt=(DirectionInt+1)%4;
	}
	return DirectionInt;
    }

    //Pre: CurrentX and CurrentY are the position of the creature, the board must be constructed
    //Post: find the best direction to turn to.
    protected int findBestDirection(int CurrentX, int CurrentY){
	// Get a random intial direction
	int DirectionInt = initialDirection();

	// Creates a mini array that reads the count number that's north, south, east, and west of the current position
	// if out of bounds (won't happen unless the maze is not enclosed), store -1 instead of the count
	int [] directionalValue = new int [4];
	if (CurrentY-1 > 0){
	    directionalValue [NORTH] = board[CurrentX][CurrentY-1];
	}else{
	    directionalValue [NORTH] = -1;
	}
	if (CurrentY+1 < MapDimensions.getHeight()){
	    directionalValue [SOUTH] = board[CurrentX][CurrentY+1];
	}else {
	    directionalValue[SOUTH]=-1;
	}
	if (CurrentX+1 < MapDimensions.getWidth()){
	    directionalValue [EAST] = board[CurrentX+1][CurrentY];
	}else {
	    directionalValue[EAST]=-1;
	}
	if (CurrentX-1>0){
	    directionalValue [WEST] = board[CurrentX-1][CurrentY];
	}else{
	    directionalValue[WEST]=-1;
	}
	
	// Now to find the direction that it should turn to, we guess that that direction is the initial direction
	// (DirectInt) and we'll run a for loop see if other neighboring spaces have a better value. 
	int minValue= directionalValue [DirectionInt];
	int minDirection = DirectionInt;
	int NextDirection = (DirectionInt+1)%4;

	// for loop that checks other direction. i makes sure that it runs 3 times (for the 3 other directions),
	// and NextDirection  actually gives that direction as an int.
	// NextDirrection is incremented and mod 4 is used to ensure that it never exceeds 3
	for ( int i = 1; i <= 3; i++  ){
	    // if minValue is -1, we know there's a wall in minDirection, we immediately swap,
	    if ( minValue==-1 || (directionalValue[NextDirection] != -1 && directionalValue[NextDirection]<minValue) ){
		minValue=directionalValue[NextDirection];
		minDirection = NextDirection;
	    } 
	    NextDirection= (NextDirection+1)%4;
	}

	return minDirection;
    }

    // Post: construct the board, set the CurrentPositioninstance variable, and turns randomly
    protected void firstMove(){	
	CreatureCount++;
	MapDimensions = getMapDimensions();
	if ((board== null) && (radar==null)){
	    constructBoard();
	}
    }
    
    // Pre: creature is alive
    // Post: turns randomly
    protected void RandomTurn(){
	Double randomNum=Math.random();
	if (randomNum<0.25){
	
	} else if (randomNum>=0.25 && randomNum<0.50) {
	    turnLeft();
	
	}else if (randomNum>=0.50 && randomNum < 0.75){
	    turnLeft();
	    turnLeft();
	}else{
	    turnRight();
	}
		
    }

    // Post: makes the array, the edges are assigned 10 as they are usually walls, so we're not going to look at them
    // unless we have to
    protected void constructBoard() {
	
	int x = (int)MapDimensions.getWidth();
	int y = (int)MapDimensions.getHeight();

	board= new int [x] [y];
	radar= new int [x] [y];
	for (int i = 0; i< x;i++){
	    board[i][0]=-1;
	    board[i][y-1]=-1;
	}
	for (int i=0; i<y; i++){
	    board[0][i]=-1;
	    board[x-1][i]=-1;
	}

    }

    public String getAuthorName() {
        return "Shuai and Rumi";
    }

    public String getDescription() {
        return status;
    }

}
