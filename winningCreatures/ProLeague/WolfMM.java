import java.awt.*;
import java.util.*;

/**
   Aggressive pack animal attracted to the smell of blood. Wolves
   share information about targets that they have seen and congregate
   on victims.
   <p>

   <p>
   Sound effects from http://www.ualberta.ca/~jzgurski/wcomm.html

   <p>Morgan McGuire
   <br>morgan@cs.williams.edu


<h3>State Machine</h3>
<pre>               
                   
.-----------EXPLORE
|    Timeout   ^\               
| and loss of /  \              
|   target   /    \ Any Wolf
|   after a /      \observes
|    kill   \      /a Target
|            \    v       
|           PURSUE
|   Any Wolf ^   \                    
|   observes/     \Reach location     
|   a target\     /or Timeout         
|            \   v                    
'------------>HUNT
No unexplored     
 squares left
</pre>
*/
public class WolfMM extends Creature {

    /** If true, hides the toString method */
    final static private boolean RELEASE_MODE = true;

    final protected Random random = new Random();

    private enum State { 
        /** Seeking to cover the map */
        EXPLORE,

        /** Chasing a recently observed target */
        PURSUE,

        /** Seeking targets */
        HUNT, 
    };

    //////////////////////////////////////////////////////////////////////////
    /** Allocated by the first creature to spawn. */
    static private Map         map;
    static private int         numWolves         = 0;
    static private int         initialNumWolves  = 0;
    static private boolean     firstRound        = true;
    static private long        lastKillTime      = 0;

    // If this classId is ever set, it is treated as nonzero
    static private int         friendClassId = -1;


    /** A kill is recent if it has occured within this much game time
     * of now */
    static private final long RECENT_KILL = Simulator.MOVE_FORWARD_COST * 20;

    /** Number of turns that a creature will hunt before it changes target,
        unless it encounters an enemy in the mean time. */
    static private final int MAX_HUNT_TURNS = 20;

    /** Number of turns that a creature will pursue a target before it
        checks to see if there is a closer target.  If this is too
        large, then the creature will run right past near targets.  If
        it is too small, then the creature may become indecisive and
        keep switching targets if pathfinding requires it to walk a
        larger distance.`*/
    static private final int MAX_PURSUIT_TURNS = 14;

    /////////////////////////////////////////////////////////////////////////

    private State              state             = State.EXPLORE;

    /** Where this creature is currently trying to go */
    private Point              target            = null;

    private int                turnsInState      = 0;
    
    /** Last thought, for debugging */
    private String             thinking          = "";

    private void think(String s) {
        thinking = s;
        // System.err.println(s);
    }

    private int evaluateDanger(Observation neighbor) {
        final int ENEMY   = 100;
        final int UNKNOWN = 50; 
        final int FLYTRAP = 40; 
        final int EMPTY   = 20; 
        final int APPLE   = 25; 
        final int FRIEND  = 1;  // A friend might have been converted!

        if (neighbor == null) {
            return UNKNOWN;
        } else {
            switch (neighbor.classId) {
            case APPLE_CLASS_ID:          return APPLE;
            case UNINITIALIZED_CLASS_ID:  return UNKNOWN;
            case FLYTRAP_CLASS_ID:        return FLYTRAP;
            case EMPTY_CLASS_ID:          return EMPTY;
            case HAZARD_CLASS_ID:          return 0;
            case WALL_CLASS_ID:           return 0;
            
            default:
                if (neighbor.classId == myClassId()) {
                    return FRIEND;
                } else {
                    return ENEMY;
                }
            }
        }
    }

