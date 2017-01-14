/*
Simulator.java

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
import java.awt.*;
import java.util.*;
import java.io.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
   Darwin 2.1 simulator.  Executes the simulation on a set of
   Creatures.

   <p><i>To play Darwin, you do not need to read the source code for
   this class or understand how it works.</i></p>

   <p>The array passed to the Simulator constructor indicates maps
   spawn point labels (numbers in the map source) to subclasses to be
   loaded for them.  The outer edge of the map is forced to wall
   blocks regardless of whether it is specified that way or not.</p>

   <p>Simulator implements Icon so that it can be rendered.  It can
   also be displayed in text mode in text mode using toString().</p>

   <p>Check Simulator.getResult to see if the simulation has
   ended.</p>

   <p>The "time" referred to in the player's guide is virtual.  Taking
   actions does not consume wall-clock time. Instead, the creature's
   time accumulator is penalized as if the action had occured.  This
   enables the simulator to run faster than real-time.  The different
   speed modes create wall-clock delays between turns (for which no
   creature is penalized) so that humans can watch the action at a
   comfortable pace. </p>

   <p>All public methods are synchronized unless provably threadsafe.</p>

   <p>Morgan McGuire
   <br>morgan@cs.williams.edu
   </p>

   <p><i>Inspired by the RoboRally board game, Steve Freund's "Darwin"
   lab assignment, and Nick Parlante's "Darwin's World"
   assignment.</i></p>
 */
public final class Simulator implements javax.swing.Icon {

    /** Shared between ClassLoader (Sandbox) instances */
    static public final Class[] sharedClasses = 
    {Creature.class, Direction.class, Observation.class, 
     Type.class, Entity.class, ConvertedError.class};

    /** Loads a class in its own Sandbox. 
        
        secure: if true, security is enforced and the class is not
        allowed to access the file system, Simulator, etc.
     */
    static public Class loadClass(String name, boolean secure) 
        throws ClassNotFoundException, java.io.IOException {
        return Sandbox.loadIsolated(name, sharedClasses, secure);
    }


    private static class WorkerThread extends Thread {

        private boolean       working = false;
        private Runnable      job     = null;
        final private Object  lock    = new Integer(0);

        public void run() {
            assert (Thread.currentThread() == this) :
                "Should not enter a WorkerThread's run() method on another thread.";

            while (true) {
                // Wait for work
                try {
                    synchronized (lock) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    // If interrupted, just die
                    return;
                }
                try {
                    job.run();
                } finally {
                    synchronized (lock) {
                        job = null;
                        working = false;
                    }
                }
            }
        }


        /** True if this thread is currently working on a job. */
        final public boolean working() {
            return working && isAlive() && (job != null);
        }

        /** True if this thread is eligible for ThreadPool.recycle */
        final public boolean recycleable() {
            return isAlive() && ! working;
        }

        /** Called by Simulator to assign a new job to this thread. */
        final public void startJob(Runnable r) {
            assert r != null;
            assert (Thread.currentThread() != this) : 
            "Cannot assign a job on the same thread.";

            assert ! working;

            // Wake up the thread
            synchronized (lock) {
                working = true;
                job = r;
                lock.notify();
            }
        }
    }


    class ThreadPool {
        final protected Vector<WorkerThread> freelist = new Vector<WorkerThread>();

        /** Put a thread that is not working but is still alive back in */
        public void recycle(WorkerThread t) {
            assert t != null;

            // Verify that this thread is alive but is currently waiting
            if (t.recycleable()) {
                freelist.add(t);
            } else {
                assert false : "Tried to recycle a working thread";
            }
        }


        /** Returns a WorkerThread that is waiting for work. */
        public WorkerThread allocate() {
            if (freelist.size() == 0) {
                final WorkerThread t = new WorkerThread();
                t.start();

                // Wait for the worker thread to start up and wait for a job
                while (t.getState() != Thread.State.WAITING) {
                    Thread.yield();
                }

                return t;
            } else {
                return freelist.remove(freelist.size() - 1);
            }
        }
    }
    
    /** Java doesn't deal well with the thousands of short-lived
        threads that the simulator can spawn, so the simulator maintains
        its own threadpool. */
    private final ThreadPool threadPool = new ThreadPool();

    /** One second in nanoseconds. */
    final static public long SECONDS      = (long)1e9;

    /** One millisecond in nanoseconds. */
    final static public long MILLISECONDS = (long)1e6;
    
    /** Any creature that takes more wall-clock time than this is
        automatically converted into an Apple (killed).  Because the
        garbage collector can run and slow a creature unreasonable on
        one turn, this must be at least 0.1 seconds. */
    final static public long KILL_TIME    = (long)(0.25 * SECONDS);

    // All costs are in nanoseconds.  It is worth turning around if a
    // creature plans to move backwards more than three consecutive
    // spaces.
    static public final long EMIT_PHEROMONE_COST= 200000;
    static public final long DELAY_COST         = 100000;
    static public final long OBSERVE_COST       = 200000;
    /** @deprecated */
    static public final long LOOK_COST          = OBSERVE_COST;
    static public final long ATTACK_COST        = 800000;
    static public final long TURN_COST          = 600000;
    static public final long MOVE_FORWARD_COST  = 400000;
    static public final long MOVE_BACKWARD_COST = 700000;

    /** The additional cost of a move forward operation that enters fog.
        Note that other actions, including moving backwards and exiting fog, 
        incur no additional penalty. */
    static public final long FOG_MOVE_FORWARD_PENTALTY_COST = 200000;

    /** Additional cost for moving, turning, or attacking while in mud. */
    static public final long MUD_PENALTY_COST = 500000;

    static public final int  NS_ASCENSIONS_TO_WIN = 3;

    /** A maze game ends after this many virtual nanoseconds, even if
        no Creature has won. */
    static public final long MZ_TIME_LIMIT      = 6 * SECONDS;

    /** A non-maze game ends after this many virtual nanoseconds, even if
        no Creature has won. */
    static public final long NS_TIME_LIMIT      = 50 * SECONDS;

    /** Wall-clock nanoseconds equal to "pause".  When the game is
        paused it is in fact just running *very* slowly. */
    static private final long PAUSE_TIME        = Long.MAX_VALUE / 10;

    /** Should floor blocks be drawn?  If false, a grid is drawn instead. */
    static private final boolean DRAW_FLOOR      = true;

    static private final Color PHEROMONE_COLOR  = new Color(255, 235, 30, 100);
    static private final Color PHEROMONE_SHADOW_COLOR  = new Color(0, 0, 0, 100);


    /** A version of Runnable that suppresses output */
    static public abstract class QuietRunnable implements Runnable {
        final public void run() {
            // Catch uncaught converted exceptions so that
            // they don't print at the console.
            try {
                runQuiet();
            } catch (ConvertedError e) {
            } catch (java.lang.StackOverflowError e) {
                // Print a little bit of the overflow
                System.err.println("Stack overflow");
                java.lang.StackTraceElement s[] = e.getStackTrace();
                for (int i = Math.min(5, s.length - 1); i >= 0; --i) {
                    System.err.println(" at " + s[i]);
                }
            }
        }

        /** override this */
        abstract public void runQuiet();
    }


    /** @see Simulator#getSound */
    public enum Condition { 
        VICTORY { public int toInt() { return 0; } };

        public abstract int toInt();
    };


    /** Return value of {@link Simulator#getResult} */
    static public class Result {
        public static enum Code {ASCENSION, DOMINATION, MAJORITY, SURVIVAL, LOSE};

        /** One of the winning species, null if none. */
        public Class  species;

        public String result;

        /** Explanation of why the game ended. */
        public String why;

        /** Image of the winner */
        public Image  icon;

        /** Game time in virtual nanoseconds at which the result occured. */
        public long   timeSteps;

        /** Data about each species */
        public Map<Class, Species> speciesStats;

        public Result(String r, Class s, String w, Image i, long t, Map<Class, Species> stats) {
            result    = r;
            species   = s;
            why       = w;
            icon      = i;
            timeSteps = t;
            speciesStats = stats;
        }

        public String toString() {
            return result + "\n" + why + "\nin " + (timeSteps / 1e6) + " milliseconds";
        }
    }

    /** Information about a creature that needs to be spawned at the
        end of the current turn. */
    private static class DelayedSpawn {
        Class         creatureClass;
        Point         point;
        Direction     direction;
        long          initialCPUTime;
        boolean       isEnchanted;
        
        Proxy         proxy;

        public DelayedSpawn(Class c, Point p, Direction d, boolean e, long i) {
            creatureClass = c;
            point = p;
            direction = d;
            isEnchanted = e;
            initialCPUTime = i;
        }
    };

    static private boolean isSecure = false;


    /** Turns on the MaximumSecurityManager and prevents Observations
     from containing classNames.  There is no way to turn security off
     once it has been enabled.*/
    static public void beginSecureExecution() {
        // Need checkPackageAccess to be able to load images from the jar file
        System.setSecurityManager(new MaximumSecurityManager
                                  (new String[]{"readFileDescriptor", 
                                                "accessClassInPackage.sun.security.util"}));
        isSecure = true;
    }


    /** Aggregate information about the performance of an entire species */
    public static class Species {
        public Class     creatureClass;

        public int       classId;
        
        /** Total time spent in actions and compute for all creatures
            of this type, in nanoseconds. */
        public long      totalTime;

        /** Total (virtual) compute time in nanoseconds spent by all
            creatures of this type. */
        public long      computeTime;

        /** Size of the bytecode for this creature. */
        public long      bytes;

        /** Total number of observe actions */
        public int       observeCount;

        /** Total number of move actions */
        public int       moveCount;

        /** Total number of turn actions */
        public int       turnCount;

        /** Total number of attack actions */
        public int       attackCount;

        /** Total number of emitPheromone actions */
        public int       emitCount;

        public int       ascensions;

        public Result.Code resultCode = Result.Code.LOSE;

        public Species(Class c, int id) {
            creatureClass = c;
            classId = id;
        }

        /** Percentage of time spent by this creature in computation [0, 100] */
        public int computePercent() {
            if (totalTime <= 0) {
                return 0;
            } else {
                return (int)(100 * computeTime / totalTime);
            }
        }

        public int totalActions() {
            return observeCount + moveCount + turnCount + attackCount + emitCount;
        }

        public int observePercent() {
            return 100 * observeCount / Math.max(1, totalActions());
        }

        public int movePercent() {
            return 100 * moveCount / Math.max(1, totalActions());
        }

        public int turnPercent() {
            return 100 * turnCount / Math.max(1, totalActions());
        }

        public int attackPercent() {
            return 100 * attackCount / Math.max(1, totalActions());
        }

        public int emitPercent() {
            return 100 * emitCount / Math.max(1, totalActions());
        }

        public void accumulateFrom(Species s) {
            totalTime    += s.totalTime;
            computeTime  += s.computeTime;
            observeCount += s.observeCount;
            moveCount    += s.moveCount;
            turnCount    += s.turnCount;
            attackCount  += s.attackCount;
            emitCount    += s.emitCount;
            ascensions   += s.ascensions;
        }

        public String toString() {
            return "Stats for " + creatureClass.getName() + " (ID " + classId + ", " + bytes + " bytes)\n" +
                "\n" +
                "Time:\n" +
                "  compute   = " + computePercent() + "%\n" +
                "  actions   = " + (100 - computePercent()) + "%\n" +
                "\n" +
                "Actions:\n" +
                "  observe   = " + observePercent()   + "%\n" +
                "  move      = " + movePercent()   + "%\n" +
                "  turn      = " + turnPercent()   + "%\n" +
                "  attack    = " + attackPercent() + "%\n" +
                "  emit      = " + emitPercent()   + "%\n";
        }
    }


    /** Proxy for one creature in the map, hiding per-Creature
       Simulator information from the Creatures themselves.

       Intentionally not a static class because it needs a pointer to
       the simulator.
    */
    private class Proxy {
        public Species   species;

        public Point     position;
        public Direction direction;
        
        /** Thread time offset at spawn. */
        private long     spawnTime;

        /** Total time that this creature has spent running on its
            thread plus the time cost of the actions that it has taken, 
            in nanoseconds. CPU time is virtual and normalized. 
            Used as a priority for selecting the next creature's turn.*/
        public long      totalTimeSinceGameStart;

        /** Virtual CPU time spent by this creature since spawn. */
        public long      computeTimeSinceSpawn = 0;

        /** Virtual CPU + action time since spawn. */
        public long      totalTimeSinceSpawn() {
            return totalTimeSinceGameStart - spawnTime;
        }

        /** Number of actions this creature has taken. */
        public int       numTurns;
        public Thread    thread;

        public boolean   isEnchanted;

        /** Instance */
        public Creature  creature;

