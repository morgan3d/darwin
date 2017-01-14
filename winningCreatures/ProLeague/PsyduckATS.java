//  Copyright 2011 by April Shen
//	Written for The Darwin Game by Morgan McGuire.
//
import java.util.*;
import java.awt.*;

public class PsyduckATS extends Creature {
	
	private static Random gen = new Random();
	private static HashMap<Integer,PsyduckATS> friends = new HashMap<Integer,PsyduckATS>();
	
	private int leader;
	private boolean isExplorer;
	private int numTurns;		//number of rotations w/o seeing leader
	private int numDelays;		//number of delays w/o moving
	
	public PsyduckATS() {
		leader = 0;
		if (gen.nextInt(100) < 20)
			isExplorer = true;
		else
			isExplorer = false;
		numTurns = 0;
		numDelays = 0;
	}
	
    public void run() {
		friends.put(getId(),this);
		
        while (true) {
			Observation ob = look();
			switch (ob.type) {
				case WALL:
				case THORN:
					navigate(ob.position);
					break;
				case CREATURE:
					if (isEnemy(ob))
						fight(ob);
					else {
						if (checkLeader() && !isExplorer)
							follow(ob);
						else
							turn();
					}
					break;
				default: break;
			}
			if (gen.nextInt(100)<15)
				isExplorer = !isExplorer;
        }
    }
	
	//Act in response to observing a wall/thorn
	private void navigate(Point pos) {
		int dist = distance(pos);
		if (dist > 1) {
			if (isExplorer)
				dist -= gen.nextInt(dist); //leave possibility of finding side passages
			move(dist);
		}
		turn();
	}
	
	//Act in response to observing an enemy
	private void fight(Observation ob) {
		if (distance(ob.position) == 1) {
			attack();
			numDelays = 0;
		}
		else {
			if (ob.classId == APPLE_CLASS_ID) {
				move(distance(ob.position));
				attack();
			}
			else if (ob.classId == FLYTRAP_CLASS_ID) {
				if (Simulator.TURN_COST*numLeftTurnsTillFacing(ob.direction)			//time to face me 
					> Simulator.MOVE_FORWARD_COST*(distance(ob.position)-1)				//time to reach it
					|| Simulator.TURN_COST*(numLeftTurnsTillFacing(ob.direction)+4)	 
					> Simulator.MOVE_FORWARD_COST*(distance(ob.position)-1)	) {	//far flytraps
					move(distance(ob.position));
					attack();
				}
				else
					delay();
			}
			else {
				if (Simulator.TURN_COST*(ob.direction.numTurns(getDirection().opposite()))		//time to face me 
					> Simulator.MOVE_FORWARD_COST*(distance(ob.position)-1)) {	//time to reach it
					move(distance(ob.position));
					attack();
				}
				else if (ob.direction.opposite() != getDirection() //doesn't see me
						 || (numDelays > 10)) { //stalemated
					move(distance(ob.position)/2);
				}
				else {
					delay();
					numDelays++;
				}
			}
		}
	}
	
	//Act in response to observing a friend
	private void follow(Observation ob) {
		if (distance(ob.position) > 1)
			move(distance(ob.position));	
		leader = ob.id;
		while (leader != 0) {
			Observation ob2 = look();
			switch (ob2.type) {
				case WALL:
				case THORN:
					if (isExplorer || numTurns > 4 || gen.nextInt(100) < 20) {
						navigate(ob2.position);
						leader = 0;
						return;
					}
					else {
						turn();
						numTurns++;
					}
					break;
				case CREATURE:
					if (isEnemy(ob2)) {
						if (distance(ob2.position) < 3
							|| (ob2.classId == APPLE_CLASS_ID) || (ob2.classId == FLYTRAP_CLASS_ID)) {
							fight(ob2);
							leader = 0;
							return;
						}
					}
					else {
						if (ob2.id == leader) {
							if (distance(ob2.position) > 1)
								move(distance(ob2.position));
							numTurns = 0;
							if (gen.nextInt(10)<5)
								turn();
						}
						else {
							follow(ob2);
						}
					}
					break;
				default: break;
			}
		}
		attack();
	}
	
	//
	private boolean checkLeader() {
		boolean isLeader = isLeader();
		if (isLeader && gen.nextInt(10) < 4)
			isExplorer = true;
		return !isLeader || (gen.nextInt(10) < 5);
	}
	
	//Returns true iff leader of someone
	private boolean isLeader() {
		for (PsyduckATS p : friends.values())
			if (p.leader == getId())
				return true;
		return false;
	}
	
	//Returns number of left turns an enemy straight ahead needs to go from d to facing me
	private int numLeftTurnsTillFacing(Direction d) {
		int i = 0;
		while (d.left(i) !=	getDirection().opposite())
			i++;
		return i;
	}
	
	//Turn in some direction
	private void turn() {
		if (gen.nextBoolean())
			turnLeft();
		else
			turnRight();
	}
	
	//Specialized move that checks for enemies
	private void move(int dist) {
		for (int i=1; i<dist; i++)
			if (!moveForward()) {
				attack();
				return;
			}
	}
	
	//Tells its followers that it's dead :(
	public void onDeath() {
		friends.remove(getId());
		for (PsyduckATS p : friends.values()) {
			if (p.leader == getId())
				p.leader = 0;			//tells p that leader dead
		}
	}
	
    public String getAuthorName() {
        return "April Shen";
    }
	
    public String getDescription() {
        return "Psyduck: it looks like a platypus.";
    }
}
