/**BAKA JANAI
   Heidi and Nehemiah
   
*/
import java.util.*;
import java.awt.*;
//import structure5.NaturalComparator;

public class Bakajanai extends Creature {

    //instances used to represent the content of locations passed by                                                                                                                                                                        
    /**  protected static final int A_WALL = 1;
    protected static final int EMPTY = 2;
    protected static final int APPLE = 3;
    protected static final int THORN = 4;
    protected static final int FLYTRAP = 5;

    //represents direction                                                                                                                                                                                                                  
    protected int DIR = 1;

    //an array used to store the contents of the locations in the map                                                                                                                                                                       
    protected static int[][] map;
    */
    protected static int BakaCount = 0;
    protected int wallCount = 0;
    
    protected boolean moved;
    
    public Bakajanai() {
	
        ++BakaCount;
	
    }
    
    /**    //store information of the locations of the walls we've passed by
    protected void setWall(){
	map[getMovePosition().x][getMovePosition().y] = A_WALL;
    }*/
    
    
    public void run(){
	
	/**	if(map == null){
		int width = getMapDimensions().width;
		int height = getMapDimensions().height;
		
		//create a map                                                                                                                                                                                                                      
		map = new int[width][height];
}	*/	
	while(true){
	    moved = false;
	    
	    
	    bacaAct(); //this is a helper method containing primary set of actions.
	    
	}
    }
    
    
    /**    private class DistanceCom implements Comparator<Node> {
	public int compare(Node a, Node b){
	    
	    int disA = a.distance;
	    int disB = b.distance;
	    
	    return(new NaturalComparator().compare(disA, disB));
	}
    }
    
    private class Node{
	Node previous;
	Point point;
	int distance;
	Vector<Node> neighbor;
    
	public Node(Point p){
	    point = p;
	    
	    if(map[point.x + 1][point.y] == EMPTY){
		neighbor.add(new Node(new Point(point.x + 1 , point.y)));
	    }	   
	    if(map[point.x - 1][point.y] == EMPTY){
		neighbor.add(new Node(new Point(point.x - 1 , point.y)));
	    }	    
	    if(map[point.x][point.y + 1] == EMPTY){
		neighbor.add(new Node(new Point(point.x , point.y + 1)));
	    }	   
	    if(map[point.x][point.y - 1] == EMPTY){
		neighbor.add(new Node(new Point(point.x , point.y - 1)));
	    }
	    
	    distance = 100000; //any more elegant way of initializing it?
	}
    }

	
    

	
    public Stack<Point> findPath(Point current, Point target){
	Stack<Point> path = new Stack<Point>(); //stack that stores the nodes we need to go through to get the target

	PriorityQueue<Node> Q = new PriorityQueue<Node>(10, new DistanceCom());//stores the node we are at right now

	Node node = new Node(current); //the position where we start
	node.previous = null;	
	node.distance = 0;
	    
	Q.add(node);
	    
	while(!Q.isEmpty()){
	    Node p = Q.poll();//the position we want to check for shortest distance
		
	    //look through its neighbors and compute the distances of the neighbors from the source
	    for(int i = 0; i < p.neighbor.size(); ++i){
		Node n = p.neighbor.get(i);
		
		//if a neighbor is a target, we are done
		if(n.point.equals(target)){			
		    path.add(n.point);

		    while(n.previous != null){
			n = n.previous;
			path.add(n.point);
		    }

		}else{
		    int t = p.distance + 1;
			
		    if(t < n.distance){
			n.distance = t;
			n.previous = p;
			Q.add(n);
		    }
		}
	    }
	}
	return path;
    }
    */	    
    //Heidi, I've moved everything in the run method to a helper called bakaAct, so that we can abstract
    /*this way, if you have any ideas that you're not sure will work you can make your own helper method
      to test it out, without changing bacaAct, thus, we won't accidentally break our program...
    */
    //ALSO, since this compiles, let's not mess with it unless we are together collaborating
    public void bacaAct(){
	
	
	Observation obs = look();
	
	int obsID = obs.classId;
	
	//if the observed object is a creature
	//	if(obs.type == Type.CREATURE){	    
	
	//check if it's Bakajanai. If so, move towards it. If it's already right at the front, turn right
	if(!isEnemy(obs)){
	    moved = true;
	    wallCount = 0;
	    if(distance(obs.position) > 1){
		moveForward();
	    }else{
		turnRight();
	    }
	}else {
	    switch(obsID){
	    
	    case APPLE_CLASS_ID:
		moved = true;
	    wallCount = 0;
		//if it's an apple, we want to move towards it and eat it. Attack whatever moves in between.
	    //	map[obs.position.x][obs.position.y] = APPLE;
		
		while(!moveForward()){
		    attack();
		}
		break;
	    case THORN_CLASS_ID:
	    wallCount = 0;
		moved = true;
		//	map[obs.position.x][obs.position.y] = THORN;
		turnRight();
		
		//case FLYTRAP_CLASS_ID:
	    case WALL_CLASS_ID:
		++wallCount;
		moved = true;
		//move towards the wall. If we are at the wall already, turn right
		
		if(wallCount > 5){
		    getOut();
		}else if(distance(obs.position) > 1){
		    
		    moveForward();
		}else {
		    turnRight();
		}		
		break;
		
		//	    case TREASURE_CLASS_ID:
		
	    case FLYTRAP_CLASS_ID:
	    wallCount = 0;
		moved = true;
		//map[obs.position.x][obs.position.y] = FLYTRAP;                   
		turnRight();
		moveForward();
		break;
		
	    }
	    if(!moved){
		enemySight(obs);
		
	    }
	    
	}
    }
    
    
    //    protected void onDeath(){
    //	--bakaCount();
    //}
    