    public void run() {
        if (map == null) {
            // This is the start of the game and I'm the first wolf to spawn
            map = new Map(getMapDimensions(), myClassId());
        }

        if (firstRound) {
            // Assume the map is symmetric and that there are targets opposite me,
            // unless another wolf has already observed that location
            final Point opposite = new Point(map.width() - getPosition().x - 1,
                                             map.height() - getPosition().y - 1);
            if (map.get(opposite) == null) {
                // This prevents exploring:
                // recordUnknownEnemy(opposite);
            }

            ++initialNumWolves;
        } else {
            checkForPheromones();
        }
        
        ++numWolves;

        // Record myself
        record(observeSelf());

        // I MAY be spawning on a spot previously occupied by an
        // enemy...in which case there is likely another UNKNOWN enemy
        // right next to me, e.g., if we're fighting in a tunnel.
        // Find out where that spot likely is and attack it. There
        // may be multiple potential threats.  Rank each by danger and 
        // choose the most dangerous
        if (! firstRound) {
            Direction likelyDirection = getDirection();
            int danger = 0;
            // Find the most-dangerous direction
            for (Direction d : Direction.values()) {
                final int neighborDanger = evaluateDanger(map.get(d.forward(getPosition())));
                if (neighborDanger > danger) {
                    danger = neighborDanger;
                    likelyDirection = d;
                }
            }

            if (danger > 0) {
                String t = "Spawned in danger.\n  I believe that there is an enemy to the " +
                    likelyDirection + " (danger = " + danger + "). ";
                
                for (Direction d : Direction.values()) {
                    Observation n = map.get(d.forward(getPosition(), 1));
                    t += ("to the " + d + ": " + n + "\n");
                }
                think(t);
                turn(likelyDirection);

                // Only attack if the enemy is still there
                Observation obs = map.get(likelyDirection.forward(getPosition(), 1));
                boolean stillAttack = true;

                if ((obs == null) || isEnemy(obs)) {
                    attack();
                }
            }
        }
        
        // Record anything that I can see
        look();
        
        // By the time that we get here, it must be the 2nd round
        firstRound = false;
        
        while (true) {
            ++turnsInState;

            if (! overridingSituation()) {
                switch (state) {
                case EXPLORE:
                    exploreStep();
                    break;
                    
                case PURSUE:
                    pursueStep();
                    break;
                    
                case HUNT:
                    huntStep();
                    break;
                }
            }
        }
    }

    /** If there is some situation that demands a specific action
     independent of the state, resolve it and return true, otherwise
     return false.*/
    private boolean overridingSituation() {
        final Point p = getPosition();

        Observation N = map.get(p.x, p.y - 1);
        Observation S = map.get(p.x, p.y + 1);
        Observation E = map.get(p.x + 1, p.y);
        Observation W = map.get(p.x - 1, p.y);

        // Am I unable to move?
        if (nonHostileBlock(N) && nonHostileBlock(S) &&
            nonHostileBlock(E) && nonHostileBlock(W)) {
            think("Overriding situation: I can't move");
            delay();
            return true;
        }

        // Is there a known enemy right next to me?
        if (isEnemy(map.get(getDirection().forward(p)))) {
            think("Overriding situation: Enemy in front");
            // Prefer to attack forward
            attack();
            return true;
        } else if (isEnemy(N)) {  // TODO: we should favor attacking to the sides before behind
            think("Overriding situation: Enemy to the North");
            turn(Direction.NORTH);
            attack();
            return true;
        } else if (isEnemy(S)) {
            think("Overriding situation: Enemy to the South");
            turn(Direction.SOUTH);
            attack();
            return true;
        } else if (isEnemy(E)) {
            think("Overriding situation: Enemy to the East");
            turn(Direction.EAST);
            attack();
            return true;
        } else if (isEnemy(W)) {
            think("Overriding situation: Enemy to the West");
            turn(Direction.WEST);
            attack();
            return true;
        }

        return false;
    }


    /** True if there is some non-hostile object blocking this square */
    private boolean nonHostileBlock(Observation obs) {
        return (obs != null) && 
            ((obs.classId == myClassId()) || 
             (obs.classId == WALL_CLASS_ID) ||
             (obs.classId == HAZARD_CLASS_ID));
    }


    /** Version of isEnemy that returns false for null */
    @Override
    protected boolean isEnemy(Observation obs) {
        return (obs != null) && (obs.classId != friendClassId) && super.isEnemy(obs);
    }