        public Proxy(Point p, Direction d, Thread t, Creature c, Species s, boolean e, long totalTimeSinceGameStart) {
            species   = s;
            position  = p;
            direction = d;
            thread    = t;
            creature  = c;
            isEnchanted = e;
            this.totalTimeSinceGameStart = totalTimeSinceGameStart;
            assert totalTimeSinceGameStart >= 0; 
            spawnTime = totalTimeSinceGameStart;
            computeTimeSinceSpawn = 0;
            numTurns = 0;
        }


        public void addComputeTime(long dt) {
            totalTimeSinceGameStart += dt;
            computeTimeSinceSpawn   += dt;
            species.computeTime     += dt;
        }


        public void addActionTime(long dt) {
            totalTimeSinceGameStart += dt;
            species.totalTime       += dt;
        }


        /** Called by the actions from a Creature's own thread to end its
            turn and yield. 
            
            @param cost The cost in nanoseconds that the Creature will pay
            for its action, in addition to the measured computation time.*/
        public void endTurn(long cost) {
            ++numTurns;

            assert ! Thread.holdsLock(Simulator.this) :
            "Cannot end turn while holding the simulator lock";

            if (Thread.currentThread() != thread) {
                throw new Error(thread + " tried to ends its turn on " +
                                Thread.currentThread() + "'s thread.");
            }

            addActionTime(cost);
        
            // Make this thread wait (the Simulator will notice the
            // thread waiting and end the Creature's turn).  We wait
            // on the Proxy object because unlike the Creature and
            // Thread objects, it is not visible to the Creature code,
            // so there is no way for another instance to wake this
            // one up.
            try {
                // Sync on the proxy, not the simulator
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
                // If interrupted, it is probably because something external
                // (like the timer) killed the simulation
                //System.out.println("Thread interrupted!");
            }
        }

    }

    /** All walls and all thorns are the same instance. */
    private static class StaticEntity implements Entity {
        static public StaticEntity wall  = new StaticEntity(Type.WALL,   'X', "Wall.png");
        static public StaticEntity wall2 = new StaticEntity(Type.WALL,   '#', "Wall2.png");
        static public StaticEntity wall3 = new StaticEntity(Type.WALL,   '%', "Wall3.png");
        static public StaticEntity thorn = new StaticEntity(Type.HAZARD, '+', "Thorn.png");
        static public StaticEntity floor = new StaticEntity(Type.EMPTY,  ' ', "Floor.png");
        static public StaticEntity mud   = new StaticEntity(Type.EMPTY,  '.', "Mud.png");
        static public Image        fog   = loadImage("Fog.png");
        
        public Image image;
        public Type  type;
        public char  label;

        private StaticEntity(Type t, char L, String imageFile) {
            type = t;
            label = L;
            image = loadImage(imageFile);
        }

        public Type getType() {
            return type;
        }

        public char getLabel() {
            return label;
        }

        public int getClassId() {
            switch (getType()) {
            case HAZARD:
                return Creature.HAZARD_CLASS_ID;

            case WALL:
                return Creature.WALL_CLASS_ID;
            }
            return Creature.UNINITIALIZED_CLASS_ID;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                         Instance State                                //
    //                                                                       //

    /** Describes the environment of a Darwin square independent of the
        Entity inside it. */
    private static class Environment {
        public static final int   NO_SHRINE        = Creature.UNINITIALIZED_CLASS_ID;
        public static final int   TO_BE_DETERMINED = -1;

        /** Set by the map.  Fog limits visibility an movement.  The
            fog status of a square never changes.  The presence of fog
            implicitly makes new pathfinding and hiding/hunting
            strategies viable. */
        public boolean      inFog     = false;

        /** True if this square contains mud */
        public boolean      inMud    = false;

        /** NO_SHRINE if there is no shrine, otherwise the classId of
            the species that owns the shrine.  Temporarily TO_BE_DETERMINED
            during map loading.
        */
        public int          shrineClassId  = NO_SHRINE;
        
        /** Dropped by Creatures.  A dropped pheroemone overwrites any
            previous pheromone and lasts indefinitely. The pheromone
            in an Observation returned by Creature.observe is never
            null.
            
            Pheromones provide a mechanism for Creatures to easily
            communicate with other species for collusion.
        */
        public String       pheromone = "";
        
        public Environment() {
        }
    
        public Environment(boolean inFog, String pheromone) {
            this.inFog = inFog;
            this.pheromone = (pheromone == null) ? "" : pheromone;
        }
        
        public Environment clone() {
            return new Environment(inFog, pheromone);
        }
        
        public boolean equals(Environment e) {
            return 
                (e.inFog == inFog) &&
                (e.pheromone.equals(pheromone)) &&
                (e.shrineClassId == shrineClassId) &&
                (e.inMud == inMud);
        }
        
        public int hashCode() {
            return pheromone.hashCode() ^ (inFog ? 0xFF : 0x00) ^ (inMud ? 0xFF00 : 0x0000) ^ shrineClassId;
        }
        
        public String toString() {
            return "Environment { inFog = " + inFog + 
                "; pheromone = \"" + pheromone + "\"; inMud = " + inMud +
                "; shrineClassId = " + shrineClassId + "}"; 
        }
    }

    /** Map title */
    private String          title;
    
    /** The simulator's own thread */
    private Thread          simThread;

    /** Dimensions of the map */
    private int             width;
    private int             height;

    /** The world map */
    private Entity[][]      map;

    /** Fog in the world map */
    private Environment[][] environment;

    /** If true, paintIcon renders a 3D view */
    private boolean         view3D = true;

    /** Last transformation used when rendering in 2D (excepting the
        SCALE_2D aspect of the grid).  Set by paintIcon2D, used by the
        Darwin.Inspector to determine which location was clicked.
    */
    private AffineTransform lastXForm2D;

    /** The images are indexed by Direction. */
    static private Map<Class, Image[]>    imageCache = new HashMap<Class, Image[]>();

    /** The images are indexed by Condition. */
    static private Map<Class, Sound[]>    soundCache = new HashMap<Class, Sound[]>();

    /** Creatures scheduled to spawn after the current turn ends. 
        @see #processSpawnQueue */
    private final Vector<DelayedSpawn>    spawnQueue = new Vector<DelayedSpawn>();

    /** Creatures scheduled to die after the current turn ends. There
        should only ever be at most one element in the death queue in the
        current implementation.

        @see #processDeathQueue  */
    private final Vector<Proxy>           deathQueue = new Vector<Proxy>();

    /** Metadata about Creature instances in the map */
    final private Map<Creature, Proxy>    proxyTable = new HashMap<Creature, Proxy>();

    /** Maps Creature classes to species descriptors.  This is passed
        to the Result at the end. */
    private Map<Class, Species>           speciesTable;

    /** The current competitors. */
    private Class[]                       competitorCreatureClasses;

    /** Used to generate unique IDs for creatures as they spawn.
        Random so that creatures can't infer anything about the total
        number of creatures from their own ID values.*/
    private int                           nextID = new Random().nextInt(10000);

    /** Used to disable the assertions around oneTurn when a creature spawns. */        
    private volatile boolean              spawning = false;

    /** Elapsed time for all creature turns. */
    private long                          totalElapsedTime;

    /** Wall-clock time in nanoseconds to wait between turns.
     Volatile because it is written and read in an unsychronized
     method.*/
    private volatile long                 intraTurnTime = PAUSE_TIME;

    /** Number of each type of creature present in the world.  Only
        species with a non-zero number of instances appear in the
        Map. 

        @see incCount, decCount */
    final private Map<Class, Integer>     creatureCount = new HashMap<Class, Integer>();

    /** Drawn highlighted.  For UI only */
    private Creature                      selectedCreature;

    /** Threadgroup containing all creature threads */
    private ThreadGroup                   creatureThreadGroup;

    /** True when a simulation is loaded and running.*/
    private boolean                       live = false;

    /** Ranks all creatures by their elapsed execution time. */
    private PriorityQueue<Proxy>          turnQueue;

    /** Time on the current creature's thread when its current turn started. 
        @see getTurnTime */
    private long                          currentTurnThreadStartTime;

    /** Time limit for the current map, in nanoseconds.  Set in the
        constructor. */
    private long                          TIME_LIMIT;

    static private long                   UNINITIALIZED = -5;

    /** How long (in nanoseconds) it takes the real CPU to perform a computation
        that takes the virtual CPU 1e6 ns. This is used by
        realCPUToVirtualCPUTime().  See also calibrateVirtualCPU. 
    */
    static private long                   realCPUVirtual1000000ns = UNINITIALIZED;

    private enum GameMode {
        NATURAL_SELECTION,
        MAZE
    }

    private GameMode                      gameMode;
    private int                           numCompetitors;

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                            Constructor                                //
    //                                                                       //

    /** Loads the specified map, instantiating each of the creatures
       for the numbered spots in the map. */
    public Simulator(String mapFilename, Class[] creatures) {
        assert creatures != null;
        assert (MZ_TIME_LIMIT > KILL_TIME * 20) &&
            (NS_TIME_LIMIT > KILL_TIME * 20) : 
        "MZ_TIME_LIMIT or GENERAL_TIME_LIMIT is too short--creatures could " + 
            "just stall to force a loss with minority.";

        if (mapFilename.startsWith("mz_")) {
            TIME_LIMIT = MZ_TIME_LIMIT;
        } else {
            TIME_LIMIT = NS_TIME_LIMIT;
        }

        prepareColors();

        // The priority queue sorts on elapsed time
        turnQueue = new PriorityQueue<Proxy>
            (10, // initial capacity
             new Comparator<Proxy>() {
                public int compare(Proxy a, Proxy b) {
                    return (int)(a.totalTimeSinceGameStart - b.totalTimeSinceGameStart);
                }});

        calibrateVirtualCPU();

        // System.out.println(getInfo());

        start(mapFilename, creatures);
    }


    /** Returns a description of the simulator */
    public String getInfo() {
        return getVersion() + "\n\nMorgan McGuire\nWilliams College\n" + 
            "http://cs.williams.edu/~morgan/darwin\n\n" +
            "\n\n" +
            "DELAY_COST = " + Simulator.DELAY_COST + " ns\n" +
            "EMIT_PHEROMONE_COST = " + Simulator.EMIT_PHEROMONE_COST + " ns\n" +
            "ATTACK_COST = " + Simulator.ATTACK_COST + " ns\n" +
            "TURN_COST = " + Simulator.TURN_COST + " ns\n" +
            "OBSERVE_COST = " + Simulator.OBSERVE_COST + " ns\n" +
            "MOVE_FORWARD_COST = " + Simulator.MOVE_FORWARD_COST + " ns\n" +
            "MOVE_BACKWARD_COST = " + Simulator.MOVE_BACKWARD_COST + " ns\n" +
            "MUD_PENALTY_COST = " + Simulator.MUD_PENALTY_COST + " ns\n" +
            "FOG_MOVE_FORWARD_PENALTY_COST = " + Simulator.FOG_MOVE_FORWARD_PENTALTY_COST + " ns\n" +
            "KILL_TIME = " + (Simulator.KILL_TIME/(double)Simulator.SECONDS) + " s\n" +
            "MZ_TIME_LIMIT = " + (Simulator.MZ_TIME_LIMIT / (double)Simulator.SECONDS) + " s\n" +
            "NS_TIME_LIMIT = " + (Simulator.NS_TIME_LIMIT / (double)Simulator.SECONDS) + " s\n" +
            "Virtual CPU is " + String.format("%4.1f", 1e6 / realCPUVirtual1000000ns) + "x slower than the real CPU\n";
    }
    

    static int ignore;
    /** Called from calibrateVirtualCPU */
    static private int slowRoutine() {
        // Test something that takes about 1/10 second to get some reliability
        final int REPEATS = 100;
        int k = 0;
        // The following computation, by definition, takes 1e6 ns * REPEATS on the virtual CPU.
        for (int j = 0; j < REPEATS; ++j) {
            PriorityQueue<Integer> p = new PriorityQueue<Integer>();

            // Increasing N is to the advantage of creatures that use a lot of computation.
            final int N = 350; // Do not change this; it would change the relative cost of Darwin creatures.
            for (int i = 0; i < N; ++i) {
                p.add(i);
                k += p.peek();
            }
            for (int i = N - 1; i > 0; --i) {
                k += p.peek();
                p.remove(i);
            }
        }
        ignore = k;
        return REPEATS;
    }


    static private void calibrateVirtualCPU() {
        // Only run this once; otherwise the hotspot GC will optimize it
        // after the third or fourth (reload Simulator) call
        if (realCPUVirtual1000000ns == UNINITIALIZED) {
            Runtime.getRuntime().gc();
            final long start = getThreadUserTime(Thread.currentThread());
            final int REPEATS = slowRoutine();

            // Force the time bound to be between 0.25x and 4x
            realCPUVirtual1000000ns = 
                Math.max((long)(1e6  * 0.25), 
                         Math.min((long)(1e6 * 4.0),
                                  (getThreadUserTime(Thread.currentThread()) - start) / REPEATS));
        }
    }

    /** Given a time in nanoseconds on the real CPU, estimates how long it
        would take to perform that computation on the (typically slower) 
        virtual CPU.  This allows Darwin to produce consistent priorization
        of creatures as real CPUs get faster.  Without this, creatures that perform
        a lot of computation would get better as the CPU gets faster. */
    long realCPUToVirtualCPUTime(long r) {
        return r * 1000000 / realCPUVirtual1000000ns;
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                            Accessors                                  //
    //                                                                       //

    /** Assumes that p is in bounds */
    public synchronized String getToolTip(Point p) {
        String s = "(" + p.x + ", " + p.y + ")";

        final Entity e = map[p.x][p.y];
        if (e != null) {
            switch (e.getType()) {
            case EMPTY:
                break;

            case WALL:
                s += ", Wall";
                break;

            case HAZARD:
                s += ", Hazard";
                break;

            case CREATURE:
                s += ", " + ((Creature)e).getClassName();
                break;
            }
        }

        final Environment env = environment[p.x][p.y];
        if (env.pheromone.length() > 0) {
            s += ", Pheromone = \"" + env.pheromone + "\"";
        }

        if (env.inFog) {
            s += ", in fog";
        }

        return s;
    }



    /** Total time allowed in virtual nanoseconds for the game on the current map.*/
    public long timeLimit() {
        return TIME_LIMIT;
    }


    /** Total time for this creature; on spawn, this is inherited from the parent. */
    public synchronized long getTime(Creature e) {
        final Proxy p = getProxy(e);
        if (p == null) {
            return 0;
        } else {
            return p.totalTimeSinceGameStart;
        }
    }


    public synchronized long getTotalTimeSinceSpawn(Creature e) {
        final Proxy p = getProxy(e);
        if (p == null) {
            return 0;
        } else {
            return p.totalTimeSinceSpawn();
        }
    }


    public synchronized long getComputeTimeSinceSpawn(Creature e) {
        final Proxy p = getProxy(e);
        if (p == null) {
            return 0;
        } else {
            return p.computeTimeSinceSpawn;
        }
    }


    /** Returns the number of turns this creature has taken.  Spawning
        is the first turn. */
    public synchronized int getTurns(Creature e) {
        final Proxy p = getProxy(e);
        if (p == null) {
            return 0;
        } else {
            return p.numTurns;
        }
    }


    /** Clones the position.  Throws a ConvertedError if the Creature is
        not in the world. */
    public synchronized Point getPosition(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot update its position because it is dead.");
        } else {
            return (Point)(p.position.clone());
        }
    }


    /** Throws a ConvertedError if the Creature is not in the world. */
    public synchronized Direction getDirection(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot update its direction because it is dead.");
        } else {
            return p.direction;
        }
    }