    public void getOut(){
	moveForward();
	int left, front, right, max;
	turnLeft();
	left = distance(look().position);
	turnRight();
	front = distance(look().position);
	turnRight();
	right = distance(look().position);
	
	if(left >= right){
	    if(left >= front){
		turnLeft();
		turnLeft();
		while(!moveForward()){
		    turnRight();
		}
	    }else{
		getOut();
	    }
	    
	}else{
	    if(right >= front){
		while(!moveForward()){
		    turnRight();
		}
	    }else{
		turnLeft();
		while(!moveForward()){
		    turnRight();
		}
	    }
	}
    }
    
    //if we see an enemy run this                                                                              //face direction of enemy                                                                                 //delay until distance small. attack                                                                 
    public void enemySight(Observation enemy){
	
        int enemyDistance = distance(enemy.position);
	
	if(enemyDistance == 2){
            delay();
        }else if(enemyDistance == 1){
            attack();
        }else{
	    moveForward();
	}
	
    }
    
    /**    //update the map when we look. 
     //use the direction and update the X or Y based on this
     public void updateMap(Observation look){
     Point end =  look.position;
     Point start = getPosition();
     
     int startY = (int)start.getY();
     int startX = (int)start.getX();
     int endY = (int)end.getY();
     int endX = (int)end.getX();

     
     int distance;
     
     if(getDirection() == Direction.NORTH ||    //if the direction is north or south modify the y
     getDirection() == Direction.SOUTH){
     
     distance = Math.abs(endY - startY);
     
     for(int i = 0; i < distance; i++){
     map[startX][startY + i] = EMPTY;  
     System.out.println("the updated value is" + map[startX][startY]);
     }
     
     }else{   //else if the direction is not, modify the x
     distance = Math.abs(endX - startX);
     
     for(int i = 0; i < distance; i++){
     map[startX + i][startY] = EMPTY;
     
     }
     
     
     }
     
     //NOTE: I switched everything to using CLASS_ID which is an int to build our map. 
     //before we were using something types, which didn't apply to apples or walls.
     //int obsId = look.classId;
     //if(obsId == APPLE_CLASS_ID){
     //  map[endX][endY] = APPLE_CLASS_ID;
     //}else if(obsId == WALL_CLASS_ID){
     //  map[endX][endY] = WALL_CLASS_ID;
     //}else if(obsId == THORN_CLASS_ID){
     //  map[endX][endY] = THORN_CLASS_ID;
     //}
     }
     
     
    */
    public String getAuthorName() {
	return "Heidi & Nehemiah";
    }
    
    public String getDescription() {
	return "not a foolish creature";
    }
}