    private void exploreStep() {
        ScanResult s = scanMap();

        if (s.enemy != null) {
            think("Enemy observed at " + s.enemy);
            // An enemy was found, switch to pursuit
            state = State.PURSUE;
            target = s.enemy;
            turnsInState = 0;
            return;
        }
        
        if ((target == null) || getPosition().equals(target) || (map.get(target) != null)) {
            // Our target is no longer valid, so switch to the new one
            if (s.unexplored == null) {
                // There's nothing unexplored
                think("Everything has been explored.");
                state = State.HUNT;
                target = null;
                turnsInState = 0;
                return;
            } else {
                target = s.unexplored;
                turnsInState = 0;
            }
        }
        think("Moving from " + getPosition() + " towards " + target);
        moveTowardsTarget();
    }


    /** Called from all of the step() methods to advance towards the
        target. If the square it needs to move into is unknown, will
        look instead of moving.*/
    private void moveTowardsTarget() {

        final Point firstStep = map.firstPointOnPath(getPosition(), target);

        if (firstStep == null) {
            // That square is unreachable, mark it as such
            recordUnreachable(target);
            target = null;
        } else {
            if (turn(directionToPoint(firstStep))) {
                // Only look if we actually had to turn
                look();
            } else {
                final Observation obs = map.get(firstStep);
                if (obs == null) {
                    // We've just encountered an unknown square.  Look first.
                    think("moveTowardsTarget encountered an unknown square--looking");
                    look();
                } else if (! moveForward()) {
                    // We tried to move, but hit something (which was
                    // unexpected, but could be because another
                    // creature moved or because we just tried to move
                    // in this direction for the first time.) Look
                    // before moving again.
                    think("Hit an unexpected obstacle in moveTowardsTarget.");
                    look();
                }
            }
        }
    }

    
    private class ScanResult {
        /** null if there are no unexplored squares */
        public Point unexplored;

        /** null if there are no enemies */
        public Point enemy;
    }

    /** Scans the map for various kinds of targets. */
    private ScanResult scanMap() {
        final ScanResult s = new ScanResult();

        int unexploredDistance = 10000000;
        int enemyDistance      = 10000000;

        final Point p = new Point();
        for (p.x = 1; p.x < map.width() - 1; ++p.x) {
            for (p.y = 1; p.y < map.height() - 1; ++p.y) {
                final Observation obs = map.get(p);
                final int d = distance(p, getPosition());

                if (obs == null) {
                    if (d < unexploredDistance) {
                        unexploredDistance = d;
                        if (s.unexplored == null) { s.unexplored = new Point(); }
                        s.unexplored.x = p.x; s.unexplored.y = p.y;
                    }
                } else if (isEnemy(obs)) {
                    if (d < enemyDistance) {
                        enemyDistance = d;
                        if (s.enemy == null) { s.enemy = new Point(); }
                        s.enemy.x = p.x; s.enemy.y = p.y;
                    }
                }
            }
        }
        
        return s;
    }


    /** Chooses an "empty" location that has not been recently
        investigated, with probability proportional to observation
        age.  Called in the HUNT step when looking for enemies.*/
    private Point findOldLocation() {
        final ScanResult s = new ScanResult();

        long  time_r  = getGameTime();
        Point pos_r   = new Point(1, 1);
        final Point p = new Point();

        for (p.x = 1; p.x < map.width() - 1; ++p.x) {
            for (p.y = 1; p.y < map.height() - 1; ++p.y) {
                final Observation obs = map.get(p);

                if ((obs != null) && (obs.type == Type.EMPTY) && (obs.time < time_r)) {
                    // New closest position
                    pos_r.x = p.x; pos_r.y = p.y; time_r = obs.time;
                }
            }
        }
        
        return pos_r;
    }
    

