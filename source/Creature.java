/*
Creature.java

Copyright (c) 2009-2012, Morgan McGuire
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
import java.awt.Point;
import java.awt.Dimension;

/** 
 Extend this class to create your own Creature.
 
 <p>Override the {@link #run} method to implement the Creature's
 AI. Within that method the following actions are available (each of
 which takes some time steps to execute):
 <ul>
   <li> {@link #moveForward}
   <li> {@link #moveBackward}
   <li> {@link #turnLeft}
   <li> {@link #turnRight}
   <li> {@link #attack}
   <li> {@link #observe}
   <li> {@link #delay}
   <li> {@link #emitPheromone}
 </ul>
</p>

 <p>Example:
<pre>
<font color=888888>
public class Rover extends Creature {
    public void run() {
           while (true) {
</font>
                if (! moveForward()) {
                    attack();
                    turnLeft();
                }
<font color=888888>
            }
    }
 }
</font></pre>
</p>

 
 <p>Morgan McGuire
 <br>morgan@cs.williams.edu</p>
 */
public abstract class Creature implements Entity, Runnable {

    /** @deprecated Old name for HAZARD */
    static final public Type THORN = Type.HAZARD;

    /** An illegal, uninitialized value distinct from an empty square.
        Guaranteed to be zero.
        @see Observation#classId
        @see Creature#getClassId */
    static final public int UNINITIALIZED_CLASS_ID = 0;

    /** The class ID for an empty space on the map.  This will never
        be created by an Observation, but can be used by Creatures to
        record empty spaces that they have inferred.

        @see Observation#classId
        @see Creature#getClassId */
    static final public int EMPTY_CLASS_ID         = 1;

    /** @see Observation#classId
        @see Creature#getClassId */
    static final public int WALL_CLASS_ID          = 2;

    /** @see Observation#classId
        @see Creature#getClassId */
    static final public int HAZARD_CLASS_ID        = 3;

    /** @deprecated Old name for HAZARD_CLASS_ID */
    static final public int THORN_CLASS_ID         = HAZARD_CLASS_ID;

    /** The class ID for a Treasure.
        @see Observation#classId
        @see Creature#getClassId */
    static final public int TREASURE_CLASS_ID      = 10;

    /** The class ID for an Apple.
        @see Observation#classId
        @see Creature#getClassId */
    static final public int APPLE_CLASS_ID         = 11;

    /** The class ID for a Flytrap.
        @see Observation#classId
        @see Creature#getClassId */
    static final public int FLYTRAP_CLASS_ID       = 12;

    /** A class ID reserved for use by Creature subclasses.  This is
        guaranteed to not match the class ID of any creature actually
        in the match.

        @see Observation#classId
        @see Creature#getClassId */
    static final public int UNKNOWN_CREATURE_CLASS_ID  = 18;

    /** Private to prevent the Creature from mutating it. */
    private int classId;

    /** Private to prevent the Creature from mutating it. */
    private int id;

    /** Private to prevent the Creature from invoking simulator methods. */
    private Simulator simulator;

    /** Cached to avoid locking the simulator during the Creature's logic. */
    private Point position;

    /** Cached to avoid locking the simulator during the Creature's logic. */
    private Direction direction;

    /** Each creature species is assigned a unique class ID for each trial,
        except Treasure, Apple, and Flytrap, which always have the same values.
        You can recognize your own creatures by 

        <pre>
        Observation obs;
        ...
        if (obs.classId == getClassId()) { ... }
        </pre>

        @see Creature#UNINITIALIZED_CLASS_ID
        @see Creature#EMPTY_CLASS_ID
        @see Creature#TREASURE_CLASS_ID
        @see Creature#APPLE_CLASS_ID
        @see Creature#FLYTRAP_CLASS_ID
        @see Creature#UNKNOWN_CREATURE_CLASS_ID
    */
    final public int getClassId() {
        return classId;
    }
    
    /** Synonym for {@link Creature#getClassId} to make Creature subclasses more readable. */
    final protected int myClassId() {
        return getClassId();
    }

    /** Synonym for {@link Creature#getId} to make Creature subclasses more readable. */
    final protected int myId() {
        return getId();
    }

    final public Type getType() {
        return Type.CREATURE;
    }

    /** @deprecated Use getGameTime instead */
    final public long getTime() {
        return simulator.getTime();
    }

    /** Returns the time in virtual nanoseconds since the simulation started.
        This is the sum of the time spent by all creatures.  It matches the
        values stored in Observations. <b>This is only updated between turns.</b>

        @see #getTurnTime()
        @see #getMyTimeSinceSpawn()
        @see Observation#time
        @see System#nanoTime()
    */
    final public long getGameTime() {
        return simulator.getTime();
    }

    /** The time in virtual nanoseconds that this Creature has spent
       in actions and computing since it spawned.  Will always be less
       than getTime, and usually <b>much</b> less if there are other
       creatures on the map.

       @see #getTime()
       @see #getTurnTime()
     */
    final public long getMyTimeSinceSpawn() {
        return simulator.getTotalTimeSinceSpawn(this);
    }

