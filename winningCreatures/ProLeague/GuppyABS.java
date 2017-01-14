/**
   Herd creature. Enjoys safety in numbers. Has rare moments of recklessness...
   The meek shall inherit the Earth.
   
   By Aaron Size
 *
 *
moveBackward    4
moveForward     2
delay           .5
attack          4
look            1
turnLeft        3
turnRight       3
 *
 *
 */
import java.util.*;
// import java.lang.Math.*;

public class GuppyABS extends Creature {
    private String msg = " History: ";
    private Random random = new Random();
    private int targetFreshness = 140000000; // how long before target is forgotten
    private Observation observed;
    private boolean oldTarget = true;

    static boolean turnDirection = true; // T=Right, F=Left
    static int globalBoredCycles = 0;
    //static Observation TARGET = new Observation(22, 22, 0);
    static Observation TARGET ;
    //static Observation TARGET = null;

    public void run() {
        try {
            int eDist = 0;
            int sDist = 0;
            boolean lastTurn = turnDirection;
            
            boolean goEast = true;
            boolean goSouth = true;
            if (oldTarget == true) {
                //TARGET = observeSelf();
                TARGET = new Observation((getMapDimensions().width/2), (getMapDimensions().height/2), getGameTime());
                oldTarget = false;
            }

            while (true) {
                if (!oldTarget&(getGameTime() > (TARGET.time + targetFreshness))){ // check for stale target
                    //msg=msg+"-";
                    oldTarget = true;
                    }   else{
                    //msg=msg+".";
                    }
                
                if (facingTarget()) {
                    attack();
                    oldTarget = true;
                }
                if (!oldTarget) gotoPosition(); // fresh target? go!
                else {                          // No target. Scan for targets
                    wander();
                    }
                }
          
      } catch (ConvertedError e) {}
}

    protected boolean wander() {
        observed = look();
        if (isEnemy(observed)) {
            TARGET = observed;
            oldTarget = false;
            return false;
        }
        randomTurn();
        smartMove();
        return true;
    }

    public void onDeath() {
            // "I DIE!"
            TARGET = observeSelf();
            oldTarget = false;
    }

    protected boolean gotoPosition() {
    observed = look();

    if (distance(observed.position) == 1) { // Something is right in front of me:
        if (isEnemy(observed)) {
            //msg=msg+"A";
            attack();
            return true;
        }
        // BLOCKED!:
        if ((getDirection().toInt() - directionsTo()[0].toInt()) == 0) { // shortest direction to target is forward (AND BLOCKED)
           switch ( getDirection().toInt() - directionsTo()[1].toInt() ) { // seek out longest direction to target
                case -2:
                case 2:  // target BEHIND. This shouldn't be possible
                case 0:  // target in FRONT (Target is in row or column)
                    if (turnDirection) {
                        //msg=msg+"B";
                        turnRight();
                        observed = look();
                        smartMove();
                        turnLeft();
                    } else {
                        //msg=msg+"C";
                        turnLeft();
                        observed = look();
                        smartMove();
                        turnRight();
                    }
                    turnDirection = !turnDirection;
                    break;
                case -1:
                case 3:  // target LEFT
                //msg=msg+"D";
                    turnLeft();
                    observed = look();
                    smartMove();
                    break;
                case -3:
                case 1:  // target RIGHT
                //msg=msg+"E";
                    turnRight();
                    observed = look();
                    smartMove();
                    break;
             }
             return true;
          }
    }
    // path is clear in front:
    if ((getDirection().toInt() - directionsTo()[1].toInt()) == 0) { // if already pointing in long direction, just go
        //msg=msg+"F";
            smartMove();
            return true;
    }
    switch (getDirection().toInt() - directionsTo()[0].toInt()) { // seek out shortest direction to target
        case 0:
        //msg=msg+"G";
            smartMove();
            break;
        case -1:
        case 3:  //target LEFT
        //msg=msg+"H";
                turnLeft();
                break;
        case -2:
        case 2:  //target BEHIND
        //msg=msg+"I";
                randomTurn();
                break;
        case -3:
        case 1:  //target RIGHT
        //msg=msg+"J";
                turnRight();
                break;
        }
    return true;
    }

    protected boolean facingTarget(){
        if (((distance(TARGET.position) == 1)&(directionsTo()[0].toInt()) == 0)) { // attack target blindly, skip the look, if close
            return true;
        }
        return false;
    }
    
    protected Direction[] directionsTo() {  // determines directions to the target, [shortest, longest]
                Direction[] tdir;
                tdir = new Direction[2];
                int eDist = (TARGET.position.x - getPosition().x);
                int sDist = (TARGET.position.y - getPosition().y);

                if (Math.abs(eDist) < Math.abs(sDist)) { // shortest distance is EW
                        if (eDist < 0) // set shortest
                            tdir[0] = Direction.WEST;
                        else 
                            tdir[0] = Direction.EAST;

                        if (sDist < 0) // set longest
                            tdir[1] = Direction.NORTH;
                        else 
                            tdir[1] = Direction.SOUTH;

                } else { // shortest direction is NS

                        if (sDist < 0) // set shortest
                            tdir[0] = Direction.NORTH;
                        else 
                            tdir[0] = Direction.SOUTH;

                        if (eDist < 0) // set longest
                            tdir[1] = Direction.WEST;
                        else
                            tdir[1] = Direction.EAST;
                }

                if ((Math.abs(eDist) == 0)|(Math.abs(sDist) == 0)) // handles same row/column
                    tdir[0] = tdir[1];
                if (Math.abs(eDist) == Math.abs(sDist)) // varies side of diagonal followed
                    if (turnDirection) {
                        Direction tmp;
                        tmp = tdir[0];
                        tdir[0] = tdir[1];
                        tdir[1] = tmp;
                        turnDirection = !turnDirection;
                    }
                return tdir;
    }

    protected boolean randomTurn() {
        switch (random.nextInt(2)){

            case 0:
                turnRight();
            case 1:
                turnLeft();
        }
        return true;
    }

    protected boolean smartMove() {
        if (distance(observed.position) == 1){ // something in the way?
            if (observed.type == Type.THORN)
                return false;
            
            if (isEnemy(observed)) {
                attack();
                return false;
            } else
                if (moveForward())
                    return true;
                else
                    return false;
            
         } else { // path is clear:
            if (isEnemy(observed)) {
                 if (oldTarget == true) {
                    TARGET = observed;
                    oldTarget = false;
                 }
                 if ((distance(observed.position) >= 3)& isFacingMe()){
                    attack();
                 } else {
                    if (moveForward())
                        return true;
                    else
                        return false;
                 }
         } else {
                    if (moveForward())
                        return true;
                    else
                        return false;
         }
        }
	return false;
    }
	
    protected boolean isFacingMe() {
      if ((observed.type != Type.CREATURE) || (observed.className == "Apple")) { return false; }
      return (observed.direction.toInt() == ((getDirection().toInt() + 2) % 4));
    }
        
    public String getAuthorName() {
        return "Aaron Size";
    }

    public String getDescription() {
        return "Swarm creature. Converges on targets.";
    }

    public String toString() {
        if (getGameTime() < (TARGET.time + targetFreshness))
            return "Have Target! " + TARGET.position.toString() + "\n"+ oldTarget + " \n" + observed.position.toString() + " \n"+msg;
        else
            return "NO TARGET. (last): \n" +TARGET.position.toString()+ " \n"+ oldTarget+" \n "+  observed.position.toString() +" \n"+msg;
    }
}