    private void huntStep() {
        think("Hunting");
        final ScanResult s = scanMap();

        if (s.enemy != null) {
            think("Found a target, switching from HUNT to PURSUE");
            state = State.PURSUE;
            turnsInState = 0;
            target = s.enemy;

        } else if (s.unexplored != null) {
            think("Couldn't find a target, switching from HUNT to EXPLORE");
            target = s.unexplored;
            state = State.EXPLORE;
            turnsInState = 0;

        } else {
            if ((target == null) || (turnsInState > MAX_HUNT_TURNS)) {
                // Reset to a new target
                turnsInState = 0;
                target = findOldLocation();
                think("Selected target " + target + " to re-explore in HUNT mode");
            }

            look();
            moveTowardsTarget();
        }
    }

    
    private void pursueStep() {
        if ((target == null) || getPosition().equals(target) || 
            (turnsInState > MAX_PURSUIT_TURNS) || ! isEnemy(map.get(target))) {
            // See if there is a new target
            final ScanResult s = scanMap();
            if (s.enemy == null) {
                // There is no known enemy
                if ((getGameTime() - lastKillTime < RECENT_KILL) && (s.unexplored != null)) {
                    // There was a recent kill, and there are still squares to explore
                    target = s.unexplored;
                    state = State.EXPLORE;
                    turnsInState = 0;
                    return;
                } else {
                    // Hunt for additional targets
                    state = State.HUNT;
                    target = null;
                    turnsInState = 0;
                    return;
                }
            } else {
                target = s.enemy;
                turnsInState = 0;
            }
        }

        // Is A target (even if not our target) right in front of us?
        Point p = getDirection().forward(getPosition(), 1);
        final Observation obs = map.get(p);
        if ((obs != null) && isEnemy(obs)) {
            attack();
        } else {
            moveTowardsTarget();
        }
    }


    @Override
    public boolean attack() {
        {
            final Observation obs = map.get(getDirection().forward(getPosition(), 1));
            assert (obs == null) || ((obs.type != Type.WALL) && (obs.type != Type.HAZARD)) : 
            "A wolf is about to attack a known wall or thorn.";
        }

        if (super.attack()) {
            // On success, the new wolf will have already marked its position for us
            target = null;
            lastKillTime = getGameTime();
            return true;
        } else {
            // Record that there is nothing in front of me, unless a
            // newer observation has been made since my attack was
            // launched.
            final Point p = getDirection().forward(getPosition(), 1);
            final long time = getGameTime() - Simulator.ATTACK_COST;
            Observation obs = map.get(p);
            if ((obs != null) && (obs.time < time) && (obs.type != Type.WALL) && (obs.type != Type.HAZARD)) {
                // As far as I know, this square is empty
                record(new Observation(p, time));
            }

            return false;
        }
    }

    static private int sgn(int x) {
        if (x > 0) {
            return 1;
        } else if (x == 0) {
            return 0;
        } else {
            return -1;
        }
    }


    static private int sgn(long x) {
        if (x > 0) {
            return 1;
        } else if (x == 0) {
            return 0;
        } else {
            return -1;
        }
    }

    private void checkForPheromones() {
        final String s = getPheromone();
        if (s.length() > 0) {
            Authentication a = new Authentication(s, getPosition());
            if (a.isValid()) {
                friendClassId = a.friendClassID();
                think("made a friend: " + friendClassId);
            } else {
                think("Bogus friend ID");
            }
        }
    }

    /** Updates the map.*/
    @Override
    protected boolean moveForward() {
        final Point oldPos = getPosition();
        if (super.moveForward()) {
            checkForPheromones();
            

            // Update the map
            record(new Observation(oldPos, getGameTime()));
            record(observeSelf());
            return true;
        } else {
            return false;
        }
    }


    /** Updates the map.*/
    @Override
    protected boolean moveBackward() {
        final Point oldPos = getPosition();
        if (super.moveBackward()) {
            // Update the map
            record(new Observation(oldPos, getGameTime()));
            record(observeSelf());
            return true;
        } else {
            return false;
        }
    }


    /** Abstract map.record so that during debugging we can see which creature called it */
    private void record(final Observation obs) {
        //System.out.println("" + getId() + ": record(" + obs.position + ") = " + obs);
        map.record(obs);
    }


    /** Updates the map.*/
    @Override
    protected Observation look() {
        final Observation obs = super.look();

        // Record what we saw
        final Point me = getPosition();
        final Point it = obs.position;

        final int dist = distance(it) - 1;

        // Only one of these is non-zero
        final int dx = sgn(it.x - me.x);
        final int dy = sgn(it.y - me.y);
        
        int x = me.x + dx;
        int y = me.y + dy;
        
        // Lots of empty squares...
        for (int i = 0; i < dist; ++i) {
            record(new Observation(x, y, obs.time));
            x += dx; y += dy;
        }
        
        // ...and one non-empty
        record(obs);

        return obs;
    }