    /** Returns the time in virtual nanoseconds since this creature's current
        turn started.

        @see #getTime()
        @see #getMyTimeSinceSpawn()
    */
    final public long getTurnTime() {
        return simulator.getTurnTime();
    }

    /** Subclass constructors must not invoke any of the parent class
        methods from their constructor.  Instead, perform
        initialization at the beginning of the {@link #run()} method. */
    protected Creature() { }

    /** Name of this species of creature. */
    final public String getClassName() {
        return getClass().getName();
    }

    /** Allows GUI browsers to display your name as author of this creature.*/
    abstract public String getAuthorName();// {
        //        return "Anonymous";
        // }

    /** Allows GUI browsers to display information and credits about
        your creature. Override to return a one-sentence description
        of your creature's behavior.*/
    abstract public String getDescription();

    /** 
        <p> Called by the simulator on a creature that has is about to
        die, to give it an opportunity to communicate with other
        members of its species before it is removed from the game.
        The creature is still in the map and may observe itself or ask
        for its position or direction before it is removed. Taking any
        action will throw an exception and cause the method to
        terminate.  </p>

        <p>
        This is far more reliable than a finalizer, which Java is not
        required to call and has no explicit time at which it runs.
        This is run on a separate thread than the creature's usual
        thread, so if the creature was holding a lock this method may
        block.  The normal time limit is enforced for the onDeath
        method.
        </p>
     */
    public void onDeath() {}

    /** Returns the size of the map.  Fast. */
    public Dimension getMapDimensions() {
        return simulator.getDimensions();
    }

    /** Each creature has a unique number that it can use to
        distinguish itself from others.  The id is not valid until
        run() is called on the creature; do not reference it from the
        constructor.*/
    final public int getId() {
        return id;
    }

    /** Create an observation describing this creature.  This is not
        an action and does not take substantial time. */
    public Observation observeSelf() {
        return new Observation(getPosition(), getClassName(), getClassId(), getId(), getDirection(),
                               getTime(), inFog(), inMud(), shrineClassId());
    }

    /** Returns the pheromone at this creature's current
        location. This is not an action and does not take substantial
        time.  Pheromones are an experimental feature in the current
        version of Darwin. */
    protected String getPheromone() {
        return simulator.getPheromone(this);
    }

    /** Returns true if this creature's current location is in
        fog. This is not an action and does not take substantial
        time. Fog is an experimental feature in the current version of
        Darwin and will not appear on any tournament map in 2012. */
    protected boolean inFog() {
        return simulator.inFog(this);
    }


    /** Returns true if this creature's current location is in mud.
        Invoking this method is not an action and does not take
        substantial time. */
    protected boolean inMud() {
        return simulator.inMud(this);
    }

    /** Returns true if this creature is enchanted.  Invoking this
        method is not an action and does not take substantial time. 
        A creature that spawns enchanted remains enchanted. */
    protected boolean isEnchanted() {
        return simulator.isEnchanted(this);
    }

    /** If this creature is on a shrine, returns the classID of the
        creatures that own the shrine.  Otherwise, returns
        {@link #UNINITIALIZED_CLASS_ID} */
    protected int shrineClassId() {
        return simulator.shrineClassId(this);
    }

    /** The coordinates of the next position this Creature will enter
      if it moves <i>n</i> times, regardless of whether that position is
      currently empty. (fast)*/
    protected Point getMovePosition(int n) {
        assert direction != null;
        assert position != null;
        return getDirection().forward(getPosition(), n);
    }

    /** Same as {@link #getMovePosition} with the argument <code>n = 1</code> */
    protected Point getMovePosition() {
        return getMovePosition(1);
    }

    /** Returns true if this observation describes a Creature that is 
        not of this species, including Flytrap, Apple, and Treasure.

        @see Observation#className
    */
    protected boolean isEnemy(Observation obs) {
        assert obs != null;
        return 
            (obs.type == Type.CREATURE) && 
            (obs.classId != getClassId());
    }


    /** Returns the Manhattan distance from current position to p2. */
    protected int distance(Point p2) {
        Point p = getPosition();
        return distance(p, p2);
    }

    /** Returns the Manhattan distance between p1 and p2 */
    static public int distance(Point p1, Point p2) {
        return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
    }

    /** Uses the pointer hashCode from Object.*/
    final public int hashCode() {
        // Prevent users from overriding hashcode, which is used by
        // the simulator.
        return super.hashCode();
    }

    /** Uses the pointer comparision from Object. */
    final public boolean equals(Object ob) {
        // Prevent users from overriding equals, which is used by
        // the simulator.
        return super.equals(ob);
    }

    /** Used by Simulator for labeling the icons in 2D render mode. */
    final public char getLabel() {
        return getClassName().charAt(0);
    }
    
    /** Override this method to make your creature think and move.
        Executes as soon as the creature is injected into the world.
        Your creature will stop moving when the run method ends (but
        stay alive), so most implementations will be an intentional
        infinite loop. */
    abstract public void run();

    /** Returns the position of this Creature.  If you change this
        value, it will remain at the incorrect value until the
        Creature moves and it is overwritten by the simulator.  This
        is fast. */
    final public Point getPosition() {
        return position;
    }

