import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;

/** Minimal creature that blindly moves and attacks.*/
public class GhostCSW extends Creature {

    // shared data
//    private static int[][] allyMap = null;
//    private static int mapX;
//    private static int mapY;

    // instance data
//    private Point myPos;
//    private Point lastPos;

    private double turnChooser;
    private int turnCounter = 0;

    private boolean isExplorer = true;

    private Direction lastSuccessfulMoveDir = null;



    public void run() {

//        if (allyMap == null)
//        {
//            java.awt.Dimension mapdim = getMapDimensions();
//            mapX = mapdim.width;
//            mapY = mapdim.height;
//            allyMap = new int[mapX][mapY];
//        }

//        myPos = new Point(0,0);
//        lastPos = new Point(0,0);

        turnChooser = Math.random();

        myMakeTurn();

        while (true) {
//            myPos = getPosition();
//            allyMap[lastPos.x][lastPos.y] = -1;
//            allyMap[myPos.x][myPos.y] = getDirection().toInt();

            Observation obs = look();
            int spacesAhead = distance(obs.position) - 1;

            if (spacesAhead == 0)
            {
                if (isEnemy(obs)) // STRIKE
                {
                    attack();
                } else
                if ((obs.type == Type.WALL) || (obs.type == Type.THORN))
                {
                    myMakeTurn();
                } else
                {
                    myMakeTurn();
                }
            } else
            if (isEnemy(obs))
            {
                if (obs.className == "Apple") // APPLE EATER
                {
                    while (myMoveForward()) {}
                } else
                if (isFacingMe(obs))
                {
                    if (spacesAhead < 3) // AMBUSH
                    {
                        delay();
                    } else
                    {
                        myMoveForward(); // STALK
                    }
                } else
                if (getDirection() == obs.direction)
                {
                    myMoveForward();  // FOLLOW
                } else
                {
                    if (spacesAhead == 1) // POUNCE
                    {
                        myMoveForward();
                        attack();
                        myMakeTurn();
                    } else  // MOVE TOWARDS
                    {
                        for (int i = 1; i < spacesAhead; i++)
                        {
                            if (! myMoveForward()) { break; }
                        }
                    }
                }
            } else
            {
                if (obs.className == getClassName())
                {
                    if (isExplorer) // DISCOVER
                    {
                        myMakeTurn();
                    } else // CLUSTER
                    {
                        for (int i = 0; i < spacesAhead; i++)
                        {
                            if (! myMoveForward()) { break; }
                        }
                    }
                } else // DRIFT
                {
                    int start = 0;
                    if (! isExplorer) // non-explorers move less than the full distance, usually
                    {
                        start = (int)((Math.random() * (double)spacesAhead)+1);
                    }
                    for (int i = start; i < spacesAhead; i++)
                    {
                        if (! myMoveForward()) { break; }
                    }
                    myMakeTurn();
                }
            }

            //lastPos.x = myPos.x;
            //lastPos.y = myPos.y;

            if (Math.random() < .1) { isExplorer = false; }
            if (Math.random() < .01) { isExplorer = true; }
        }
    }

    public String getAuthorName() {
        return "Chris Warren";
    }

    public String getDescription() {
        return "Independent actors with moderate clustering and reasonably smart attacks";
    }

    protected boolean isFacingMe(Observation obs)
    {
      if ((obs.type != Type.CREATURE) || (obs.className == "Apple")) { return false; }
      return (obs.direction.toInt() == ((getDirection().toInt() + 2) % 4));
    }

    protected boolean isFacing(Direction d)
    {
       return (d.toInt() == ((getDirection().toInt() + 2) % 4));
    }

    protected boolean myMoveForward()
    {
        if (! moveForward()) {
            attack();
//            lastSuccessfulMoveDir = null;
//            myMakeTurn();
            return false;
        }
        lastSuccessfulMoveDir = getDirection();
        return true;
    }

    protected void myMakeTurn()
    {
        if (turnChooser < .5) {
            turnLeft();
            if ((isExplorer) && (lastSuccessfulMoveDir != null) && (isFacing(lastSuccessfulMoveDir)))
            {
                lastSuccessfulMoveDir = null;
                turnLeft();
            }
        } else
        {
            turnRight();
            if ((isExplorer) && (lastSuccessfulMoveDir != null) && (isFacing(lastSuccessfulMoveDir)))
            {
                lastSuccessfulMoveDir = null;
                turnRight();
            }
        }

        turnCounter++;
        if (turnCounter > 8)
        {
          turnChooser = Math.random();
          turnCounter = 0;
        }
    }
}