    public void onDeath() {
        // I was killed; tell everyone where I was when that happened
        --numWolves;
        recordUnknownEnemy(getPosition());
    }


    private void recordUnknownEnemy(Point p) {
        record(new Observation(p, "Unknown", UNKNOWN_CREATURE_CLASS_ID, 
                                   0, Direction.NORTH, getGameTime()));
    }


    private void recordUnreachable(Point p) {
        record(new Observation(p, Type.WALL, WALL_CLASS_ID, getGameTime()));
    }


    /** Turn in a random direction 90 degrees. */
    protected void turnRandom() {
        if (random.nextInt(2) == 0) {
            turnLeft();
        } else {
            turnRight();
        }
    }


    /** Returns the primary direction of travel to reach this point. */
    protected Direction directionToPoint(Point loc) {
        Point pos = getPosition();

        int dx = loc.x - pos.x;
        int dy = loc.y - pos.y;

        if (Math.abs(dx) >= Math.abs(dy)) {
            // Mostly off on the horizontal
            if (dx < 0) {
                return Direction.WEST;
            } else {
                return Direction.EAST;
            }
        } else if (dy < 0) {
            return Direction.NORTH;
        } else {
            return Direction.SOUTH;
        }
    }


    /** Rotate to face this point. Return true if turned in order to do so. */
    protected boolean turn(Point loc) {
        return turn(directionToPoint(loc));
    }


    /** Rotate to face this direction, returning true if actually rotated.*/
    protected boolean turn(Direction target) {
        final Direction dir = getDirection();

        if (dir == target) {
            return false;
        }

        int me = dir.toInt();
        int it = target.toInt();

        if ((me + 2) % 4 == it) { 
            // Turn 180
            turnRandom();
            turn(target);
        } else if ((me + 1) % 4 == it) {
            turnLeft();
        } else {
            turnRight();
        }

        return true;
    }


    public String getAuthorName() {
        return "Morgan McGuire";
    }


    public String getDescription() {
        return "Aggressive pack animal attracted to the smell of blood.";
    }


    public String toString() {
        if (RELEASE_MODE) {
            return "I am Rose Tyler";
        } else {
            String s = "";
            
            s += "state            = " + state + "\n";
            s += "target           = " + target + "\n";
            s += "thinking         = " + thinking + "\n";
            
            s += "_______________________________________________\n\n";
            s += "numWolves        = " + numWolves + "\n";
            s += "initialNumWolves = " + initialNumWolves + "\n";
            s += "firstRound       = " + firstRound + "\n";
            s += "friendClassId    = " + friendClassId + "\n";
            
            s += "map =\n" + map;
        
            return s;
        }
    }


    /////////////////////////////////////////////////////////////////////////////////
    //                                                                             //
    //                            Pathfinding                                      //

    static final private class Map {
        /** Write to this using the Record method */
        private Observation data[][];
        private int width;
        private int height;
        private int myClassId;

        /** Pathfinding node */
        static final private class Node implements Comparable<Node> {
            public Point position;
            
            /** Time to reach this point from the start along the shortest
                known path */
            public long  timeFromStart;
            
            /** Estimated time to reach the goal from here. */
            public long  timeFromGoal;
            
            /** First step on the shortest path to this node from the start.
                The creature only ever needs to know the first step, so we
                don't bother tracking the full path. */
            public Point firstStep;

            public boolean inQueue = true;

            public Node(Point pos, long ts, long tg, Point first) {
                position = pos;
                timeFromStart = ts;
                timeFromGoal = tg;
                firstStep = first;
            }
            
            public int compareTo(Node other) {
                return sgn((timeFromStart + timeFromGoal) - 
                           (other.timeFromStart + other.timeFromGoal));
            }
        }