    /** Direction this creature is facing. This is fast. */
    final public Direction getDirection() {
        assert direction != null;
        return direction;
    }

    /** Prints a point to a string concisely. */
    static public String toString(Point point) {
        return "(" + point.x + ", " + point.y + ")";
    }

    /** The simulation calls this on the creature when it is first added
        to the world.  Do not invoke this yourself. */
    final synchronized void setSimulator(Simulator s, int _id, int _classId) {
        if (simulator != null) {
            throw new IllegalArgumentException("Cannot invoke setSimulation twice.");
        }
        simulator  = s;
        id         = _id;
        classId    = _classId;
        position   = simulator.getPosition(this);
        direction  = simulator.getDirection(this);
        assert position != null;
        assert direction != null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                           The Actions                                 //

    /** Execute the delay action. */
    protected void delay() {
        simulator.delay(this);
    }


    /** Execute the delay action n times in a row */
    protected void delay(int n) {
        for (int i = 0; i < n; ++i) {
            delay();
        }
    }


    /** Execute the moveForward action n times in a row.
         @return true if it moves all n spaces successfully.*/
    protected boolean moveForward(int n) {
        for (int i = 0; i < n; ++i) {
            if (! moveForward()) {
                return false;
            }
        }
        return true;
    }


    /** Execute the moveBackward action n times in a row.
        @return true if it moves all n spaces successfully.
    */
    protected boolean moveBackward(int n) {
        for (int i = 0; i < n; ++i) {
            if (! moveBackward()) {
                return false;
            }
        }
        return true;
    }


    /** Call to move your creature forward 1 square.  If the creature
        is blocked, it will not move. Moving costs {@link
        Simulator#MOVE_FORWARD_COST} nanoseconds, even if it fails.

        @return true if the Creature successfully moved forward.
    */
    protected boolean moveForward() {
        final Direction d = getDirection();

        if (simulator.move(this, 1)) {
            // To support teleporters and wrapping boards, don't
            // assume that moving forward updated the position to
            // be forward.
            position = simulator.getPosition(this);
            return true;
        } else {
            return false;
        }
    }


    /** Call to move your creature backward 1 square without changing
        its facing direction.  If the creature is blocked, it will not
        move. Moving costs {@link Simulator#MOVE_BACKWARD_COST}
        time steps, even if it fails.

        @return true if the creature successfully moved backward.*/
    protected boolean moveBackward() {
        final Direction d = getDirection().opposite();
        
        // Our old position will become stale
        if (simulator.move(this, -1)) {
            position = simulator.getPosition(this);
            return true;
        } else {
            return false;
        }
    }


    /** Emit a pheromone onto this creature's current location.  Costs
      {@link Simulator#EMIT_PHEROMONE_COST} nanoseconds.
      Pheromones can be sensed with getPheromone().
    */
    protected void emitPheromone(String s) {
        simulator.emitPheromone(this, s);
    }


    /** @deprecated 
        returns the first element of observe(). 
        Provided for backwards compatibility. */
    protected Observation look() {
        return simulator.observe(this)[0];
    }


    /**
       Observe all locations that are visible to this creature.  This
       includes the creature itself and all squares along its facing
       direction until blocked by a wall, thorn, or another
       creature. Future versions may include a wider field of view, so
       design your creatures to make no assumptions about the
       locations of observed squares.
       
       <p>The returned array is ordered so that the first element is
       always the first non-Type.EMPTY element along the facing
       direction.  Note that a square with a shrine on it has {@link
       Type#EMPTY}, so a shrine will never appear first in the array.
       The other elements are not in any guaranteed order.

       <p>Costs {@link Simulator#OBSERVE_COST} nanoseconds.  The
       result of the observe action is accurate at the
       <i>beginning</i> of the creature's next turn, but of course
       whatever is seen might have moved by the time the creature
       actually makes its response.

       <p>The return value always contains at least one element.
     */
    protected Observation[] observe() {
        return simulator.observe(this);
    }


    /** Rotate counter-clockwise 90 degrees.  Costs {@link
      Simulator#TURN_COST} nanoseconds. */
    protected void turnLeft() {
        simulator.turnLeft(this);
        direction = simulator.getDirection(this);
        assert direction != null;
    }


    /** Rotate clockwise 90 degrees.  Costs {@link
       Simulator#TURN_COST} nanoseconds. */
    protected void turnRight() {
        simulator.turnRight(this);
        direction = simulator.getDirection(this);
        assert direction != null;
    }


    /** Attack the creature right in front of you.  If there is a
        creature of a different species present in that spot, that
        creature will be destroyed and a new creature of the same type
        as this one will be created in its place.  The new creature
        will face in the opposite direction as this one (i.e., they will
        be face to face) with probability 60%.  It will be facing
        perpendicular with probability 20% per direction.  It will
        never be facing away from this one.

        <p>Whether there is a creature present or not, this costs
         {@link Simulator#ATTACK_COST} nanoseconds.

        @return true if the attack succeeded. 
    */
    protected boolean attack() {
        return simulator.attack(this);
    }
}