    /** Throws a ConvertedError if the Creature is not in the world. */
    public synchronized boolean isEnchanted(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot be enchanted because it is dead.");
        } else {
            return p.isEnchanted;
        }
    }


    /** Returns true if the Creature's current location is in fog.
        Fog is an experimental feature that will not appear on any
        tournament map in 2011.  Throws a ConvertedError if the
        Creature is not in the world. */
    public synchronized boolean inFog(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot sense fog because it is dead.");
        } else {
            return environment[p.position.x][p.position.y].inFog;
        }
    }


    /** Returns true if the Creature's current location is in mud.
        Throws a ConvertedError if the Creature is not in the
        world. */
    public synchronized boolean inMud(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot sense mud because it is dead.");
        } else {
            return environment[p.position.x][p.position.y].inMud;
        }
    }


    /** If this creature is on a shrine, returns the classID of the
        creatures that own the shrine.  Otherwise, returns
        {@link Creature#UNINITIALIZED_CLASS_ID} 
        Throws a ConvertedError if the Creature is not in the
        world. */
    public synchronized int shrineClassId(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot sense shrines because it is dead.");
        } else {
            return environment[p.position.x][p.position.y].shrineClassId;
        }
    }


    /** Throws a ConvertedError if the Creature is not in the world. */
    public synchronized String getPheromone(Creature e) {
        assert e != null;
        final Proxy p = getProxy(e);
        if (p == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                                     " cannot sense a pheromone because it is dead.");
        } else {
            return environment[p.position.x][p.position.y].pheromone;
        }
    }

    
    /** Returns the creature at x, y on the grid. Called by the
        Darwin.click method for debugger support. */
    public synchronized Creature getCreature(int x, int y) {
        if (! inBounds(x, y)) {
            return null;
        }

        final Entity c = map[x][y];

        if ((c != null) && (c instanceof Creature)) {
            return (Creature)c;
        } else {
            return null;
        }
    }


    /** Reports the version of this simulator. */
    static public String getVersion() {
        return "The Darwin Game 2.2.08";
    }


    /** Name of this map */
    synchronized public String getTitle() {
        return title;
    }


    /** Returns the number of virtual milliseconds since the game
        began. This is the total time consumed by creatures, which
        is typically much longer than the wall-clock time for the
        simulation if run with no delay between turns. */
    synchronized public long getTime() {
        return totalElapsedTime;
    }


    /** Returns the size of the map. */
    synchronized public Dimension getDimensions() {
        return new Dimension(width, height);
    }


    /** Returns the wall-clock time of waitBetweenTurns() in
      nanoseconds. Initially huge, so that the simulator is
      "paused". */
    public long getIntraTurnTime() {
        // Do not synchronize...this must be readable while other
        // synchronized calls are going on
        return intraTurnTime;
    }


    /** Set the wall-clock time to wait between turns in nanoseconds.

        1 millisecond = 1e6 nanoseconds,
        1 second = 1e9 nanoseconds

        Minimum value is 10000. */
    public void setIntraTurnTime(long t) {
        // Do not synchronize...this must be writable while other
        // synchronized calls are going on.  intraTurnTime is volatile
        // instead.
        intraTurnTime = Math.max(t, (long)10000);
    }


    public synchronized void setSelectedCreature(Creature c) {
        selectedCreature = c;
    }


    /** If true, the view renders in 3D */
    synchronized public void setView3D(boolean b) {
        view3D = b;
    }


    synchronized public boolean getView3D() {
        return view3D;
    }


    /** True while the simulation is running (not stopped). */
    synchronized public boolean isRunning() {
        return live;
    }


    // Not synchronized because the color never changes while the
    // simulation is running
    public Color getCreatureColor(Class c) {
        return creatureColor.get(getClassId(c));
    }


    // Not synchronized because the color never changes while the
    // simulation is running
    public Color getCreatureColor(int classId) {
        return creatureColor.get(classId);
    }


    /** Returns the number of different species left alive, excluding Flytraps.*/
    public synchronized int getNumSpeciesLeft() {
        int count = creatureCount.size();

        // Exclude Flytraps
        if (creatureCount.get(Flytrap.class) != null) {
            --count;
        }

        return count;
    }

    /** Returns the number of creatures of this species alive in the map. */
    public synchronized int getCreatureCount(Class c) {
        final Integer i = creatureCount.get(c);
        if (i == null) {
            return 0;
        } else {
            return i;
        }
    }


    /** Returns the amount of user time consumed by the specified
        thread since it was started in nanoseconds */
    final private static long getThreadUserTime(Thread thread) {
        return java.lang.management.ManagementFactory.getThreadMXBean().getThreadUserTime(thread.getId());
    }

    
    /** Returns the time in virtual nanoseconds since the current creature's
        current turn started. Updated continuously.*/
    public long getTurnTime() {
        assert (Thread.currentThread() != simThread);
        return realCPUToVirtualCPUTime(getThreadUserTime(Thread.currentThread()) - currentTurnThreadStartTime);
    }


    /**
       Returns a description of the final outcome, or null if the game has not yet ended.
       
       A creature has won if:
       <ul>
         <li> It is the only species other than Flytraps and Apples remaining, OR
         <li> Time has elapsed and it has more instances than any other Creature
         and there are no Treasures on the map.
       </ul>
     */
    public Result getResult() {
        synchronized (performanceLock) {
            // Number of instances
            final int numFlytraps  = getCreatureCount(Flytrap.class);
            final int numApples    = getCreatureCount(Apple.class);
            final int numTreasures = getCreatureCount(Treasure.class);

            // Number of species
            final int numSpecies   = creatureCount.size() - 
                (((numFlytraps  > 0) ? 1 : 0) +
                 ((numApples    > 0) ? 1 : 0) + 
                 ((numTreasures > 0) ? 1 : 0));
            
            final long t = getTime();
            
            if ((numSpecies == 0) || (turnQueue.size() == 0)) {
                // Everyone lost
                
                for (Species species : speciesTable.values()) {
                    species.resultCode = Result.Code.LOSE;
                }

                return new Result("Total Loss", null, 
                                  "because there are no active Creatures", null, t,
                                  speciesTable);
            }

            switch (gameMode) {
            case MAZE:
                if ((numSpecies == 1) && (numTreasures == 0)) {
                    // Find the winner
                    Class winner = getMostPopulousSpecies(1)[0];
                    assert winner != null;
                    speciesTable.get(winner).resultCode = Result.Code.DOMINATION;
                    return new Result(shortName(winner.getName()) + " completed", winner, "the maze", 
                                      getImage(winner, Direction.EAST), t, speciesTable);
                }
                break;

            case NATURAL_SELECTION:
                // See if any species won by ascension
                for (Species winner : speciesTable.values()) {
                    
                    if (winner.ascensions >= NS_ASCENSIONS_TO_WIN) {
                        
                        winner.resultCode = Result.Code.ASCENSION;
                        for (Species species : speciesTable.values()) {
                            if (species != winner) {
                                species.resultCode = Result.Code.LOSE;
                            }
                        }
                        
                        return new Result(shortName(winner.creatureClass.getName()) + " wins", winner.creatureClass, "by ascension",
                                          getImage(winner.creatureClass, Direction.EAST), t, speciesTable);
                    }
                }
                
            
                if ((numSpecies <= numCompetitors / 2) && (numTreasures == 0)) {
                    // Total domination
                    
                    for (Species species : speciesTable.values()) {
                        species.resultCode = Result.Code.LOSE;
                    }

                    // Find the winner
                    Class[] winners = getMostPopulousSpecies(numCompetitors / 2);
                    for (int i = 0; i < winners.length; ++i) {
                        speciesTable.get(winners[i]).resultCode = Result.Code.DOMINATION;
                    }
                    return new Result(toWinnerString(winners), winners[0], "by total domination", 
                                      getImage(winners[0], Direction.EAST), t, 
                                      speciesTable);
                }
                
                if ((t >= TIME_LIMIT) ||
                    (turnQueue.size() == 0)) {

                    Class[] majority = getMostPopulousSpecies(numCompetitors / 2);
                    for (int i = 0; i < majority.length; ++i) {
                        speciesTable.get(majority[i]).resultCode = Result.Code.MAJORITY;
                    }

                    for (Species species : speciesTable.values()) {
                        if (species.resultCode != Result.Code.MAJORITY) {
                            if (getCreatureCount(species.creatureClass) > 0) {
                                species.resultCode = Result.Code.SURVIVAL;
                            } else {
                                species.resultCode = Result.Code.LOSE;
                            }
                        }
                    }

                    return new Result(toWinnerString(majority), majority[0], 
                                          "by population majority at time limit", 
                                          getImage(majority[0], Direction.EAST), t, speciesTable);
                }
                break;
            }
        } // synchronized (performanceLock)

        return null;
    }

    /** Called from getResult to format multiple winner names */
    static private String toWinnerString(Class winners[]) {
        String s = "";
        for (int i = 0; i < winners.length; ++i) {
            if (i > 0) {
                s += " and ";
            }
            s += shortName(winners[i].getName());
        }

        if (winners.length > 1) {
            s += " win";
        } else {
            s += " wins";
        }
        return s;
    }


    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                          Map Parser                                   //
    //                                                                       //


    static private String readQuotedString(Scanner scanner) {
        return scanner.next("\"[^\"]*\"");
    }

    static public String readMapTitle(String mapFilename) {
        String title = mapFilename;
        try {
            if (! new File(mapFilename).exists()) {
                mapFilename += ".map";
            }
            Reader reader = new BufferedReader(new FileReader(mapFilename));
            Scanner scanner = new Scanner(reader);
            
            if (! scanner.hasNextInt()) {
                String gfxPack = readQuotedString(scanner);
            }

            int width = scanner.nextInt();
            int height = scanner.nextInt();
            title = scanner.nextLine().trim();
            if (title.length() == 0) {
                title = mapFilename;
            }
        } catch (java.io.FileNotFoundException e) {
            
        }

        return title;
    }


    private void loadGraphicsPack(String graphicsPackFilename) {
        System.out.println(graphicsPackFilename);
        try {
            final StreamTokenizer t = new StreamTokenizer(new BufferedReader(new FileReader(graphicsPackFilename)));
            t.eolIsSignificant(false);
            t.slashSlashComments(true);
            t.slashStarComments(true);
            t.nextToken();
            
            StaticEntity.wall.image = loadImage(t.sval, "Wall.png");
            t.nextToken();
            
            StaticEntity.wall2.image = loadImage(t.sval, "Wall2.png");
            t.nextToken();
            
            StaticEntity.wall3.image = loadImage(t.sval, "Wall3.png");
            t.nextToken();
            
            StaticEntity.thorn.image = loadImage(t.sval, "Thorn.png");
            t.nextToken();

            StaticEntity.floor.image = loadImage(t.sval, "Floor.png");
            t.nextToken();

            StaticEntity.mud.image = loadImage(t.sval, "Mud.png");
            t.nextToken();

            StaticEntity.fog = loadImage(t.sval, "Fog.png");
            t.nextToken();
        } catch (java.io.FileNotFoundException e) {
            System.err.println(e);
        } catch (java.io.IOException e) {
            System.err.println(e);
        }
    }


    private void loadMap(String mapFilename, Class[] creatures) {
        try {
            gameMode = GameMode.NATURAL_SELECTION;
            numCompetitors = 0;
            
            if (! new File(mapFilename).exists()) {
                mapFilename += ".map";
            }
            
            Reader reader = new BufferedReader(new FileReader(mapFilename));
            // Make the file reader so that we can back up after reading
            // the first line.
            reader.mark(2000);
            Scanner scanner = new Scanner(reader);
        
            // Read the (optional) graphics pack
            String gfx = "default.gfx";
            if (! scanner.hasNextInt()) {
                gfx = readQuotedString(scanner);
                // Remove the quotes
                gfx = gfx.substring(1, gfx.length() - 1);
            }
            
            loadGraphicsPack(gfx);

            width = scanner.nextInt();
            height = scanner.nextInt();
            title = scanner.nextLine().trim();
            if (title.length() == 0) {
                title = mapFilename;
            }
            System.out.println("Loading map " + title);
            
            map = new Entity[width][height];
            environment = new Environment[width][height];
            for (int x = 0; x < width; ++x) {
                for (int y = 0; y < height; ++y) {
                    environment[x][y] = new Environment();
                }
            }

            parse(reader, creatures);

            assignShrineClassIds();

        } catch (FileNotFoundException e) {
            System.err.println(e);
            System.exit(-1);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(-1);
        }
    }


    /** If location (x, y) is not a wall, make it one */
    private void forceWall(int x, int y) {
        if ((map[x][y] == null) || 
            ((map[x][y].getType() != Type.WALL) &&
             (map[x][y].getType() != Type.HAZARD))) {
            map[x][y] = StaticEntity.wall;
        }
    }


    /** Called from loadMap. */
    private void parse(Reader reader, Class[] creatures) {
        // Now read directly from the file
        // Intentionally use the same seed every time to make maps deterministic
        final Random rnd = new Random();

        try {
            // Skip the first line
            reader.reset();
            readToEndOfLine(reader);

            for (int y = 0; y < height; ++y) {
                readMapLine(reader, creatures, y, rnd);
            }


        } catch (IOException e) {
            // Done!
        }

        // Force walls around the outside
        for (int x = 0; x < width; ++x) {
            forceWall(x, 0);
            forceWall(x, height - 1);
        }
        
        for (int y = 1; y < height - 1; ++y) {
            forceWall(0, y);
            forceWall(width - 1, y);
        }
    }


    /** Called from loadMap to assign classIds to all shrines. */
    private void assignShrineClassIds() {
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                Environment e = environment[x][y];
                if (e.shrineClassId == Environment.TO_BE_DETERMINED) {
                    e.shrineClassId = findClosestSpawnClassId(x, y);
                }
            }
        }
    }


    private int square(int x) { return x * x; }

    /** Finds the classId of the spawn point nearest to (x, y) */
    private int findClosestSpawnClassId(int x0, int y0) {
        int distance = 100000000;

        int classId = Creature.UNINITIALIZED_CLASS_ID;
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                Entity e = map[x][y];

                if ((e != null) && (e.getType() == Type.CREATURE) &&
                    (e.getClassId() != Creature.APPLE_CLASS_ID) &&
                    (e.getClassId() != Creature.TREASURE_CLASS_ID) &&
                    (e.getClassId() != Creature.FLYTRAP_CLASS_ID)) {
                    
                    
                    int d = square(x0 - x) + square(y0 - y);
                    if (d < distance) {
                        distance = d;
                        classId = e.getClassId();
                    }
                }
            }
        }

        return classId;
    }


    /** Produces a consistent direction as a function of x and y */
    static private Direction directionHash(int x, int y) {
        return Direction.EAST.left(((Integer)x).hashCode() ^ ((Integer)y).hashCode());
    }


    /** Read one line from the map file. Called from loadMap. */
    private void readMapLine(Reader reader, Class[] creatures, int y, Random rnd) throws IOException {

        int c = '\0';
        for (int x = 0; x < width; ++x) {
            c = reader.read();

            // Give objects random start offset times so that the
            // order of appearance in the map doesn't bias the first
            // round scheduling.
            final long t = rnd.nextInt(1000);

            if (c == '\n') {
                // End of this line; abort the FOR loop
                break;
            } else if ((c == 'X') || (c == 'x')) {
                // Wall
                map[x][y] = StaticEntity.wall;
            } else if (c == '#') {
                // Alternative color wall 2
                map[x][y] = StaticEntity.wall2;
            } else if (c == '%') {
                // Alternative color wall 3
                map[x][y] = StaticEntity.wall3;
            } else if ((c == '+') || (c == 'T')) {
                // Thorn
                map[x][y] = StaticEntity.thorn;
            } else if ((c == 'a') || (c == 'A') || (c == 'e') || (c == 'E')) {
                // Apple
                spawn(Apple.class, new Point(x, y), Direction.EAST, (c == 'E') || (c == 'e'), t);
            } else if (c == '*') {
                // Treasure
                spawn(Treasure.class, new Point(x, y), Direction.EAST, false, t);
                gameMode = GameMode.MAZE;
            } else if ((c == 'f') || (c == 'F')) {
                // Flytrap
                spawn(Flytrap.class, new Point(x, y), directionHash(x, y), false, t);
            } else if (Character.isDigit(c)) {
                // Creature
                final int i = c - '0';
                if (creatures.length > i) {
                    numCompetitors = Math.max(numCompetitors, i + 1);

                    final Class creatureClass = creatures[i];

                    int classID = Creature.UNINITIALIZED_CLASS_ID;

                    if (creatureClass != null) {
                        
                        // Define a new unique creature ID if required
                        if (! speciesTable.containsKey(creatureClass)) {
                            classID = newCreatureClassId();
                            Species s = new Species(creatureClass, classID);
                            speciesTable.put(creatureClass, s);
                        }

                        spawn(creatureClass, new Point(x, y), directionHash(x, y), false, t);
                    } else {
                        System.err.println("Warning: creature #" + i + " could not be instantiated.");
                    }

                    if (creatureColor.get(classID) == null) {
                        // Choose a color for this creature
                        //creatureColor.put(classID, colorStack.pop());
                        BufferedImage image = (BufferedImage)getImage(creatureClass, Direction.EAST);

                        creatureColor.put(classID, (image == null) ? colorStack.pop() : getColor(image));
                    }
                    
                } else {
                    System.err.println("Warning: ignored unspecified creature #" + i + " in map.");
                }
            } else {
                // Anything else should be left null; it is an empty spot

                if ((c == ':') || (c == 'F') || (c == 'A') || (c == 'T') || (c == 'E')) {
                    // Fogged square
                    environment[x][y].inFog = true;
                } else if (c == '.') {
                    environment[x][y].inMud = true;
                } else if ((c == 's') || (c == 'S')) {
                    environment[x][y].shrineClassId = Environment.TO_BE_DETERMINED;
                } // Environment

            } // Type
        }

        
        // Read to the end of the line, discarding additional characters
        if (c != '\n') {
            readToEndOfLine(reader);
        }
    }


    /** Throws IOException if the end of file is reached. */
    static private void readToEndOfLine(Reader reader) throws IOException {
        int c = 0;
        while (c != '\n') {
            c = reader.read();
            if (c == -1) {
                throw new IOException();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                            Methods                                    //
    //                                                                       //

    /** Load a new map and start a simulation.  The simulation is
        initially paused. Called from the start button and the constructor. */
    public void start(String mapfile, Class[] creatures) {
        // Do not synchronize: synchronization on this happens after
        // we grab the performance lock.

        if (live) {
            throw new IllegalArgumentException("Simulation already in progress.");
        }

        competitorCreatureClasses = creatures;

        // We need to be holding the performance lock to avoid a
        // deadlock where the swing thread grabs the performanceLock
        // and this thread grabs the lock on this. 

        synchronized (performanceLock) {
            synchronized (this) {
                assert creatures != null;
                proxyTable.clear();
                intraTurnTime = PAUSE_TIME;
                creatureCount.clear();
                turnQueue.clear();
                spawnQueue.clear();
                deathQueue.clear();
                selectedCreature = null;
                creatureThreadGroup = new ThreadGroup("Creatures");
                
                totalElapsedTime = 0;
                
                prepareClassIds();
                loadMap(mapfile, creatures);
                live = true;
                
                simThread = new Thread(new Runnable() {
                        public void run() {
                            while (getResult() == null) {
                                oneTurn();
                                // Must process deaths first to clear map squares
                                processDeathQueue();
                                processSpawnQueue();
                                waitBetweenTurns();
                            }
                            stop();
                        }}, "Simulation thread");
            
                simThread.start();
            }
        }
    }

    
    /** End simulation, without destroying final state. */
    synchronized public void stop() {
        // Pause play
        setIntraTurnTime(PAUSE_TIME);
        live = false;

        // End the simulation
        stopThread(simThread);
        stopThreadGroup(creatureThreadGroup);
        simThread = null;
    }


    /** Runs one turn for the highest-priority creature. */
    private void oneTurn() {
        
        if (turnQueue.isEmpty()) {
            // There's nothing to do
            Thread.yield();
            return;
        }

        final Proxy proxy = turnQueue.peek();
        assert isAlive(proxy.creature); 
        
        // Wake up the creature's thread. (It conveniently waits on its
        // own proxy; a Creature CANNOT wait on itself, since that is
        // visible to players.)
        synchronized (proxy) { proxy.notify(); }

        // The creature might have died immediately after waking
        if (isAlive(proxy.creature)) {
            oneTurn(proxy);
        } else {
            // Make sure it was removed from the queue
            assert ! turnQueue.contains(proxy);
        }
    }


    // We need to be able to temporarily prevent the swing thread from
    // running during a creature's turn, without locking the simulator
    // itself so that the simulator thread and the creature thread
    // can both run without being blocked.
    final private Object performanceLock = new Integer(0);

    /** Run the Creature described by info for one turn. Assumes that
        the creature's thread is already awake. 

        Updates the totalTimeSinceGameStart and inserts the creature into the
        turnQueue, if the creature is not dead at the end of the turn. 

        Updates this.totalElapsedTime with the virtual time spent by
        the creature.
    */
    private void oneTurn(Proxy proxy) {
        assert (spawning || ! Thread.holdsLock(this)) : 
        "Should not enter oneTurn while holding the simulator lock";

        assert (spawning || (Thread.currentThread() == simThread)) :
        ("Ran oneTurn on the wrong thread (" + Thread.currentThread() + ")");

        assert isAlive(proxy.creature): 
        ("Tried to run oneTurn() on " + proxy.creature.getClassName() + 
         " #" + proxy.creature.getId() + ", which is not in the world.");

        // Previous sum of CPU clock time and action time, used to
        // compute the net cost
        final long startCPUTime = proxy.totalTimeSinceGameStart;

        // virtual CPU clock time taken for this creature's computation
        long elapsedTime;

        // Run until the thread waits or runs out of time. We can't
        // trust the creature to not crash or lock up, so we have to
        // actively monitor it instead of using some kind of lock or
        // wait() call.  We have to distinguish between waiting and
        // timed waiting/blocked because if a thread is in a timed
        // wait it could wake itself up.
        Thread.State s;

        // We synchronize so that threads are not penalized for
        // blocking on the simulator mutex when the swing thread
        // renders the maze.
        long elapsedWallTime = 0;
        synchronized (performanceLock) {

            final long wallClockStart = System.nanoTime();
            currentTurnThreadStartTime = getThreadUserTime(proxy.thread);

            do {
                // The simulator thread is busy-waiting, so yield its quantum
                Thread.yield();
                try {
                    nanoSleep(1000);
                } catch (Exception e) {}
                s = proxy.thread.getState();
                
                assert (s != Thread.State.NEW) : 
                "Creature thread reached oneTurn() without having been started.";
                
                elapsedWallTime = realCPUToVirtualCPUTime(System.nanoTime() - wallClockStart);
                
            } while ((s != Thread.State.TERMINATED) && 
                     (s != Thread.State.WAITING) && 
                     (elapsedWallTime < KILL_TIME) &&
                     isAlive(proxy.creature));

            // At this point, totalTimeSinceGameStart has been
            // incremented by the thread to account for an action
            // taken, if it invoked endTurn().

            elapsedTime = realCPUToVirtualCPUTime(getThreadUserTime(proxy.thread) - currentTurnThreadStartTime);
        }

        // Kill based on wall-clock time so that blocking (i.e.,
        // system time) still counts against a creature.
        if (elapsedWallTime >= KILL_TIME) {
            System.err.println("\nA " + proxy.creature.getClassName() + 
                               " who stopped responding after " + elapsedWallTime + 
                               " ns was turned into an Apple.");
            if (s == Thread.State.BLOCKED) {
                // Give the programmer some more information.
                System.err.println("Its execution was blocked.");
            }
            System.err.println("Its stack trace was:");
            for (java.lang.StackTraceElement st : proxy.thread.getStackTrace()) {
                System.err.println(" at " + st);
            }
            System.err.println();

            // Kill the creature because it took too much time
            if (isAlive(proxy.creature)) {
                killLater(proxy);
            }

            spawnLater(Apple.class, proxy.position, Direction.random(), proxy.isEnchanted, proxy.totalTimeSinceGameStart);

        } else {

            if (s == Thread.State.TERMINATED) {
                // The process ended
                elapsedTime = 0;
            }
            
            // Assume that the overhead of managing the threads takes
            // at least this much time, so don't count it against
            // creatures because it would make their percent
            // computation times appear inflated.
            final long OVERHEAD_TIME_NS = 40000;

            // Assume that even doing nothing takes at least this much time
            final long MIN_TIME_NS      = 100;
            elapsedTime = Math.max(elapsedTime - OVERHEAD_TIME_NS, MIN_TIME_NS);

            // The creature is waiting, so update its time and priority

            // Lock the simulator while we manipulate the queue
            synchronized (this) {
                // Remove to update (or actually remove) the creature.
                turnQueue.remove(proxy);

                // Account for the compute cost of the turn in the proxy
                proxy.addComputeTime(elapsedTime);
                
                // Don't bother putting back in the queue if
                // terminated...the creature is still alive, but is
                // not active; or if it actually is dead (due to walking
                // into a thorn).
                if ((s != Thread.State.TERMINATED) && isAlive(proxy.creature)) {
                    turnQueue.add(proxy);
                }
                
                // elapsedTime doesn't count the action time cost, so
                // account for it by subtracting the starting virtual
                // time.  Count the creature's execution time against the
                // total game time limit
                final long dt = proxy.totalTimeSinceGameStart - startCPUTime;
                totalElapsedTime += Math.max(0, dt);
            }
        }
    }

    /** Creates but does not start the thread for a creature.  Abstracts
        some exception handling and startup behavior. 

        @see spawn */
    private Thread makeCreatureThread(final Proxy proxy, final Creature c, int id) {
        return new Thread
            (creatureThreadGroup,
             new QuietRunnable() {
                 public void runQuiet() {
                     // Immediately end the first turn so that this
                     // creature is waiting.  Without this, multiple
                     // threads would be running at once and the user
                     // code might experience deadlocks or race
                     // conditions in Creature subclasses.
                     proxy.endTurn(0);

                     // Pass off to the creature's run method
                     c.run();
                 }},
             c.getClassName() + "_" + id);
    }

    /** Wait the intraTurnTime in wall-clock time. If intraTurnTime is changed
        while in delay, the new time will immediately be honored. */
    // Cannot be synchronized because stop() needs to be called while
    // this is running
    private void waitBetweenTurns() {
        try {

            // Sleep in relatively small increments to ensure
            // that the system is responsive to sleep time changes
            // when it is paused or running slowly.

            final long MILLISECONDS = 1000000;

            long timePassed = 0;
            long timeLeft = getIntraTurnTime() - timePassed;

            if (timeLeft < 0) {
                // Integer wrap around must have occured
                timeLeft = Long.MAX_VALUE;
            }

            while (timeLeft > 0) {
                // Never sleep more than 100 ms or less than 1 ns so
                // as to be responsive to speed change buttons.
                final long sleepTime = Math.min(Math.max(1, timeLeft), 100 * MILLISECONDS);

                nanoSleep(sleepTime);

                timePassed += sleepTime;
                
                // We must update this every time around the loop because
                // the delay time may have changed while we were sleeping.
                timeLeft = getIntraTurnTime() - timePassed;
            }

        } catch (InterruptedException e) {
            // System.err.println("Interupted during delay");
            // The simulator is probably being shut down
        }
    }

    /** Has nanoSleep been tested? */
    static private boolean nanoTested = false;

    /** Does System.sleep have nano-second accuracy */
    static private boolean nanoThreadOk = false;

    /** On some systems, Thread.sleep(long, int) has a minimum sleep time of 1ms.
        This implements true nano-second sleep intervals even on such systems. */
    // Must not be synchronized, and has no object anyway
    static public void nanoSleep(long t) throws InterruptedException {
        final long million = 1000000;

        if (! nanoTested) {
            // See if Thread.sleep has nanosecond accuracy on this system
            final int desired = 250000;
            final long t0 = System.nanoTime();
            Thread.sleep(0, desired);
            final long t1 = System.nanoTime();
            final int actual = (int)(t1 - t0);
            nanoThreadOk = (Math.abs(actual - desired) < 10000);
            nanoTested = true;

            if (t > desired) {
                nanoSleep(t - desired);
            }

        } else if (nanoThreadOk) {

             Thread.sleep(t / million, (int)(t % million));
        } else {
            long ms = t / million;
            int  ns = (int)(t % million);
            
            // Millisecond sleep
            if (ms > 0) {
                Thread.sleep(ms);
            }
            
            // Nano sleep
            long t1 = System.nanoTime() + ns;
            while (System.nanoTime() < t1) {
                Thread.yield();
            }
        }
    }


    /** Returns a printable text representation of the map */
    synchronized public String toString() {
        String s = title + " ("  + width + " x " + height + ")\n";
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                Entity e = map[x][y];
                if (e == null) {
                    s += ' ';
                } else {
                    s += e.getLabel();
                }
            }
            s += '\n';
        }
        return s;
    }

    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                           The Actions                                 //
    //              (Called by Creatures on their threads)                   //
    //                                                                       //

    /** Called by a creature to turn */
    // DO NOT SYNCHRONIZE
    public void turnRight(Creature e) {
        Proxy proxy;
        long cost = TURN_COST;
        synchronized (this) {
            checkThread(e);
            proxy = getProxy(e);
            proxy.direction = proxy.direction.right();
            if (environment[proxy.position.x][proxy.position.y].inMud) {
                cost += MUD_PENALTY_COST;
            }
            ++proxy.species.turnCount;
        }
        proxy.endTurn(cost);
    }

 
    // DO NOT SYNCHRONIZE
    public void turnLeft(Creature e) {
        Proxy proxy;
        long cost = TURN_COST;
        synchronized (this) {
            checkThread(e);
            proxy = getProxy(e);
            proxy.direction = proxy.direction.left();
            if (environment[proxy.position.x][proxy.position.y].inMud) {
                cost += MUD_PENALTY_COST;
            }
            ++proxy.species.turnCount;
        }
        proxy.endTurn(cost);
    }


    // DO NOT SYNCHRONIZE
    public void emitPheromone(Creature e, String p) {
        checkThread(e);
        final Proxy proxy = getProxy(e);
        synchronized (this) {
            ++proxy.species.emitCount;

            // Override the current pheromone, if any
            if (p == null) {
                p = "";
            }
            environment[proxy.position.x][proxy.position.y].pheromone = p;
        }
        proxy.endTurn(EMIT_PHEROMONE_COST);
    }


    // DO NOT SYNCHRONIZE
    public Observation[] observe(Creature e) {
        checkThread(e);
        final Proxy proxy = getProxy(e);

        // Observe occurs at the beginning of the next turn
        proxy.endTurn(OBSERVE_COST);

        ArrayList<Observation> array = new ArrayList<Observation>();
        synchronized (this) {
            ++proxy.species.observeCount;

            Point p = proxy.position;
            array.add(observe((Point)p.clone()));
            Observation obs = null;
            do {
                // Advance to the new position (this guarantees
                // that p is a new Point as well)
                p = proxy.direction.forward(p);
                
                obs = observe(p);
                assert obs != null : "observe(Point) returned null";
                array.add(obs);
            } while (inBounds(p) && (obs.type == Type.EMPTY));
        }
        Collections.reverse(array);
        return array.toArray(new Observation[0]);
    }


    /** Moves this creature forward if the space in front if it is
        empty, otherwise does not move.  Moving into a Thorn causes a
        Creature to be converted into an Apple.
 
        <p>
        Throws ConvertedError if the Creature is not in the world.
        </p>

        @param distance must be -1 or +1
        @return true if moved.
    */
    public boolean move(Creature e, int distance) {
        // DO NOT SYNCHRONIZE: this needs to block until the next turn
        Proxy proxy;
        boolean result;
        long cost;

        synchronized (this) {
            if ((distance != -1) && (distance != +1)) {
                throw new IllegalArgumentException("bad distance on move: " + distance);
            }
            
            checkThread(e);
            proxy = getProxy(e);
            final Point nextPos = proxy.direction.forward(proxy.position, distance);

            final boolean wasInMud = environment[proxy.position.x][proxy.position.y].inMud;

            if (isEmpty(nextPos)) {
                map[proxy.position.x][proxy.position.y] = null;
                
                proxy.position = nextPos;
                map[proxy.position.x][proxy.position.y] = e;

                if (proxy.isEnchanted &&
                    (environment[proxy.position.x][proxy.position.y].shrineClassId == proxy.creature.getClassId())) {
                    // Moved onto shrine: ascend
                    killLater(proxy);

                    // Record the ascension
                    ++proxy.species.ascensions;

                    // Spawn an apple at a random location
                    spawnEnchantedAppleLater(proxy.totalTimeSinceGameStart);
                }
                
                result = true;
            } else if (inBounds(nextPos) && 
                       (map[nextPos.x][nextPos.y].getType() == Type.HAZARD)) {
                // Moved onto a thorn
                killLater(proxy);
                
                spawnLater(Apple.class, proxy.position, Direction.random(), proxy.isEnchanted, proxy.totalTimeSinceGameStart);
                result = false;
            } else {
                // Blocked by something else
                result = false;
            }
            
            if (distance > 0) {
                cost = MOVE_FORWARD_COST;
                if (environment[proxy.position.x][proxy.position.y].inFog) {
                    // Moved forward into fog
                    cost += FOG_MOVE_FORWARD_PENTALTY_COST;
                }
            } else {
                cost = MOVE_BACKWARD_COST;
            }

            if (wasInMud) {
                cost += MUD_PENALTY_COST;
            }

            ++proxy.species.moveCount;

        } // synchronized(this)

        proxy.endTurn(cost);

        return result;
    }


    /**
       Spawn an apple at a random location, not too near to a shrine or
       other creature.
    */
    private void spawnEnchantedAppleLater(long totalTimeSinceGameStart) {
        // Find an empty location
        Point p = new Point();

        // Try many times, and then give up
        final int NUM_TRIES = 40;
        for (int t = 0; t < NUM_TRIES; ++t) {
            p.x = Math.min(width - 2, 1 + (int)(Math.random() * (width - 1)));
            p.y = Math.min(height - 2, 1 + (int)(Math.random() * (height - 1)));

            Entity      m = map[p.x][p.y];
            Environment e = environment[p.x][p.y];


            if ((m == null) && (e.shrineClassId == Creature.UNINITIALIZED_CLASS_ID)) {
                // This square is empty.  Are the neighbors unoccupied as well?
                boolean     neighborhoodOk = true;

                for (int dx = -1; dx <= +1; ++dx) {
                    for (int dy = -1; dy <= +1; ++dy) {
                        m = map[p.x][p.y];
                        e = environment[p.x][p.y];
                        if (((m != null) && ((m.getType() != Type.WALL) || (m.getType() != Type.HAZARD))) || 
                            (e.shrineClassId != Creature.UNINITIALIZED_CLASS_ID)) {
                            // There is a shrine or creature too close to this square
                            neighborhoodOk = false;
                        }
                    }
                }
                
                if (neighborhoodOk) {
                    // Found a good spot to spawn the apple
                    spawnLater(Apple.class, p, Direction.random(), true, totalTimeSinceGameStart);
                    return;
                }
            }
        }
        
        System.err.println("Warning: unable to re-spawn enchanged apple");
    }

    /** Take the delay action  */
    // DO NOT SYNCHRONIZE: this needs to block until the next turn
    public void delay(Creature c) {
        checkThread(c);
        Proxy proxy = getProxy(c);
        proxy.endTurn(DELAY_COST);
    }

    ////////////////////////////////////////////////////////////////////////////////////////

    private Observation observe(Point p) {
        // TODO: add enchantment, shrine
        if (inBounds(p)) {
            final Entity  e   = map[p.x][p.y];
            final boolean f   = environment[p.x][p.y].inFog;
            final boolean m   = environment[p.x][p.y].inMud;
            final int     sid = environment[p.x][p.y].shrineClassId;
            
            if (e == null) {
                // Empty square
                return new Observation(p, getTime(), f, m, sid);

            } else if (e instanceof Creature) {

                final Creature c = (Creature)e;
                final Proxy proxy = proxyTable.get(c);
                final String s = isSecure ? null : c.getClassName();
                return new Observation(p, s, c.getClassId(), c.getId(), proxy.direction, getTime(), f, m, sid);

            } else if (e instanceof StaticEntity) {

                return new Observation(p, e.getType(), e.getClassId(), getTime(), f, m, sid);

            } else {
                assert false : "Internal error; unknown Entity type: " + e;
                return null;
            }
        } else {
            // Out of bounds
            return new Observation(p, Type.WALL, Creature.WALL_CLASS_ID, getTime());
        }
    }

    /** Attack the creature in front of this one. If there is a
        creature there that is not the same class as this creature,
        kill it and spawn another instace of this creature facing in
        the same direction as this creature.

        If the creature in front is of the same class as this creature
        and this creature is enchanted, move the enchantment to the
        attacked creature.
    */
    // DO NOT SYNCHRONIZE: we must be able to end the turn
    public boolean attack(Creature e) {
        checkThread(e);
        final Proxy proxy = getProxy(e);
        
        Point attackPos;
        boolean success = false;
        Creature targetCreature = null;
        
        boolean passEnchantment = false;

        boolean wasInMud = false;
        synchronized (this) {
            ++proxy.species.attackCount;

            attackPos = proxy.direction.forward(proxy.position);
            
            wasInMud = environment[proxy.position.x][proxy.position.y].inMud;

            if (inBounds(attackPos)) {
                // See what was attacked
                final Entity target = map[attackPos.x][attackPos.y];
                if ((target != null) && (target instanceof Creature)) {
                    
                    targetCreature = (Creature)target;
                    
                    if (! e.getClass().isAssignableFrom(targetCreature.getClass())) {
                        // Not the same class: the attack succeeds
                        success = true;
                    } else if (e.isEnchanted() && ! targetCreature.isEnchanted()) {
                        // Pass the enchantment
                        success = true;
                        passEnchantment = true;
                    }
                }
            }
        }

        if (success) {
            Proxy targetProxy = proxyTable.get(targetCreature);

            if (passEnchantment) {
                targetProxy.isEnchanted = true;
                proxy.isEnchanted = false;
            } else {

                killLater(targetProxy);
                
                // Spawn a new one of the attacking creature.  It is
                // important that the new creature gets my time BEFORE I
                // pay for the attack so that he can move quickly. This is
                // what allows two creatures working together to take out
                // one that has holed up in a U-shaped cavity.  The first
                // creature in will be converted, but then the second one
                // converts him back and he attacks before the holed-up
                // creature can attack again.
                //
                // Likewise, make child creatures appear rotated from
                // their parent 50% of the time.  Never appear facing
                // away from the parent, because that would make it too
                // easy to get into standoffs.
                
                Direction d = proxy.direction.opposite();
                int r = (int)(Math.random() * 5);
                if (r == 1) {
                    d = d.left();
                } else if (r == 2) {
                    d = d.right();
                }
                spawnLater(e.getClass(), attackPos, d, targetProxy.isEnchanted, proxy.totalTimeSinceGameStart + DELAY_COST);
            }
        }

        long cost = ATTACK_COST;
        if (wasInMud) {
            cost += MUD_PENALTY_COST;
        }

        proxy.endTurn(ATTACK_COST);

        return success;
    }

    /** Schedule a creature to be killed at the end of the current turn. */
    private void killLater(Proxy proxy) {
        assert deathQueue.size() == 0 :
        "There is no way for two creatures to die on the same turn.";

        deathQueue.add(proxy);
    }


    /** Kill target creature, removing it from the world. Called from processDeathQueue. */
    private void kill(Proxy proxy) {
        final Creature creature = proxy.creature;

        decCount(creature.getClass());

        // Remove target from world (this prevents it from taking further actions)
        turnQueue.remove(proxy);

        proxyTable.remove(creature);
        map[proxy.position.x][proxy.position.y] = null;

        stopThread(proxy.thread);
    }


    /** Increments the instance count of creature class c */
    private void incCount(Class c) {
        Integer i = creatureCount.get(c);
        if (i == null) {
            i = 0;
        }
        creatureCount.put(c, (int)i + 1);
    }


    /** Decrements the count of creature class c */
    private void decCount(Class c) {
        int i = (int)creatureCount.get(c);
        --i;
        if (i == 0) {
            // Removed the last instance of this creature
            creatureCount.remove(c);
        } else {
            creatureCount.put(c, i);
        }
    }


    /** Returns the N-most populous creatures' classes, excluding
        Flytraps, Treasures, and Apples. */
    //@SuppressWarnings("unchecked")
    private Class[] getMostPopulousSpecies(final int N) {
        
        ArrayList<Class> creatures = new ArrayList<Class>();
        for (Class c : creatureCount.keySet().toArray(new Class[0])) {
            if ((c != Flytrap.class) && (c != Treasure.class) && (c != Apple.class)) {
                creatures.add(c);
            }
        }

        Collections.sort(creatures, new Comparator<Class>() {
                public int compare(Class a, Class b) {
                    return creatureCount.get(b) - creatureCount.get(a);                    
                }
            });
        
        // Return only the first N values
        final Class[] best = new Class[Math.min(N, creatures.size())];
        System.arraycopy(creatures.toArray(), 0, best, 0, best.length);
        return best;
    }

    // Suppresses the thread stop deprecation warning. 
    @SuppressWarnings("deprecation")
    private void stopThread(Thread t) {
        //assert ! t.holdsLock(this) : 
        //"Thread " + Thread.currentThread().getName() + " tried to stop " + 
        //    "thread " + t.getName() + ", which was locking the simulator.";
        t.interrupt();
        try {
            Thread.sleep(1);
        } catch (java.lang.InterruptedException e) {}
        t.stop();
    }

    // Suppresses the thread stop deprecation warning.
    @SuppressWarnings("deprecation")
    private void stopThreadGroup(ThreadGroup t) {
        t.interrupt();
        try {
            Thread.sleep(1);
        } catch (java.lang.InterruptedException e) {}
        t.stop();
        
    }

    /** Ensures that a creature is alive and running on the right
        thread.  Must be called from the thread that the creature is
        simulated on; that ensures that a Creature is not cheating by
        using another member of its species to execute its moves for
        it. */
    private void checkThread(Creature e) {
        Proxy proxy = proxyTable.get(e);
        
        if (proxy == null) {
            throw new ConvertedError("Creature" + e.getClassName() + "_" + e.getId() + 
                            " was prohibited from taking an action because it is dead.");
        } else if (Thread.currentThread() != proxy.thread) {
            throw new Error(proxy.thread + " tried to take an action on " + 
                            Thread.currentThread() + "'s turn.");
        }

        if (! live) {
            throw new ConvertedError("Creature was prohibited from taking an" + 
                                     " action because the game is over.");
        }
    }

    /** Returns the underlying object (if mutated, that will affect
        the Creature.) */
    private Proxy getProxy(Creature e) {
        return proxyTable.get(e);
    }

    /** A creature's thread can't be stopped without potentially
      releasing locks that it is using for synchronization, so this
      method tells a creature when it should die.  Creatures are not allowed
      to move when dead.
      <p>
      All creatures are considered dead once the simulator is stopped.
    */
    public boolean isAlive(Creature c) {
        // Not synchronized because this must be called from oneTurn
        return (proxyTable.get(c) != null);
    }


    /** Schedule this creature to be spawned, but don't do it on the
     current thread.  Call this when the game is running and you want
     to spawn a creature that is not an Apple or Treasure.*/
    private void spawnLater(Class c, Point p, Direction d, boolean enchanted, long initialCPUTime) {
        spawnQueue.add(new DelayedSpawn(c, p, d, enchanted, initialCPUTime));
    }


    /** Invokes onDeath on a new thread that does not affect game time
        and may run for the normal KILL_TIME limit. */
    private void invokeOnDeathMethod(final Proxy proxy) {
        // Don't put this thread in a threadgroup...Java doesn't seem
        // to collect the threads that are forced to stop if they are
        // in a threadgroup and wille eventually run out.
        final Runnable deathJob = new QuietRunnable() {
                public void runQuiet() {
                    proxy.creature.onDeath();
                }};

        final WorkerThread deathThread = threadPool.allocate();
        deathThread.startJob(deathJob);
        
        // Launch death thread, but monitor its running time
        final long wallClockStart = System.nanoTime();
        long elapsedWallTime;
        do {
            // The simulator thread is busy-waiting, so yield its quantum
            Thread.yield();
            try { nanoSleep(1000); } catch (Exception e) {}
                
            elapsedWallTime = System.nanoTime() - wallClockStart;
                
        } while (deathThread.working() && (elapsedWallTime < KILL_TIME));


        if (deathThread.getState() == Thread.State.BLOCKED) {
            // Give the programmer some more information.
            System.err.println(proxy.creature.getClassName() +"'s execution was blocked in onDeath() with stack trace:");
            for (java.lang.StackTraceElement st : deathThread.getStackTrace()) {
                System.err.println(" at " + st);
            }
        }

        if (deathThread.recycleable()) {
            // The thread exited normally
            threadPool.recycle(deathThread);
        } else if (deathThread.isAlive()) { 
            // Kill the death thread...it is running without our
            // permission now
            stopThread(deathThread);
        }
    }


    /** Called at the end of oneTurn() */
    private void processDeathQueue() {
        assert Thread.currentThread() == simThread;
        for (Proxy proxy : deathQueue) {
            invokeOnDeathMethod(proxy);
            kill(proxy);
        }
        deathQueue.clear();
    }


    /** Called at the end of oneTurn() */
    private void processSpawnQueue() {
        assert Thread.currentThread() == simThread;
        for (DelayedSpawn d : spawnQueue) {
            d.proxy = spawn(d.creatureClass, d.point, d.direction, d.isEnchanted, d.initialCPUTime);
        }
        
        // Allow each spawned creature to take a turn
        for (DelayedSpawn d : spawnQueue) {
            oneTurn(d.proxy);
        }

        spawnQueue.clear();
    }


    /** Spawns a Creature of class c at position p. c must be a
        subclass of Creature.

        Creates the Proxy for the creature and inserts it into the
        world.  When this returns the Creature's thread is either
        Thread.State.BLOCKED or Thread.State.TERMINATED.

        @see makeCreatureThread 
    */
    private Proxy spawn(Class c, Point p, Direction d, boolean enchanted, long initialCPUTime) {
        assert (! live) || (Thread.currentThread() == simThread) :
        "Do not call spawn from a creature's thread--they could deadlock.";

        assert (! live) || (! Thread.currentThread().holdsLock(performanceLock)) :
        "Do not call spawn while holding the performanceLock--it could deadlock with the renderer.";

        assert inBounds(p);
        assert map[p.x][p.y] == null;

        Creature creature = null;
        
        incCount(c);

        // Instantiate it
        try {
            creature = (Creature)c.newInstance();
        } catch (ClassCastException e) {
            System.err.println("Spawned creature was not a Creature");
            return null;
        } catch (InstantiationException e) {
            System.err.println(e + " while attempting to spawn " + c.getName() +
                               ": " + e.getMessage());
            //e.printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        } catch (ExceptionInInitializerError e) {
            e.printStackTrace();
            return null;
        } catch (NoClassDefFoundError e) {
            // Creature tried to load an illegal class
            System.err.println(c.getName() + " tried to access a prohibited class, or " + 
                               "one not found in the CLASSPATH, and was" +
                               " prevented from spawning. (" + e + ")");
            return null;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }


        // Insert into the world
        final Species species = speciesTable.get(c);
        final Proxy proxy = new Proxy(p, d, null, creature, species, enchanted, initialCPUTime);
        proxy.thread = makeCreatureThread(proxy, creature, nextID);
        proxyTable.put(creature, proxy);
        map[p.x][p.y] = creature;
        ++nextID;

        // Cannot set the simulator until after the creature is
        // inserted into the world because creatures immediately ask
        // for their own position.
        creature.setSimulator(this, nextID, species.classId);

        // Start simulating the creature, which will immediately
        // force it into a wait call so that it is ready to be woken.
        proxy.thread.start();

        // Disable the assertion in oneTurn that verifies that we
        // don't have the simulator locked.
        spawning = true;

        assert isAlive(proxy.creature); 

        // Wait for the creature to initialize and take its first
        // action, which is always the delay specified above.  This
        // call also enqueues it in turnQueue.
        oneTurn(proxy);

        spawning = false;

        return proxy;
    }


    /** Returns true if this location is on the map. */
    private boolean inBounds(Point p) {
        return inBounds(p.x, p.y);
    }


    private boolean inBounds(int x, int y) {
        return (x >= 0) && (y >= 0) && (x < width) && (y < height);
    }


    /** Returns true if the position is out of bounds or empty. */
    private boolean isEmpty(Point p) {
        if (inBounds(p)) {
            Entity e = map[p.x][p.y];
            return e == null;
        } else {
            // Out of bounds is not empty
            return false;
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //                                                                       //
    //                        Drawing Methods                                //
    //                                                                       //

    // Size of a 3D grid square (which is drawn on a 45-degree diagonal) in 2D
    final static int XSCALE_3D = 20;
    final static int YSCALE_3D = 10;
    final static int TITLE_HEIGHT_3D = YSCALE_3D * 5; // Reserve room for the title

    /** 2D grid size in pixels. Must be an odd number */
    public static final int SCALE_2D = 17;


    public int getIconHeight() {
        // Returning a larger number prevents resizing
        return 0;
    }
 
    public int getNaturalHeight() {
        if (view3D) {
            return (width + height) * YSCALE_3D + TITLE_HEIGHT_3D;
        } else {
            return height * SCALE_2D + TITLE_HEIGHT_3D;
        }
    }

    public int getIconWidth() {
        return 0;
    }

    public int getNaturalWidth() {
        if (view3D) {
            return (width + height) * XSCALE_3D;
        } else {
            return width * SCALE_2D;
        }
    }


    public void paintIcon(Component c, Graphics _g, int tx, int ty) {
        // Don't try and paint while a creature is swapped in
        synchronized (performanceLock) {
            final Graphics2D g = (Graphics2D)_g;

            final Rectangle rect = c.getBounds();
            final float paintScale =
                Math.min(4.0f, Math.min((float)rect.width / getNaturalWidth(), 
                                        (float)(rect.height - TITLE_HEIGHT_3D) / getNaturalHeight()));

            g.translate(0, TITLE_HEIGHT_3D * paintScale);

            // Center
            g.translate(Math.max((rect.width - getNaturalWidth() * paintScale) / 2, 0.0f), 0); 
            g.scale(paintScale, paintScale);

            if (view3D) {
                paintIcon3D(c, _g, tx, ty);
            } else {
                paintIcon2D(c, _g, tx, ty);
            }
        }
    }

    /** Returns the width */
    private int drawOutlineText(Graphics2D g, String s, Color color, int x, int y) {
        // Draw black outline
        g.setColor(Color.black);
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                g.drawString(s, x + dx, y + dy);
            }
        }

        // Draw color
        g.setColor(color);
        g.drawString(s, x, y);

        final java.awt.geom.Rectangle2D rect = g.getFont().getStringBounds(s, g.getFontRenderContext());
        return (int)rect.getWidth();
    }


    /** Name of a Creature subclass, for printing on the titlebar*/
    private String titleName(Class c) {
        String name = shortName(c.getName());

        final int a = speciesTable.get(c).ascensions;
        
        if (a > 0) {
            name += " [" + a + "]";
        }

        return name;
    }


    private Font titleFont;
    private void drawTitle(Graphics2D g, boolean useColor) {
        if (titleFont == null) {
            titleFont = g.getFont().deriveFont(20.0f).deriveFont(Font.BOLD);
        }
        g.setFont(titleFont);

        // This is only used for computing the width of the whole string
        String caption = "";
        for (int c = 0; c < competitorCreatureClasses.length; ++c) {
            caption += titleName(competitorCreatureClasses[c]);
            if (c < competitorCreatureClasses.length - 1) {
                caption += " vs. ";
            }
        }

        // Draw centered
        final java.awt.geom.Rectangle2D rect = titleFont.getStringBounds(caption, g.getFontRenderContext());
        final int vsWidth = (int)titleFont.getStringBounds(" vs. ", g.getFontRenderContext()).getWidth();
        int x = (getNaturalWidth() - (int)rect.getWidth()) / 2;
        int y = -TITLE_HEIGHT_3D / 2;

        if (useColor) {
            for (int c = 0; c < competitorCreatureClasses.length; ++c) {
                final Class cl = competitorCreatureClasses[c];
                caption = titleName(cl);
                x += drawOutlineText(g, caption,  getCreatureColor(cl), x, y);
                if (c < competitorCreatureClasses.length - 1) {
                    g.setColor(Color.black);
                    g.drawString(" vs. ", x, y);
                    x += vsWidth;
                }
            }
        } else {
            g.drawString(caption, x, y);
        }
    }


    private void paintIcon3D(Component c, Graphics _g, int tx, int ty) {
        final Graphics2D g = (Graphics2D)_g;

        drawTitle(g, true);

        if (! DRAW_FLOOR) {
            drawGrid3D(g);
        }
        
        // For pheromone rendering
        g.setFont(PHEROMONE_FONT_3D);

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                drawEntity3D(g, map[x][y], x, y);
            }
        }
    }


    private void drawEntity3D(Graphics2D g, Entity e, int x, int y) {
        Image im = null;
        final Environment env = environment[x][y];

        if (DRAW_FLOOR) {
            if (env.inMud) {
                drawImage3D(g, x, y, StaticEntity.mud.image);
            } else {
                drawImage3D(g, x, y, StaticEntity.floor.image);
            }

            if (env.shrineClassId != Creature.UNINITIALIZED_CLASS_ID) {
                // Draw the shrine

                // Translate
                final int   tx = projectX(x, y);
                final int   ty = projectY(x, y);

                // Lines coming out of the shrine
                Color color = getCreatureColor(env.shrineClassId);
                g.setColor(color);
                final int N = 10;
                for (int i = 0; i < N; ++i) {
                    final float angle = (float)Math.PI / 2 * i / (N - 1) + (float)Math.PI / 4;
                    final float radius = XSCALE_3D * 4;
                    g.drawLine(tx - XSCALE_3D / 2, ty, 
                               tx + (int)(radius * Math.cos(angle))  - XSCALE_3D / 2, ty - (int)(radius * Math.sin(angle)));
                }

                // Shadow
                g.setColor(new Color(0,0,0,64));
                g.fillOval(tx - XSCALE_3D - 3, ty - (int)(YSCALE_3D * 1.5) - 3, XSCALE_3D + 6, YSCALE_3D + 6);

                g.setColor(Color.WHITE);
                g.fillOval(tx - XSCALE_3D, ty - (int)(YSCALE_3D * 1.6), XSCALE_3D, YSCALE_3D);
                g.setColor(Color.BLACK);
                g.drawOval(tx - XSCALE_3D, ty - (int)(YSCALE_3D * 1.6), XSCALE_3D, YSCALE_3D);

                g.setColor(getCreatureColor(env.shrineClassId));
                g.fillOval(tx - XSCALE_3D, ty - (int)(YSCALE_3D * 2.0), XSCALE_3D, YSCALE_3D);
                g.setColor(Color.BLACK);
                g.drawOval(tx - XSCALE_3D, ty - (int)(YSCALE_3D * 2.0), XSCALE_3D, YSCALE_3D);
            }
        }

        if (env.pheromone.length() > 0) {
            // Draw pheromone

            // Translate
            final int   tx = projectX(x, y) - XSCALE_3D + 6;
            final int   ty = projectY(x, y) - YSCALE_3D + 2;

            // Rotate 90 degrees
            final float a  = -3.1415927f / 2.0f;
            g.translate(tx, ty);
            g.rotate(a);
            g.setColor(PHEROMONE_SHADOW_COLOR);

            String s = env.pheromone.substring(0, 1);
            g.drawString(s, -1, 1);
            g.setColor(PHEROMONE_COLOR);
            g.drawString(s, 0, 0);
            g.rotate(-a);
            g.translate(-tx, -ty);
        }

        if (e instanceof StaticEntity) {
            im = ((StaticEntity)e).image;
        } else if (e instanceof Creature) {
            Creature c = (Creature)e;
            Proxy proxy = proxyTable.get(c);
            im = getImage(c, proxy.direction);

            if (proxy.isEnchanted) {

                final boolean isApple = (c.getClass() == Apple.class);

                // Draw enchantment
                final Color color = getCreatureColor(c.getClass());
                g.setColor(color);
                final int w = XSCALE_3D;
                // Don't draw very high bars on apples
                final int h = isApple ? YSCALE_3D * 10 : YSCALE_3D * 50;
                final int barX = projectX(x, y);
                final int barY = projectY(x, y) - XSCALE_3D;

                GradientPaint gradient = 
                    new GradientPaint(0, 0, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0), 
                                      w / 2, 0, new Color(color.getRed(), color.getGreen(), color.getBlue(), 200),
                                      true);
                g.setPaint(gradient);
                g.fillRect(barX - w, barY - h, w, h);
                g.setPaint(null);
            }
        }

        drawImage3D(g, x, y, im);

        if (env.inFog) {
            drawImage3D(g, x, y, StaticEntity.fog);
        }

    }
    
    /** 2D X coordinate of isometric projection of the lower-right corner of 
        3D location (x, y, 0) */
    private int projectX(int x, int y) {
        return (x + height - y + 1) * XSCALE_3D;
    }


    /** 2D Y coordinate of isometric projection of the lower-right corner
        of 3D location (x, y, 0) */
    private int projectY(int x, int y) {
        return (x + y + 2) * YSCALE_3D;
    }


    private void drawImage3D(Graphics2D g, int x, int y, Image im) {
        if (im != null) {
            int w  = im.getWidth(null);
            int h  = im.getHeight(null);
            int x0 = projectX(x, y) - w + (w - XSCALE_3D) / 2;
            int y0 = projectY(x, y) - h;
            g.drawImage(im, x0, y0, null);
        }
    }


    private void drawGrid3D(Graphics2D g) {
        g.setColor(Color.GRAY);

        int centerX = height * XSCALE_3D;

        int dx = width * XSCALE_3D;
        int dy = width * YSCALE_3D;
        for (int y = 0; y <= height; ++y) {
            int x0 = -y * XSCALE_3D + centerX;
            int y0 =  y * YSCALE_3D;
            g.drawLine(x0, y0, x0 + dx, y0 + dy);
        }

        dx = -height * XSCALE_3D;
        dy =  height * YSCALE_3D;
        for (int x = 0; x <= width; ++x) {
            int x0 =  x * XSCALE_3D + centerX;
            int y0 =  x * YSCALE_3D;
            g.drawLine(x0, y0, x0 + dx, y0 + dy);
        }
    }

    /** For 2D rendering */
    private static Font  font = new Font("Arial", Font.PLAIN, SCALE_2D - 6);

    /** For 3D rendering */
    private static Font  PHEROMONE_FONT_3D = new Font("Arial", Font.BOLD, SCALE_2D + 3);

    /** Separate from species because this is just for rendering */
    private Map<Integer, Color> creatureColor = new HashMap<Integer, Color>();

    /** Colors to be used for new creatures.*/
    private Stack<Color> colorStack = new Stack<Color>();
    
    /** Returns the transformation under which the 2D grid was last
        drawn. May be null. */
    private synchronized AffineTransform getLastTransform2D() {
        return lastXForm2D;
    }

    
    /** Returns the location under the position (x, y) if in 2D mode
        and in bounds, and null otherwise */
    public Point screenPointToLocation(int x, int y) {
        if (getView3D()) {
            // In 3D view; no click allowed
            return null;
        }

        final AffineTransform xform = getLastTransform2D();
        Point2D.Float point = new Point2D.Float(x, y);
        // System.out.println("\nClicked at " + point);
        try {
            xform.inverseTransform(point, point);
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            // Ignore
        }

        final float s = Simulator.SCALE_2D;

        // System.out.println("\nTransformed to " + point);
        
        x = (int)(point.x / s);
        y = (int)(point.y / s + 1.5f);

        // System.out.println("\nMapped to grid square (" + x + ", " + y + ")");

        if (inBounds(x, y)) {
            return new Point(x, y);
        } else {
            return null;
        }
    }

    
    private void paintIcon2D(Component c, Graphics _g, int tx, int ty) {
        Graphics2D g = (Graphics2D)_g;

        drawTitle(g, true);

        // Show coordinates along axes
        g.setFont(font);
        g.setColor(Color.GRAY);
        for (int y = 0; y < height; ++y) {
            g.drawString("" + y, -20, y * SCALE_2D + SCALE_2D - 2);
        }
        for (int x = 0; x < width; ++x) {
            g.drawString("" + x, x * SCALE_2D, -9);
        }

        // Background
        g.setColor(new Color(160, 160, 160));
        g.fillRect(0, 0, width * SCALE_2D, height * SCALE_2D);

        // Save the transformation for later picking by the inspector
        lastXForm2D = g.getTransform();

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                drawEntity2D(g, map[x][y], x, y);
            }
        }

        drawGrid2D(g);

        g.translate(-tx, -ty);
    }

    // 2D polygon
    private static int[] xpoints = {-SCALE_2D/2+1, -SCALE_2D/2+1, SCALE_2D/2-5, SCALE_2D/2-1,  SCALE_2D/2-5};
    private static int[] ypoints = {-SCALE_2D/2+1,  SCALE_2D/2-1, SCALE_2D/2-1,     0,        -SCALE_2D/2+1};

    private static final Color FOG_COLOR_2D    = new Color(255, 255, 255, 180);
    private static final Color MUD_COLOR_2D    = new Color(90, 55, 0, 200);

    private void drawEntity2D(Graphics2D g, Entity e, int x, int y) {
        final Environment env = environment[x][y];

        if (env.inMud) {
            g.setColor(MUD_COLOR_2D);
            g.fillRect(x * SCALE_2D + 1, y * SCALE_2D + 1, SCALE_2D, SCALE_2D);
        }

        if (env.shrineClassId != Creature.UNINITIALIZED_CLASS_ID) {
            g.setColor(getCreatureColor(env.shrineClassId));
            g.fillOval(x * SCALE_2D + 2, y * SCALE_2D + 2, SCALE_2D - 3, SCALE_2D - 3);
            g.setColor(Color.WHITE);
            g.drawOval(x * SCALE_2D + 2, y * SCALE_2D + 2, SCALE_2D - 3, SCALE_2D - 3);
        }

        if (e instanceof StaticEntity) {
            if (e.getType() == Type.WALL) {
                g.setColor(Color.BLACK);
                g.fillRect(x * SCALE_2D, y * SCALE_2D, SCALE_2D, SCALE_2D);
            } else {
                // Thorn
                g.setColor(Color.GREEN);
                g.fillRect(x * SCALE_2D + 3, y * SCALE_2D + 3, SCALE_2D - 6, SCALE_2D - 6);
            }
        } else if (e instanceof Treasure) {
            // Treasure
            g.setColor(Color.YELLOW);
            g.fillArc(x * SCALE_2D, y * SCALE_2D, SCALE_2D - 1, SCALE_2D - 1, 0, 360);
            g.setColor(Color.BLACK);
            g.drawArc(x * SCALE_2D, y * SCALE_2D, SCALE_2D - 1, SCALE_2D - 1, 0, 360);
            
        } else if (e instanceof Creature) {
            drawCreature2D(g, e, x, y);
        }

        if (env.pheromone.length() > 0) {
            g.setColor(PHEROMONE_COLOR);
            g.fillRect(x * SCALE_2D + 1, y * SCALE_2D + 1 + SCALE_2D / 2, SCALE_2D, SCALE_2D/2);
        }

        if (env.inFog) {
            g.setColor(FOG_COLOR_2D);
            g.fillRect(x * SCALE_2D + 1, y * SCALE_2D + 1, SCALE_2D, SCALE_2D);
        }
    }


    private void drawCreature2D(Graphics2D g, Entity e, int x, int y) {
        char label = e.getLabel();

        Proxy info = proxyTable.get(e);
        Direction d = info.direction;
        g.setColor(getCreatureColor(e.getClass()));

        if (info.isEnchanted) {
            // Fill the whole square with the creature's color
            g.fillRect(x * SCALE_2D, y * SCALE_2D, SCALE_2D, SCALE_2D);
        }
            
        int tx = x * SCALE_2D + SCALE_2D / 2;
        int ty = y * SCALE_2D + SCALE_2D / 2;
        AffineTransform old = g.getTransform();
        g.translate(tx, ty);
        g.rotate(Math.toRadians(270 - 90 * d.toInt()));
        g.fillPolygon(xpoints, ypoints, 5);

        if (e == selectedCreature) {
            // Highlight
            Stroke s = g.getStroke();
            g.setStroke(new BasicStroke(3));
            g.setColor(Color.WHITE);
            g.drawPolygon(xpoints, ypoints, 5);
            g.setStroke(s);
        }

        g.setColor(Color.BLACK);
        g.drawPolygon(xpoints, ypoints, 5);

        g.setTransform(old);

        FontMetrics m = g.getFontMetrics();

        // Center the label
        int fx = x * SCALE_2D + (SCALE_2D - m.charWidth(label)) / 2 + 1;
        int fy = y * SCALE_2D + (SCALE_2D + m.getAscent()) / 2 - 1;
        if (e == selectedCreature) {
            g.setColor(Color.WHITE);
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    g.drawString("" + label, fx + dx, fy +dy);
                }
            }
            g.setColor(Color.BLACK);
        }
        g.drawString("" + label, fx, fy);
    }


    /** Draws gridlines of the map.  Called from paint. */
    private void drawGrid2D(Graphics2D g) {
        //grid2DStart = new Point(x * SCALE_2D, y * SCALE_2D);  TODO
        
        g.setColor(new Color(0.7f, 0.7f, 0.7f));

        for (int x = 0; x <= width; ++x) {
            g.drawLine((int)(x * SCALE_2D), 0, (int)(x * SCALE_2D), (int)(height * SCALE_2D));
        }

        for (int y = 0; y <= height; ++y) {
            g.drawLine(0, (int)(y * SCALE_2D), (int)(width * SCALE_2D), (int)(y * SCALE_2D));
        }
    }


    public static Image getImage(Creature c, Direction d) {
        return getImage(c.getClass(), d);
    }


    /** Returns the image for this Creature subclass. */
    public static Image getImage(Class cs, Direction d) {
        Image[] array = imageCache.get(cs);
        if (array == null) {
            array = loadImages(cs.getName());
            imageCache.put(cs, array);
        }
        
        return array[d.toInt()];        
    }


    /** Returns the appropriate sound for this creature. */
    public static Sound getSound(Creature c, Condition condition) {
        return getSound(c.getClass(), condition);
    }


    public static Sound getSound(Class cs, Condition condition) {
        Sound[] array = soundCache.get(cs);
        if (array == null) {
            array = loadSounds(cs.getName());
            soundCache.put(cs, array);
        }
        return array[condition.toInt()];
    }


    /** Returns true if this resource exists either on disk or in a
        jar file or directory in the classpath. */
    private static boolean resourceExists(String filename) {
        return java.lang.ClassLoader.getSystemResourceAsStream(filename) != null;
    }


    /** Loads all sounds for the creature whose name is base */
    private static Sound[] loadSounds(String base) {
        Sound[] array = new Sound[1];

        String filename = base + "-Win.wav";        
        if (! resourceExists(filename)) {
            // Use the default sound
            filename = "Win.wav";
        }

        array[0] = new Sound(filename, 6.5);
        
        return array;
    }

    /** Loads all images for the creature whose name is base */
    private static Image[] loadImages(String base) {
        if (resourceExists(base + ".png")) {
            // Single image
            Image i = loadImage(base + ".png");
            Image[] array = {i, i, i, i};
            return array;
        } else {
            // Series of images, facing each direction
            String[] ext = {"N", "W", "S", "E"};
            Image[] array = new Image[4];

            for (int e = 0; e < 4; ++e) {
                String filename = base + "-" + ext[e] + ".png";
                array[e] = loadImage(filename);
            }

            return array;
        }         
    }

    /** Load filename if it exists, otherwise load fallback */
    private static Image loadImage(String filename, String fallback) {
        if (resourceExists(filename)) {
            return loadImage(filename);
        } else {
            return loadImage(fallback);
        }
    }

    private static BufferedImage loadImage(String filename) {
        
        // Try to load either from the current directory or the .jar file
        final InputStream input = java.lang.ClassLoader.getSystemResourceAsStream(filename);


        if (input == null) {
            System.err.println("Warning: Missing image: " + filename);
            return bogusImage(filename);
        }

        try {
            // Faster on some old macs:
            // return Toolkit.getDefaultToolkit().getImage(file.getPath());
            // Wait for image to load
            //while (im.getWidth(null) == 0);
            
            BufferedImage im = javax.imageio.ImageIO.read(input);

            if (im == null) {
                throw new java.io.IOException("corrupt image file");
            }

            // Ensure that the image is not too big
            if (im.getWidth() > XSCALE_3D * 2) {
                // System.out.println("Warning: Rescaled " + filename + " because it was too big.");
                return im;
                /*
                im.getScaledInstance(XSCALE_3D * 2, XSCALE_3D * 2 * im.getHeight() / im.getWidth(), 
                                     Image.SCALE_AREA_AVERAGING);
                */
            }
            
            return im;
        } catch (java.io.IOException e) {
            System.err.println("Warning: While loading " + filename + " encountered " + e);
            return bogusImage(filename);
        }
    }


    /** Creates a black box as a default image for files that don't load */
    static private BufferedImage bogusImage(String filename) {
        BufferedImage im = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        return im;
    }


    private void prepareClassIds() {
        speciesTable = new HashMap<Class, Species>();
        speciesTable.put(Apple.class, new Species(Apple.class, Creature.APPLE_CLASS_ID));
        speciesTable.put(Flytrap.class, new Species(Flytrap.class, Creature.FLYTRAP_CLASS_ID));
        speciesTable.put(Treasure.class, new Species(Treasure.class, Creature.TREASURE_CLASS_ID));
    }

    private int getClassId(Class creatureClass) {
        return speciesTable.get(creatureClass).classId;
    }


    /** Generates a fresh class id for a creature class. Not
        particularly efficient, but only called once or twice at the
        loading of a map.*/
    private int newCreatureClassId() {
        int id;
        boolean unique = false;

        do {
            // Create a new id that is larger than the unknown creature ID
            id = (int)Math.round(Math.random() * 10000) + 10 + Creature.UNKNOWN_CREATURE_CLASS_ID;

            unique = true;
            for (Map.Entry<Class, Species> entry : speciesTable.entrySet()) {
                if (entry.getValue().classId == id) {
                    unique = false;
                    break;
                }
            }
        } while (! unique);

        return id;
    }


    private void prepareColors() {
        creatureColor.put(Creature.APPLE_CLASS_ID, Color.RED);
        creatureColor.put(Creature.FLYTRAP_CLASS_ID, Color.GREEN);
        creatureColor.put(Creature.TREASURE_CLASS_ID, Color.YELLOW);

        colorStack.push(Color.YELLOW);
        colorStack.push(new Color(0.5f, 0.5f, 0.5f));
        colorStack.push(new Color(0.5f, 0, 0));
        colorStack.push(Color.PINK);
        colorStack.push(Color.BLUE);
        colorStack.push(new Color(0.3f, 0.3f, 0.0f));
        colorStack.push(Color.MAGENTA);
        colorStack.push(Color.WHITE);
        colorStack.push(Color.ORANGE);
        colorStack.push(new Color(0, 0.7f, 0.7f));
    }


    /** Strips the capital letters off the right of a creature name.*/
    public static String shortName(String s) {
        int i = s.length() - 1;
        while ((i > 0) && Character.isUpperCase(s.charAt(i))) {
            --i;
        }
        return s.substring(0, i + 1);
    }


    //////////////////////////////////////////////////////

    // From http://stackoverflow.com/questions/4427200/getting-the-most-common-color-of-a-image
    private static Color getColor(BufferedImage image) {

        int height = image.getHeight();
        int width = image.getWidth();

        Map<Integer, Integer> m = new HashMap<Integer, Integer>();
        for(int i=0; i < width ; i++) {
            for(int j=0; j < height ; j++) {
                int rgb = image.getRGB(i, j);
                int[] rgbArr = getRGBArr(rgb);                

                // Filter out grays....                
                if (! isGray(rgbArr)) {                
                    Integer counter = (Integer) m.get(rgb);   
                    if (counter == null) {
                        counter = 0;
                    }
                    ++counter;                                
                    m.put(rgb, counter);
                }
            }
        }        
        
        return getMostCommonColor(m);
    }


    public static Color getMostCommonColor(Map<Integer, Integer> map) {
        java.util.List<Map.Entry<Integer, Integer>> list = new ArrayList<Map.Entry<Integer, Integer>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
                public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                    return (o1).getValue().compareTo(o2.getValue());
                }
            });

        if (list.size() == 0) {
            return Color.BLUE;
        }
        Map.Entry<Integer, Integer> me = list.get(list.size() - 1);
        int[] rgb = getRGBArr(me.getKey());
        return new Color(rgb[0], rgb[1], rgb[2]);
    }    
    
    private static int[] getRGBArr(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;
        return new int[]{red,green,blue};
        
    }

    private static boolean isGray(int[] rgbArr) {
        int rgDiff = rgbArr[0] - rgbArr[1];
        int rbDiff = rgbArr[0] - rgbArr[2];
        // Filter out black, white and grays...... (tolerance within 10 pixels)
        int tolerance = 10;
        if (rgDiff > tolerance || rgDiff < -tolerance) 
            if (rbDiff > tolerance || rbDiff < -tolerance) { 
                return false;
            }                 
        return true;
    }
}