        public Map(Dimension d, int myClassId) {
            width = d.width;
            height = d.height;
            data = new Observation[width][height];
            this.myClassId = myClassId;

            // We know that the outside of the map is all walls
            for (int x = 0; x < width; ++x) {
                data[x][0] = new Observation(new Point(x, 0), Type.WALL, WALL_CLASS_ID, 0);
                data[x][height - 1] = new Observation(new Point(x, height - 1), 
                                                      Type.WALL, WALL_CLASS_ID, 0);
            }

            for (int y = 0; y < height(); ++y) {
                data[0][y] = new Observation(new Point(0, y), Type.WALL, WALL_CLASS_ID, 0);
                data[width - 1][y] = new Observation(new Point(width - 1, y), 
                                                     Type.WALL, WALL_CLASS_ID, 0);
            }
        }


        /** Is this square on the map */
        public boolean inBounds(Point p) {
            return (p.x >= 0) && (p.y >= 0) && (p.x < width) && (p.y < height);
        }
        

        /** Returns the first step from getPosition() towards the goal
            along the (probably) shortest path. If goal is unreachable,
            returns null.*/
        public Point firstPointOnPath(Point startPos, Point goalPos) {

            // Is the goal right next to us?
            if (distance(startPos, goalPos) == 1) {
                return goalPos;
            }

            // The goal must be at least one square away, so search for a good path
            
            // Paths sorted by expected shortest distance
            final PriorityQueue<Node>  queue   = new PriorityQueue<Node>();
            final HashMap<Point, Node> nodeMap = new HashMap<Point, Node>();

            final Node start = 
                new Node(startPos, 0, distance(startPos, goalPos) * 
                         Simulator.MOVE_FORWARD_COST, null);

            nodeMap.put(startPos, start);
            queue.add(start);

            // For debugging
            int loopIterations = 0;
            while (! queue.isEmpty()) {
                ++loopIterations;                
                // Extract the current best known path
                final Node current = queue.poll();
                current.inQueue = false;

                /* // TODO: check if we're out of time
                if (loopIterations > (width - 2) * (height - 2) / 10) {
                    // TODO: Why do we get in here?
                    // System.err.println("Wolf is taking a long time to find a path");
                    // Give up
                    return current.firstStep;
                    }*/

                // Consider all of its neighbors that are in the queue
                for (Direction dir : Direction.values()) {
                    final Point p = dir.forward(current.position, 1);
                    
                    // Is p the goal?
                    if (p.equals(goalPos)) {
                        // What was the first step on the path to p?
                        return current.firstStep;
                    }

                    Point first = current.firstStep;
                    if (first == null) {
                        // this is the first step away from the start
                        first = p;
                    }

                    // Is location p accessible in the map?  Consider
                    // unknowns to be accessible until proved
                    // otherwise
                    final Observation obs = get(p);
                    if ((obs == null) || ((obs.type != Type.HAZARD) && (obs.type != Type.WALL))) {
                        
                        // TODO: Step cost will depend on what is in
                        // the square.  Consider squares occupied by a
                        // creature to cost the attack cost, because
                        // there is a chance that we'll get blocked or
                        // have to fight.
                        final long stepCost = 
                            ((obs != null) && (obs.type == Type.CREATURE)) ? 
                            Simulator.ATTACK_COST : Simulator.MOVE_FORWARD_COST;


                        final long newTimeFromStart = 
                            current.timeFromStart + stepCost;

                        // Get the node for p (or create it, if needed)
                        Node n = nodeMap.get(p);

                        if (n != null) {

                            // Is n still in the queue?
                            // Have we found a better way of reaching n?
                            if (n.inQueue && (n.timeFromStart > newTimeFromStart)) {
                                // Update the distance to n to reflect
                                // that we just found a better way to it
                                queue.remove(n);
                                n.timeFromStart = newTimeFromStart;
                                n.firstStep = first;
                                queue.add(n);
                            }

                        } else {
                            n = new Node(p, newTimeFromStart, 
                                         distance(p, goalPos) * Simulator.MOVE_FORWARD_COST,
                                         first);
                            nodeMap.put(p, n);
                            queue.add(n);
                        }

                    }
                } // for each neighbor
            }

            // We were unable to find any path to the goal
            return null;
        }


        public void record(Observation obs) {
            if ((obs.type == Type.CREATURE) && (obs.classId != UNKNOWN_CREATURE_CLASS_ID)) {// && (obs.classId != myClassId)) {
                // Remove previous references to this creature (we
                // don't do this for unknown creature classes, which
                // don't have valid ids anyway)
                for (int x = 1; x < width - 1; ++x) {
                    for (int y = 1; y < height - 1; ++y) {
                        final Observation old = data[x][y];
                        if ((old != null) && (old.classId != UNKNOWN_CREATURE_CLASS_ID) && (old.id == obs.id)) {
                            // Record an empty square at the old position
                            data[x][y] = new Observation(x, y, obs.time);
                        }
                    }
                }
            }

            /*
              Propagating old information led to huge propagation of potential enemies.  Instead,
              mark old information on a heat map and explore around there.

            final Observation old = data[obs.position.x][obs.position.y];
            if ((old != null) && (old.type == Type.CREATURE) && (obs.id != old.id) && (old.classId != myClassId)) {
                // There was an enemy here who is no longer (and we're not observing the same
                // enemy again). Propagate the information outwards.
                for (int d = 0; d < 4; ++d) {
                    final Direction dir = Direction.fromInt(d);
                    final Point p = dir.forward(obs.position, 1);
                    final Observation neighbor = data[p.x][p.y];
                    if ((neighbor == null) || ((neighbor.type == Type.EMPTY) && (neighbor.time < (obs.time + old.time) / 2))) {
                        // Record that the creature that was
                        // previously in the center moved to this
                        // neighbor, as a speculation.  Keep the old
                        // time.
                        data[p.x][p.y] = new Observation(p, old.className, old.classId, old.classId, old.direction, old.time);
                    }
                }
                }*/

            data[obs.position.x][obs.position.y] = obs;
        }


        public Observation get(Point p) {
            return get(p.x, p.y);
        }

        public Observation get(int x, int y) {
            return data[x][y];
        }


        public int width() {
            return width;
        }


        public int height() {
            return height;
        }


        public String toString() {
            String s = "";
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    final Observation obs = data[x][y];
                    if (obs == null) {
                        s += "?";
                    } else if (obs.classId == APPLE_CLASS_ID) {
                        s += "A";
                    } else if (obs.classId == FLYTRAP_CLASS_ID) {
                        s += "F";
                    } else if (obs.classId == UNKNOWN_CREATURE_CLASS_ID) {
                        s += "u";
                    } else if ((obs.type == Type.CREATURE) && (obs.classId != myClassId)) {
                        s += "e";
                    } else if (obs.classId == myClassId) {
                        s += "w"; // Wolf!
                    } else if (obs.type == Type.EMPTY) {
                        s += " ";
                    } else if (obs.type == Type.WALL) {
                        s += "#";
                    } else if (obs.type == Type.HAZARD) {
                        s += "+";
                    } else {
                        s += ".";
                    }
                }
                s += "\n";
            }

            return s;
        } // toString
    } // map


    /** Verifies if a pheremone is a valid friend's classID.  This must be kept in sync with DragonMM. */
    private static class Authentication {
        private static final int DAY = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        public static final int INVALID = -1;
        int friendClassID = INVALID;

        public Authentication(String s, Point p) {
            if ((s.length() >= 4) && (s.charAt(0) == ' ')) {
                final int check = Integer.parseInt(s.substring(s.length() - 2), 16);
                final int combine = Integer.parseInt(s.substring(2, s.length() - 2));
                if (check == checksum(combine)) {
                    // This is a valid code
                    friendClassID = combine - offset(p);
                }
            }
        }
    
        /** Amount to offset classId by to obscure it */
        static private final int offset(Point p) {
            return 111 * p.x + 2 * p.y + 1 + DAY;
        }

        static public String encodeID(Point p, int myClassId) {
            int combine = offset(p) + myClassId;
            return " " + (int)(Math.random() * 9) + combine + String.format("%02x", checksum(combine));
        }

        static private int checksum(int num) {
            return ((num >> 2) * 13) & 0xFF;
        }

        public boolean isValid() {
            return friendClassID != INVALID;
        }

        public int friendClassID() {
            return friendClassID;
        }
    }
}
